"""Tests for LLM semantic dedup (#542)."""

import pytest

from assemble_corpus import (
    build_dedup_batches, apply_llm_dedup_verdicts, union_find_merge,
)


class TestBuildDedupBatches:
    def test_batches_near_misses(self):
        near_misses = [
            {'a': 'Goal A', 'b': 'Goal B', 'jw_score': 0.88},
            {'a': 'Goal C', 'b': 'Goal D', 'jw_score': 0.80},
        ]
        batches = build_dedup_batches(near_misses, batch_size=20)
        assert len(batches) == 1
        assert len(batches[0]) == 2

    def test_splits_into_multiple_batches(self):
        near_misses = [
            {'a': f'Goal {i}A', 'b': f'Goal {i}B', 'jw_score': 0.8}
            for i in range(25)
        ]
        batches = build_dedup_batches(near_misses, batch_size=10)
        assert len(batches) == 3
        assert len(batches[0]) == 10
        assert len(batches[1]) == 10
        assert len(batches[2]) == 5

    def test_empty_near_misses(self):
        batches = build_dedup_batches([], batch_size=20)
        assert len(batches) == 0


class TestUnionFindMerge:
    def test_single_pair(self):
        verdicts = [{'a': 'Goal A', 'b': 'Goal B', 'verdict': 'DUPLICATE'}]
        groups = union_find_merge(verdicts)
        assert len(groups) == 1
        assert set(groups[0]) == {'Goal A', 'Goal B'}

    def test_transitive_merge(self):
        verdicts = [
            {'a': 'Goal A', 'b': 'Goal B', 'verdict': 'DUPLICATE'},
            {'a': 'Goal B', 'b': 'Goal C', 'verdict': 'DUPLICATE'},
        ]
        groups = union_find_merge(verdicts)
        assert len(groups) == 1
        assert set(groups[0]) == {'Goal A', 'Goal B', 'Goal C'}

    def test_skips_related_and_distinct(self):
        verdicts = [
            {'a': 'Goal A', 'b': 'Goal B', 'verdict': 'DUPLICATE'},
            {'a': 'Goal C', 'b': 'Goal D', 'verdict': 'RELATED'},
            {'a': 'Goal E', 'b': 'Goal F', 'verdict': 'DISTINCT'},
        ]
        groups = union_find_merge(verdicts)
        assert len(groups) == 1
        assert set(groups[0]) == {'Goal A', 'Goal B'}

    def test_multiple_independent_groups(self):
        verdicts = [
            {'a': 'Goal A', 'b': 'Goal B', 'verdict': 'DUPLICATE'},
            {'a': 'Goal C', 'b': 'Goal D', 'verdict': 'DUPLICATE'},
        ]
        groups = union_find_merge(verdicts)
        assert len(groups) == 2

    def test_empty_verdicts(self):
        groups = union_find_merge([])
        assert len(groups) == 0


class TestApplyLlmDedupVerdicts:
    def test_merges_duplicate_goals(self):
        nodes = [
            {'name': 'Artistic mastery', 'subgraph_type': 'goal', 'goal_tier': 'thematic',
             'source_passages': ['p1']},
            {'name': 'Master painting', 'subgraph_type': 'goal', 'goal_tier': 'thematic',
             'source_passages': ['p2']},
            {'name': 'Physical health', 'subgraph_type': 'goal', 'goal_tier': 'strategic'},
        ]
        merge_groups = [['Artistic mastery', 'Master painting']]
        result, report = apply_llm_dedup_verdicts(nodes, merge_groups)
        goal_names = [n['name'] for n in result if n.get('subgraph_type') == 'goal']
        assert len(goal_names) == 2
        assert 'Artistic mastery' in goal_names
        assert 'Physical health' in goal_names
        canonical = next(n for n in result if n['name'] == 'Artistic mastery')
        assert 'Master painting' in canonical.get('merged_from', [])
        assert 'p2' in canonical.get('source_passages', [])

    def test_keeps_longest_name_as_canonical(self):
        nodes = [
            {'name': 'Art', 'subgraph_type': 'goal', 'source_passages': []},
            {'name': 'Artistic expression', 'subgraph_type': 'goal', 'source_passages': []},
        ]
        merge_groups = [['Art', 'Artistic expression']]
        result, _ = apply_llm_dedup_verdicts(nodes, merge_groups)
        goal_names = [n['name'] for n in result if n.get('subgraph_type') == 'goal']
        assert 'Artistic expression' in goal_names
        assert 'Art' not in goal_names

    def test_non_goals_untouched(self):
        nodes = [
            {'name': 'Frida Kahlo', 'subgraph_type': 'person'},
            {'name': 'Goal A', 'subgraph_type': 'goal', 'source_passages': []},
            {'name': 'Goal B', 'subgraph_type': 'goal', 'source_passages': []},
        ]
        merge_groups = [['Goal A', 'Goal B']]
        result, _ = apply_llm_dedup_verdicts(nodes, merge_groups)
        assert any(n['name'] == 'Frida Kahlo' for n in result)

    def test_report_structure(self):
        nodes = [
            {'name': 'A', 'subgraph_type': 'goal', 'source_passages': []},
            {'name': 'B', 'subgraph_type': 'goal', 'source_passages': []},
        ]
        merge_groups = [['A', 'B']]
        _, report = apply_llm_dedup_verdicts(nodes, merge_groups)
        assert 'llm_merged' in report
        assert 'llm_clusters' in report
        assert report['llm_merged'] == 1

    def test_empty_groups_no_change(self):
        nodes = [
            {'name': 'A', 'subgraph_type': 'goal', 'source_passages': []},
        ]
        result, report = apply_llm_dedup_verdicts(nodes, [])
        assert len(result) == 1
        assert report['llm_merged'] == 0
