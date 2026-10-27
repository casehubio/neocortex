"""Tests for orphan closure step — detection, context building, validation, parsing."""

import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'scripts'))
from orphan_closure import (find_orphans, find_passage_gaps,
                            build_passage_context, build_orphan_context,
                            validate_closure_edges, parse_closure_response,
                            run_closure)


def _node(name, subgraph_type='person', source_passages=None):
    return {
        'name': name,
        'subgraph_type': subgraph_type,
        'source_passages': source_passages or [],
    }


def _edge(source, target, edge_type='related-to'):
    return {'source': source, 'target': target, 'type': edge_type}


class TestFindOrphans:
    def test_no_orphans(self):
        nodes = [_node('A'), _node('B')]
        edges = [_edge('A', 'B')]
        assert find_orphans(nodes, edges) == []

    def test_identifies_orphan(self):
        nodes = [_node('A'), _node('B'), _node('C')]
        edges = [_edge('A', 'B')]
        orphans = find_orphans(nodes, edges)
        assert len(orphans) == 1
        assert orphans[0]['name'] == 'C'

    def test_all_orphans(self):
        nodes = [_node('A'), _node('B')]
        edges = []
        orphans = find_orphans(nodes, edges)
        assert len(orphans) == 2

    def test_source_as_edge_endpoint(self):
        nodes = [_node('A'), _node('B')]
        edges = [_edge('A', 'B')]
        assert find_orphans(nodes, edges) == []

    def test_target_as_edge_endpoint(self):
        nodes = [_node('A'), _node('B')]
        edges = [_edge('A', 'B')]
        assert find_orphans(nodes, edges) == []


class TestFindPassageGaps:
    def test_no_gaps_when_all_connected(self):
        nodes = [_node('A', source_passages=['p-001']),
                 _node('B', source_passages=['p-001'])]
        edges = [_edge('A', 'B')]
        gaps = find_passage_gaps(nodes, edges)
        assert len(gaps) == 0

    def test_finds_gap_entity(self):
        nodes = [_node('A', source_passages=['p-001']),
                 _node('B', source_passages=['p-001']),
                 _node('C', source_passages=['p-001'])]
        edges = [_edge('A', 'B')]
        gaps = find_passage_gaps(nodes, edges)
        assert len(gaps) == 1
        assert gaps[0]['passage_id'] == 'p-001'
        gap_names = {g['name'] for g in gaps[0]['gap_entities']}
        assert 'C' in gap_names

    def test_ignores_cross_passage_connections(self):
        """A node connected in passage 2 but not in passage 1 is a gap in passage 1."""
        nodes = [_node('A', source_passages=['p-001']),
                 _node('B', source_passages=['p-001']),
                 _node('C', source_passages=['p-001', 'p-002']),
                 _node('D', source_passages=['p-002'])]
        edges = [_edge('A', 'B'), _edge('C', 'D')]
        gaps = find_passage_gaps(nodes, edges)
        assert len(gaps) == 1
        assert gaps[0]['passage_id'] == 'p-001'
        gap_names = {g['name'] for g in gaps[0]['gap_entities']}
        assert 'C' in gap_names

    def test_multiple_gap_entities_in_passage(self):
        nodes = [_node('A', source_passages=['p-001']),
                 _node('B', source_passages=['p-001']),
                 _node('C', source_passages=['p-001']),
                 _node('D', source_passages=['p-001'])]
        edges = [_edge('A', 'B')]
        gaps = find_passage_gaps(nodes, edges)
        assert len(gaps) == 1
        gap_names = {g['name'] for g in gaps[0]['gap_entities']}
        assert 'C' in gap_names
        assert 'D' in gap_names

    def test_single_node_passage_no_gap(self):
        nodes = [_node('A', source_passages=['p-001'])]
        edges = []
        gaps = find_passage_gaps(nodes, edges)
        assert len(gaps) == 0

    def test_global_orphan_detected_as_gap(self):
        """A zero-edge orphan appears as a gap entity in its passage."""
        nodes = [_node('A', source_passages=['p-001']),
                 _node('B', source_passages=['p-001']),
                 _node('C', source_passages=['p-001'])]
        edges = [_edge('A', 'B')]
        gaps = find_passage_gaps(nodes, edges)
        gap_names = {g['name'] for g in gaps[0]['gap_entities']}
        assert 'C' in gap_names


