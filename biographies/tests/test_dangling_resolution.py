"""Tests for dangling edge resolution — name matching, entity back-fill, edge dropping."""

import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'scripts'))
from dangling_resolution import (find_dangling_endpoints, build_dangling_context,
                                  parse_resolution_response, apply_resolutions,
                                  run_dangling_resolution)


def _node(name, subgraph_type='person', source_passages=None):
    return {
        'name': name,
        'subgraph_type': subgraph_type,
        'source_passages': source_passages or [],
    }


def _edge(source, target, edge_type='related-to', source_passage=None):
    e = {'source': source, 'target': target, 'type': edge_type}
    if source_passage:
        e['source_passage'] = source_passage
    return e


class TestFindDanglingEndpoints:
    def test_no_dangles(self):
        nodes = [_node('A'), _node('B')]
        edges = [_edge('A', 'B')]
        result = find_dangling_endpoints(nodes, edges)
        assert len(result) == 0

    def test_finds_dangling_source(self):
        nodes = [_node('A')]
        edges = [_edge('UNKNOWN', 'A', source_passage='p-001')]
        result = find_dangling_endpoints(nodes, edges)
        assert len(result) == 1
        assert result[0]['name'] == 'UNKNOWN'

    def test_finds_dangling_target(self):
        nodes = [_node('A')]
        edges = [_edge('A', 'UNKNOWN', source_passage='p-001')]
        result = find_dangling_endpoints(nodes, edges)
        assert len(result) == 1
        assert result[0]['name'] == 'UNKNOWN'

    def test_groups_by_passage(self):
        nodes = [_node('A')]
        edges = [_edge('X', 'A', source_passage='p-001'),
                 _edge('Y', 'A', source_passage='p-002')]
        result = find_dangling_endpoints(nodes, edges)
        passages = {d['name']: d['passages'] for d in result}
        assert 'p-001' in passages['X']
        assert 'p-002' in passages['Y']

    def test_counts_references(self):
        nodes = [_node('A')]
        edges = [_edge('X', 'A', source_passage='p-001'),
                 _edge('A', 'X', source_passage='p-001')]
        result = find_dangling_endpoints(nodes, edges)
        assert result[0]['count'] == 2

    def test_deduplicates_names(self):
        nodes = [_node('A')]
        edges = [_edge('X', 'A', source_passage='p-001'),
                 _edge('A', 'X', source_passage='p-002')]
        result = find_dangling_endpoints(nodes, edges)
        assert len(result) == 1
        assert result[0]['name'] == 'X'


class TestBuildDanglingContext:
    def test_gathers_passage_text(self):
        dangles = [{'name': 'X', 'passages': {'p-001'}, 'count': 1}]
        passages = [{'id': 'p-001', 'text': 'Text about X and A.'}]
        nodes = [_node('A', source_passages=['p-001'])]
        ctx = build_dangling_context('p-001', ['X'], passages, nodes)
        assert ctx['passage_text'] == 'Text about X and A.'
        assert 'X' in ctx['dangling_names']

    def test_lists_existing_nodes(self):
        passages = [{'id': 'p-001', 'text': 'T'}]
        nodes = [_node('A', source_passages=['p-001']),
                 _node('B', source_passages=['p-001']),
                 _node('C', source_passages=['p-002'])]
        ctx = build_dangling_context('p-001', ['X'], passages, nodes)
        assert 'A' in ctx['passage_nodes']
        assert 'B' in ctx['passage_nodes']

    def test_includes_all_node_names(self):
        passages = [{'id': 'p-001', 'text': 'T'}]
        nodes = [_node('A'), _node('B'), _node('C')]
        ctx = build_dangling_context('p-001', ['X'], passages, nodes)
        assert set(ctx['all_node_names']) == {'A', 'B', 'C'}

    def test_missing_passage_handled(self):
        ctx = build_dangling_context('p-999', ['X'], [{'id': 'p-001', 'text': 'T'}], [])
        assert ctx['passage_text'] == ''


class TestParseResolutionResponse:
    def test_parses_match(self):
        text = '{"resolutions": [{"name": "Cristina", "action": "match", "match_to": "Cristina Kahlo"}]}'
        result = parse_resolution_response(text)
        assert len(result) == 1
        assert result[0]['action'] == 'match'
        assert result[0]['match_to'] == 'Cristina Kahlo'

    def test_parses_create(self):
        text = '{"resolutions": [{"name": "Detroit", "action": "create", "entity": {"name": "Detroit", "type": "place"}}]}'
        result = parse_resolution_response(text)
        assert result[0]['action'] == 'create'
        assert result[0]['entity']['type'] == 'place'

    def test_parses_drop(self):
        text = '{"resolutions": [{"name": "kidney infection", "action": "drop", "reason": "Medical condition, not a graph entity"}]}'
        result = parse_resolution_response(text)
        assert result[0]['action'] == 'drop'
        assert 'Medical condition' in result[0]['reason']

    def test_handles_mixed_actions(self):
        text = '{"resolutions": [{"name": "A", "action": "match", "match_to": "B"}, {"name": "C", "action": "create", "entity": {"name": "C", "type": "place"}}, {"name": "D", "action": "drop", "reason": "spurious"}]}'
        result = parse_resolution_response(text)
        assert len(result) == 3

    def test_handles_malformed_json(self):
        result = parse_resolution_response('not json')
        assert result == []

    def test_handles_json_in_markdown(self):
        text = '```json\n{"resolutions": [{"name": "A", "action": "drop", "reason": "x"}]}\n```'
        result = parse_resolution_response(text)
        assert len(result) == 1


