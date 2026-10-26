"""Tests for edge endpoint resolution and correction in assemble_corpus."""

import os
import sys
import tempfile

import pytest
import yaml

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'scripts'))
from assemble_corpus import resolve_edge_endpoints, correct_edge_errors


def _nodes(*names, **kwargs):
    subgraph_type = kwargs.get('subgraph_type', 'person')
    return [{'name': n, 'subgraph_type': subgraph_type} for n in names]


def _edges(*pairs):
    return [{'source': s, 'target': t, 'type': 'related-to'} for s, t in pairs]


class TestExactMatch:
    def test_exact_match_unchanged(self):
        nodes = _nodes('Frida Kahlo', 'Diego Rivera')
        edges = _edges(('Frida Kahlo', 'Diego Rivera'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Frida Kahlo'
        assert edges[0]['target'] == 'Diego Rivera'
        assert report['resolved'] == 0

    def test_no_false_resolution(self):
        nodes = _nodes('Frida Kahlo')
        edges = _edges(('Unknown Person', 'Frida Kahlo'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Unknown Person'
        assert report['unresolved_unique'] == 1


class TestDedupAliases:
    def test_merged_from_resolved(self):
        nodes = [{'name': 'Frida Kahlo', 'subgraph_type': 'person',
                  'merged_from': ['Fridita', 'FK']}]
        edges = _edges(('Fridita', 'Frida Kahlo'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Frida Kahlo'
        assert report['resolution_methods'].get('dedup-alias', 0) >= 1


class TestParenStrip:
    def test_strips_parenthetical_metadata(self):
        nodes = _nodes('Manage chronic pain', subgraph_type='goal')
        edges = _edges(('Manage chronic pain (goal, tactical, health)', 'Manage chronic pain'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Manage chronic pain'
        assert 'paren-strip' in report['resolution_methods']

    def test_paren_strip_with_alias(self):
        nodes = [{'name': 'Manage chronic pain', 'subgraph_type': 'goal',
                  'merged_from': ['Pain management']}]
        edges = _edges(('Pain management (goal, tactical)', 'Manage chronic pain'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Manage chronic pain'


class TestCaseInsensitive:
    def test_case_mismatch_resolved(self):
        nodes = _nodes('Surrealist movement', subgraph_type='concept')
        edges = _edges(('surrealist movement', 'Surrealist movement'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Surrealist movement'
        assert report['resolution_methods'].get('case-insensitive', 0) >= 1


class TestFirstName:
    def test_single_word_resolves_to_full_name(self):
        nodes = _nodes('Frida Kahlo', 'Diego Rivera')
        edges = _edges(('Frida', 'Diego'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Frida Kahlo'
        assert edges[0]['target'] == 'Diego Rivera'
        assert report['resolution_methods'].get('first-name', 0) == 2

    def test_ambiguous_first_name_not_resolved(self):
        nodes = _nodes('Jean Charlot', 'Jean Dupont')
        edges = _edges(('Jean', 'Jean Charlot'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Jean'
        assert report['unresolved_unique'] == 1

    def test_possessive_excluded(self):
        nodes = _nodes("Frida's father", 'Frida Kahlo')
        edges = _edges(('Frida', 'Frida Kahlo'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Frida Kahlo'

    def test_compound_name_excluded(self):
        nodes = _nodes('Frida and Diego', 'Frida Kahlo')
        edges = _edges(('Frida', 'Frida Kahlo'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Frida Kahlo'

    def test_goals_deprioritised(self):
        nodes = [
            {'name': 'Mexico City', 'subgraph_type': 'place'},
            {'name': 'Mexico freedom movement', 'subgraph_type': 'goal'},
        ]
        edges = _edges(('Mexico', 'Mexico City'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Mexico City'


class TestAliasFile:
    def test_explicit_alias_file(self):
        nodes = _nodes("Frida's father", 'Frida Kahlo')
        edges = _edges(('Guillermo Kahlo', 'Frida Kahlo'))
        with tempfile.NamedTemporaryFile(mode='w', suffix='.yaml', delete=False) as f:
            yaml.dump({'Guillermo Kahlo': "Frida's father"}, f)
            f.flush()
            report = resolve_edge_endpoints(nodes, edges, alias_file=f.name)
        os.unlink(f.name)
        assert edges[0]['source'] == "Frida's father"
        assert report['resolution_methods'].get('explicit-alias', 0) >= 1

    def test_alias_to_nonexistent_node_ignored(self):
        nodes = _nodes('Frida Kahlo')
        edges = _edges(('Ghost', 'Frida Kahlo'))
        with tempfile.NamedTemporaryFile(mode='w', suffix='.yaml', delete=False) as f:
            yaml.dump({'Ghost': 'Nonexistent Node'}, f)
            f.flush()
            report = resolve_edge_endpoints(nodes, edges, alias_file=f.name)
        os.unlink(f.name)
        assert edges[0]['source'] == 'Ghost'
        assert report['unresolved_unique'] == 1

    def test_no_alias_file_ok(self):
        nodes = _nodes('Frida Kahlo')
        edges = _edges(('Frida', 'Frida Kahlo'))
        report = resolve_edge_endpoints(nodes, edges)
        assert edges[0]['source'] == 'Frida Kahlo'


class TestReport:
    def test_report_structure(self):
        nodes = _nodes('Frida Kahlo')
        edges = _edges(('Frida', 'Unknown'), ('Frida', 'Unknown'))
        report = resolve_edge_endpoints(nodes, edges)
        assert 'resolved' in report
        assert 'unresolved_unique' in report
        assert 'unresolved_total' in report
        assert 'resolution_methods' in report
        assert 'unresolved_names' in report

    def test_unresolved_names_sorted_by_count(self):
        nodes = _nodes('Frida Kahlo')
        edges = _edges(('A', 'Frida Kahlo'), ('B', 'Frida Kahlo'),
                       ('B', 'Frida Kahlo'), ('C', 'Frida Kahlo'))
        report = resolve_edge_endpoints(nodes, edges)
        names = [u['name'] for u in report['unresolved_names']]
        counts = [u['count'] for u in report['unresolved_names']]
        assert counts == sorted(counts, reverse=True)


class TestEdgeCorrections:
    def test_inverted_parent_of(self):
        nodes = _nodes('Frida Kahlo', "Frida's mother")
        edges = [{'source': 'Frida Kahlo', 'target': "Frida's mother",
                  'type': 'parent-of'}]
        result, report = correct_edge_errors(edges, nodes)
        assert result[0]['source'] == "Frida's mother"
        assert result[0]['target'] == 'Frida Kahlo'
        assert report['corrections'] == 1

    def test_duplicate_married_to_removed(self):
        nodes = _nodes('A', 'B')
        edges = [{'source': 'A', 'target': 'B', 'type': 'married-to'},
                 {'source': 'B', 'target': 'A', 'type': 'married-to'},
                 {'source': 'A', 'target': 'B', 'type': 'married-to'}]
        result, report = correct_edge_errors(edges, nodes)
        assert len(result) == 1
        assert report['removals'] == 2

    def test_unvalidated_type_kept(self):
        nodes = _nodes('A', 'B')
        edges = [{'source': 'A', 'target': 'B', 'type': 'UNVALIDATED',
                  'validation_tier': 'UNVALIDATED'}]
        result, report = correct_edge_errors(edges, nodes)
        assert result[0]['type'] == 'UNVALIDATED'
        assert result[0]['validation_tier'] == 'UNVALIDATED'

    def test_factual_retype_from_file(self):
        nodes = _nodes('Frida Kahlo', 'Alejandro')
        edges = [{'source': 'Frida Kahlo', 'target': 'Alejandro', 'type': 'married-to'}]
        with tempfile.NamedTemporaryFile(mode='w', suffix='.yaml', delete=False) as f:
            yaml.dump([{
                'match': {'type': 'married-to', 'source': 'Frida Kahlo', 'target': 'Alejandro'},
                'action': 'retype', 'new_type': 'romantic-partner',
                'reason': 'Never married'
            }], f)
            f.flush()
            result, report = correct_edge_errors(edges, nodes, corrections_file=f.name)
        os.unlink(f.name)
        assert result[0]['type'] == 'romantic-partner'

    def test_factual_remove_from_file(self):
        nodes = _nodes('A', 'B')
        edges = [{'source': 'A', 'target': 'B', 'type': 'collaborated-with'},
                 {'source': 'A', 'target': 'C', 'type': 'inspired-by'}]
        with tempfile.NamedTemporaryFile(mode='w', suffix='.yaml', delete=False) as f:
            yaml.dump([{
                'match': {'type': 'collaborated-with', 'source': 'A', 'target': 'B'},
                'action': 'remove', 'reason': 'Dubious'
            }], f)
            f.flush()
            result, report = correct_edge_errors(edges, nodes, corrections_file=f.name)
        os.unlink(f.name)
        assert len(result) == 1
        assert result[0]['type'] == 'inspired-by'

    def test_no_corrections_file_ok(self):
        nodes = _nodes('A', 'B')
        edges = [{'source': 'A', 'target': 'B', 'type': 'related-to'}]
        result, report = correct_edge_errors(edges, nodes)
        assert len(result) == 1
        assert report['corrections'] == 0