class TestBuildPassageContext:
    def test_gathers_passage_text(self):
        nodes = [_node('A', source_passages=['p-001']),
                 _node('B', source_passages=['p-001']),
                 _node('C', source_passages=['p-001'])]
        edges = [_edge('A', 'B')]
        passages = [{'id': 'p-001', 'text': 'The passage text.'}]
        gap_entities = [{'name': 'C', 'subgraph_type': 'person'}]
        ctx = build_passage_context('p-001', gap_entities, passages, nodes, edges)
        assert ctx['passage_text'] == 'The passage text.'
        assert ctx['passage_id'] == 'p-001'

    def test_lists_all_entities_in_passage(self):
        nodes = [_node('A', source_passages=['p-001']),
                 _node('B', source_passages=['p-001']),
                 _node('C', source_passages=['p-001'])]
        edges = [_edge('A', 'B')]
        passages = [{'id': 'p-001', 'text': 'Text'}]
        gap_entities = [{'name': 'C', 'subgraph_type': 'person'}]
        ctx = build_passage_context('p-001', gap_entities, passages, nodes, edges)
        assert set(ctx['all_entities']) == {'A', 'B', 'C'}

    def test_lists_existing_edges(self):
        nodes = [_node('A', source_passages=['p-001']),
                 _node('B', source_passages=['p-001']),
                 _node('C', source_passages=['p-001'])]
        edges = [_edge('A', 'B', 'married-to')]
        passages = [{'id': 'p-001', 'text': 'Text'}]
        gap_entities = [{'name': 'C', 'subgraph_type': 'person'}]
        ctx = build_passage_context('p-001', gap_entities, passages, nodes, edges)
        assert len(ctx['existing_edges']) == 1
        assert ctx['existing_edges'][0]['type'] == 'married-to'

    def test_lists_gap_entity_names(self):
        nodes = [_node('A', source_passages=['p-001']),
                 _node('C', source_passages=['p-001']),
                 _node('D', source_passages=['p-001'])]
        edges = []
        passages = [{'id': 'p-001', 'text': 'Text'}]
        gap_entities = [{'name': 'C', 'subgraph_type': 'person'},
                        {'name': 'D', 'subgraph_type': 'place'}]
        ctx = build_passage_context('p-001', gap_entities, passages, nodes, edges)
        assert set(ctx['gap_entity_names']) == {'C', 'D'}

    def test_missing_passage_handled(self):
        ctx = build_passage_context('p-999', [], [{'id': 'p-001', 'text': 'T'}], [], [])
        assert ctx['passage_text'] == ''


class TestBuildOrphanContext:
    def test_gathers_passage_text(self):
        orphan = _node('Place X', 'place', source_passages=['p-001'])
        passages = [{'id': 'p-001', 'text': 'Some text about Place X.'}]
        nodes = [_node('A'), orphan]
        edges = [_edge('A', 'B')]
        ctx = build_orphan_context(orphan, passages, nodes, edges)
        assert 'Some text about Place X.' in ctx['passage_texts'][0]
        assert ctx['orphan_name'] == 'Place X'
        assert ctx['orphan_type'] == 'place'

    def test_gathers_colocated_entities(self):
        orphan = _node('Place X', 'place', source_passages=['p-001'])
        colocated = _node('Person A', 'person', source_passages=['p-001'])
        other = _node('Person B', 'person', source_passages=['p-002'])
        passages = [{'id': 'p-001', 'text': 'Text'}, {'id': 'p-002', 'text': 'Other'}]
        ctx = build_orphan_context(orphan, passages,
                                   [orphan, colocated, other], [])
        assert 'Person A' in ctx['colocated_entities']
        assert 'Person B' not in ctx['colocated_entities']

    def test_gathers_colocated_edges(self):
        orphan = _node('Place X', 'place', source_passages=['p-001'])
        colocated = _node('A', source_passages=['p-001'])
        other_node = _node('B', source_passages=['p-001'])
        edge = _edge('A', 'B', 'married-to')
        passages = [{'id': 'p-001', 'text': 'Text'}]
        ctx = build_orphan_context(orphan, passages,
                                   [orphan, colocated, other_node], [edge])
        assert len(ctx['colocated_edges']) == 1
        assert ctx['colocated_edges'][0]['type'] == 'married-to'

    def test_missing_passage_handled(self):
        orphan = _node('X', source_passages=['p-999'])
        passages = [{'id': 'p-001', 'text': 'Text'}]
        ctx = build_orphan_context(orphan, passages, [orphan], [])
        assert ctx['passage_texts'] == []