class TestApplyResolutions:
    def test_match_updates_edges(self):
        nodes = [_node('A'), _node('Cristina Kahlo')]
        edges = [_edge('Cristina', 'A')]
        resolutions = [{'name': 'Cristina', 'action': 'match', 'match_to': 'Cristina Kahlo'}]
        report = apply_resolutions(resolutions, nodes, edges)
        assert edges[0]['source'] == 'Cristina Kahlo'
        assert report['matched'] == 1

    def test_match_to_unknown_node_skipped(self):
        nodes = [_node('A')]
        edges = [_edge('X', 'A')]
        resolutions = [{'name': 'X', 'action': 'match', 'match_to': 'NONEXISTENT'}]
        report = apply_resolutions(resolutions, nodes, edges)
        assert edges[0]['source'] == 'X'
        assert report['match_failed'] == 1

    def test_create_adds_node(self):
        nodes = [_node('A')]
        edges = [_edge('Detroit', 'A')]
        resolutions = [{'name': 'Detroit', 'action': 'create',
                        'entity': {'name': 'Detroit', 'type': 'place'}}]
        report = apply_resolutions(resolutions, nodes, edges)
        assert any(n['name'] == 'Detroit' for n in nodes)
        assert report['created'] == 1

    def test_drop_removes_edges(self):
        nodes = [_node('A')]
        edges = [_edge('A', 'B'), _edge('X', 'A')]
        resolutions = [{'name': 'X', 'action': 'drop', 'reason': 'spurious'}]
        report = apply_resolutions(resolutions, nodes, edges)
        assert len(edges) == 1
        assert edges[0]['source'] == 'A'
        assert report['dropped'] == 1

    def test_create_with_different_name_updates_edges(self):
        """When LLM creates 'Natalia Sedova' for dangling 'Natalia', edges update."""
        nodes = [_node('A')]
        edges = [_edge('Natalia', 'A'), _edge('A', 'Natalia')]
        resolutions = [{'name': 'Natalia', 'action': 'create',
                        'entity': {'name': 'Natalia Sedova', 'type': 'person'}}]
        report = apply_resolutions(resolutions, nodes, edges)
        assert any(n['name'] == 'Natalia Sedova' for n in nodes)
        assert edges[0]['source'] == 'Natalia Sedova'
        assert edges[1]['target'] == 'Natalia Sedova'
        assert report['created'] == 1

    def test_drop_removes_multiple_edges(self):
        nodes = [_node('A')]
        edges = [_edge('X', 'A'), _edge('A', 'X'), _edge('A', 'B')]
        resolutions = [{'name': 'X', 'action': 'drop', 'reason': 'spurious'}]
        report = apply_resolutions(resolutions, nodes, edges)
        assert len(edges) == 1
        assert edges[0]['target'] == 'B'
        assert report['dropped_edges'] == 2


class TestRunDanglingResolution:
    def test_no_dangles_returns_empty_report(self):
        corpus = {
            'nodes': [_node('A'), _node('B')],
            'edges': [_edge('A', 'B')],
            'stats': {},
        }
        passages = [{'id': 'p-001', 'text': 'Text'}]
        report = run_dangling_resolution(corpus, passages, client=None)
        assert report['total_dangling'] == 0

    def test_orphan_backfills_removed(self):
        """Back-filled nodes with zero edges are cleaned up."""
        corpus = {
            'nodes': [_node('A', source_passages=['p-001']),
                      {'name': 'Orphan', 'subgraph_type': 'place',
                       'source_passages': [], 'extraction_method': 'entity-backfill'}],
            'edges': [_edge('A', 'A')],
            'stats': {},
        }
        passages = [{'id': 'p-001', 'text': 'Text'}]
        report = run_dangling_resolution(corpus, passages, client=None)
        assert not any(n['name'] == 'Orphan' for n in corpus['nodes'])
        assert report['orphan_backfills_removed'] == 1

    def test_connected_backfills_kept(self):
        """Back-filled nodes WITH edges are not removed."""
        corpus = {
            'nodes': [_node('A', source_passages=['p-001']),
                      {'name': 'Connected', 'subgraph_type': 'place',
                       'source_passages': [], 'extraction_method': 'entity-backfill'}],
            'edges': [_edge('A', 'Connected')],
            'stats': {},
        }
        passages = [{'id': 'p-001', 'text': 'Text'}]
        report = run_dangling_resolution(corpus, passages, client=None)
        assert any(n['name'] == 'Connected' for n in corpus['nodes'])
        assert report['orphan_backfills_removed'] == 0

    def test_dangles_without_client(self):
        corpus = {
            'nodes': [_node('A', source_passages=['p-001'])],
            'edges': [_edge('X', 'A', source_passage='p-001')],
            'stats': {},
        }
        passages = [{'id': 'p-001', 'text': 'Text about X'}]
        report = run_dangling_resolution(corpus, passages, client=None)
        assert report['total_dangling'] == 1
        assert report['passages_checked'] == 1
        assert report['matched'] == 0
