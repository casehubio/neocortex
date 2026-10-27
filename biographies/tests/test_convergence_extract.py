"""Tests for convergence extraction helpers in extract_structure.py."""

import json
import pytest

from extract_structure import group_memories_by_entity, build_convergence_prompt, merge_extraction_results


class TestGroupMemoriesByEntity:
    def test_groups_by_subject(self):
        memories = [
            {'text': 'Reflection 1', 'subject': 'Frida Kahlo', 'structurally_extracted': False},
            {'text': 'Mood 1', 'subject': 'Frida Kahlo', 'structurally_extracted': False},
            {'text': 'Reflection 2', 'subject': 'Diego Rivera', 'structurally_extracted': False},
        ]
        groups = group_memories_by_entity(memories)
        assert 'Frida Kahlo' in groups
        assert len(groups['Frida Kahlo']) == 2
        assert 'Diego Rivera' in groups
        assert len(groups['Diego Rivera']) == 1

    def test_skips_already_extracted(self):
        memories = [
            {'text': 'Already done', 'subject': 'Frida Kahlo', 'structurally_extracted': True},
            {'text': 'Not done', 'subject': 'Frida Kahlo'},
        ]
        groups = group_memories_by_entity(memories)
        assert len(groups['Frida Kahlo']) == 1
        assert groups['Frida Kahlo'][0]['text'] == 'Not done'

    def test_skips_no_subject(self):
        memories = [{'text': 'No subject'}]
        groups = group_memories_by_entity(memories)
        assert len(groups) == 0


class TestBuildConvergencePrompt:
    def test_includes_entity_name(self):
        prompt = build_convergence_prompt('Frida Kahlo', [{'text': 'Pain to art'}], ['Frida Kahlo'], [])
        assert 'Frida Kahlo' in prompt

    def test_includes_memory_text(self):
        prompt = build_convergence_prompt('Frida', [{'text': 'Pain to art'}], [], [])
        assert 'Pain to art' in prompt

    def test_includes_known_entities(self):
        prompt = build_convergence_prompt('Frida', [{'text': 'x'}], ['Diego Rivera', 'Mexico City'], [])
        assert 'Diego Rivera' in prompt
        assert 'Mexico City' in prompt

    def test_includes_known_edges(self):
        prompt = build_convergence_prompt('Frida', [{'text': 'x'}], [], ['Frida|Diego|married-to'])
        assert 'married-to' in prompt


class TestMergeExtractionResults:
    def test_entity_dedup_by_name(self):
        existing = {
            'entities': [{'name': 'Frida Kahlo', 'type': 'person', 'source_passages': ['p1']}],
            'edges': [],
            'memories': [],
        }
        new_results = {
            'entities': [
                {'name': 'Frida Kahlo', 'type': 'person', 'new_prop': 'val'},
                {'name': 'New Entity', 'type': 'concept'},
            ],
            'edges': [],
            'memories': [],
        }
        merged = merge_extraction_results(existing, new_results, pass_n=2)
        entity_names = [e['name'] for e in merged['entities']]
        assert entity_names.count('Frida Kahlo') == 1
        assert 'New Entity' in entity_names

    def test_entity_merge_adds_new_properties(self):
        existing = {
            'entities': [{'name': 'Frida Kahlo', 'type': 'person'}],
            'edges': [], 'memories': [],
        }
        new_results = {
            'entities': [{'name': 'Frida Kahlo', 'type': 'person', 'nationality': 'Mexican'}],
            'edges': [], 'memories': [],
        }
        merged = merge_extraction_results(existing, new_results, pass_n=2)
        frida = next(e for e in merged['entities'] if e['name'] == 'Frida Kahlo')
        assert frida['nationality'] == 'Mexican'

    def test_new_entity_gets_convergence_provenance(self):
        existing = {'entities': [], 'edges': [], 'memories': []}
        new_results = {
            'entities': [{'name': 'New', 'type': 'concept'}],
            'edges': [], 'memories': [],
        }
        merged = merge_extraction_results(existing, new_results, pass_n=3)
        ent = merged['entities'][0]
        assert ent['extraction_pass'] == 'convergence-pass-3'
        assert ent['extraction_method'] == 'llm-convergence-extract'

    def test_edge_dedup(self):
        existing = {
            'entities': [],
            'edges': [{'source': 'A', 'target': 'B', 'type': 'knows'}],
            'memories': [],
        }
        new_results = {
            'entities': [],
            'edges': [
                {'source': 'A', 'target': 'B', 'type': 'knows'},
                {'source': 'A', 'target': 'C', 'type': 'knows'},
            ],
            'memories': [],
        }
        merged = merge_extraction_results(existing, new_results, pass_n=2)
        assert len(merged['edges']) == 2

    def test_new_memories_get_provenance(self):
        existing = {'entities': [], 'edges': [], 'memories': []}
        new_results = {
            'entities': [], 'edges': [],
            'memories': [{'text': 'New insight', 'domain': 'reflection'}],
        }
        merged = merge_extraction_results(existing, new_results, pass_n=2)
        mem = merged['memories'][0]
        assert mem['extraction_pass'] == 'convergence-pass-2'
        assert mem['convergence_pass'] == 2

    def test_confidence_clamped_on_new_entity(self):
        existing = {'entities': [], 'edges': [], 'memories': []}
        new_results = {
            'entities': [{'name': 'X', 'type': 'concept', 'confidence': {'origin': 'INFERRED', 'value': 0.95}}],
            'edges': [], 'memories': [],
        }
        merged = merge_extraction_results(existing, new_results, pass_n=2)
        assert merged['entities'][0]['confidence']['value'] == 0.8