class TestValidateClosureEdges:
    def test_valid_edge_accepted(self):
        edges = [{'source': 'A', 'target': 'B', 'type': 'at'}]
        valid_types = {'at', 'lives-at', 'married-to'}
        node_names = {'A', 'B'}
        result = validate_closure_edges(edges, valid_types, node_names)
        assert len(result) == 1

    def test_invalid_type_rejected(self):
        edges = [{'source': 'A', 'target': 'B', 'type': 'bogus-type'}]
        valid_types = {'at', 'lives-at'}
        node_names = {'A', 'B'}
        result = validate_closure_edges(edges, valid_types, node_names)
        assert len(result) == 0

    def test_unknown_endpoint_rejected(self):
        edges = [{'source': 'A', 'target': 'UNKNOWN', 'type': 'at'}]
        valid_types = {'at'}
        node_names = {'A', 'B'}
        result = validate_closure_edges(edges, valid_types, node_names)
        assert len(result) == 0

    def test_mixed_valid_invalid(self):
        edges = [
            {'source': 'A', 'target': 'B', 'type': 'at'},
            {'source': 'A', 'target': 'C', 'type': 'bogus'},
        ]
        valid_types = {'at'}
        node_names = {'A', 'B', 'C'}
        result = validate_closure_edges(edges, valid_types, node_names)
        assert len(result) == 1
        assert result[0]['type'] == 'at'


class TestParseClosureResponse:
    def test_parses_edges(self):
        text = '{"edges": [{"source": "A", "target": "B", "type": "at"}], "standalone_reason": null}'
        edges, reason = parse_closure_response(text)
        assert len(edges) == 1
        assert edges[0]['type'] == 'at'
        assert reason is None

    def test_parses_standalone(self):
        text = '{"edges": [], "standalone_reason": "Generic concept with no specific relationship"}'
        edges, reason = parse_closure_response(text)
        assert len(edges) == 0
        assert 'Generic concept' in reason

    def test_handles_json_in_markdown(self):
        text = 'Here is my answer:\n```json\n{"edges": [{"source": "A", "target": "B", "type": "at"}], "standalone_reason": null}\n```'
        edges, reason = parse_closure_response(text)
        assert len(edges) == 1

    def test_handles_malformed_json(self):
        text = 'This is not valid JSON at all.'
        edges, reason = parse_closure_response(text)
        assert edges == []
        assert reason is None


class TestRunClosure:
    def test_no_gaps_returns_empty_report(self):
        corpus = {
            'nodes': [_node('A', source_passages=['p-001']),
                      _node('B', source_passages=['p-001'])],
            'edges': [_edge('A', 'B')],
            'stats': {},
        }
        passages = [{'id': 'p-001', 'text': 'Text'}]
        valid_types = {'at', 'lives-at'}
        report = run_closure(corpus, passages, valid_types, client=None)
        assert report['passages_checked'] == 0
        assert report['edges_added'] == 0

    def test_gap_without_client_stays_gap(self):
        corpus = {
            'nodes': [_node('A', source_passages=['p-001']),
                      _node('B', source_passages=['p-001']),
                      _node('C', source_passages=['p-001'])],
            'edges': [_edge('A', 'B')],
            'stats': {},
        }
        passages = [{'id': 'p-001', 'text': 'Text about C'}]
        valid_types = {'at'}
        report = run_closure(corpus, passages, valid_types, client=None)
        assert report['passages_checked'] == 1
        assert report['edges_added'] == 0
        assert report['gap_entities_found'] == 1

    def test_global_orphan_detected(self):
        """Zero-edge orphan appears in gap detection."""
        corpus = {
            'nodes': [_node('A', source_passages=['p-001']),
                      _node('B', source_passages=['p-001']),
                      _node('C', source_passages=['p-001'])],
            'edges': [_edge('A', 'B')],
            'stats': {},
        }
        passages = [{'id': 'p-001', 'text': 'Text'}]
        valid_types = {'at'}
        report = run_closure(corpus, passages, valid_types, client=None)
        assert report['gap_entities_found'] >= 1
        gap_names = set()
        for d in report['details']:
            for g in d.get('gap_entities', []):
                gap_names.add(g)
        assert 'C' in gap_names

    def test_backward_compat_report_fields(self):
        """Report includes total_orphans for backward compatibility."""
        corpus = {
            'nodes': [_node('A', source_passages=['p-001']),
                      _node('B', source_passages=['p-001'])],
            'edges': [_edge('A', 'B')],
            'stats': {},
        }
        passages = [{'id': 'p-001', 'text': 'Text'}]
        report = run_closure(corpus, passages, {'at'}, client=None)
        assert 'total_orphans' in report
