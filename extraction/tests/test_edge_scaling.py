"""Tests for edge extraction scaling improvements (#547)."""

import pytest

from extract_structure import build_entity_context, infer_cooccurrence_edges


class TestBuildEntityContext:
    def test_formats_entity_names_and_types(self):
        entities = [
            {'name': 'Frida Kahlo', 'type': 'person'},
            {'name': 'Mexico City', 'type': 'place'},
        ]
        ctx = build_entity_context(entities)
        assert 'Frida Kahlo (person)' in ctx
        assert 'Mexico City (place)' in ctx

    def test_includes_goal_fields(self):
        entities = [
            {'name': 'Artistic independence', 'type': 'goal',
             'goal_tier': 'thematic', 'content_domain': 'self-actualisation'},
        ]
        ctx = build_entity_context(entities)
        assert 'Artistic independence (goal' in ctx
        assert 'thematic' in ctx

    def test_empty_list_returns_none_marker(self):
        ctx = build_entity_context([])
        assert '(none)' in ctx

    def test_truncates_long_lists(self):
        entities = [{'name': f'Entity {i}', 'type': 'concept'} for i in range(60)]
        ctx = build_entity_context(entities)
        lines = [l for l in ctx.strip().split('\n') if l.strip().startswith('-')]
        assert len(lines) <= 50


class TestInferCooccurrenceEdges:
    def test_creates_edge_for_cooccurring_entities(self):
        entities = [
            {'name': 'Frida Kahlo', 'type': 'person'},
            {'name': 'Diego Rivera', 'type': 'person'},
        ]
        memories = [
            {'text': 'Frida and Diego painted together', 'subject': 'Frida Kahlo',
             'related_entities': ['Diego Rivera'], 'domain': 'experience'},
        ]
        edges = infer_cooccurrence_edges(entities, memories)
        assert len(edges) >= 1
        edge = edges[0]
        assert edge['source'] == 'Frida Kahlo'
        assert edge['target'] == 'Diego Rivera'
        assert edge['confidence_origin'] == 'INFERRED'
        assert edge['validation_tier'] == 'UNVALIDATED'
        assert edge['type'] == 'associated-with'
        assert edge.get('extraction_method') == 'cooccurrence-inference'

    def test_skips_when_no_related_entities(self):
        entities = [{'name': 'Frida Kahlo', 'type': 'person'}]
        memories = [
            {'text': 'Frida painted', 'subject': 'Frida Kahlo', 'domain': 'experience'},
        ]
        edges = infer_cooccurrence_edges(entities, memories)
        assert len(edges) == 0

    def test_deduplicates_edges(self):
        entities = [
            {'name': 'Frida Kahlo', 'type': 'person'},
            {'name': 'Diego Rivera', 'type': 'person'},
        ]
        memories = [
            {'text': 'Frida and Diego 1', 'subject': 'Frida Kahlo',
             'related_entities': ['Diego Rivera'], 'domain': 'experience'},
            {'text': 'Frida and Diego 2', 'subject': 'Frida Kahlo',
             'related_entities': ['Diego Rivera'], 'domain': 'experience'},
        ]
        edges = infer_cooccurrence_edges(entities, memories)
        pairs = [(e['source'], e['target']) for e in edges]
        assert len(set(pairs)) == len(pairs)

    def test_skips_self_references(self):
        entities = [{'name': 'Frida Kahlo', 'type': 'person'}]
        memories = [
            {'text': 'Frida reflected', 'subject': 'Frida Kahlo',
             'related_entities': ['Frida Kahlo'], 'domain': 'reflection'},
        ]
        edges = infer_cooccurrence_edges(entities, memories)
        assert len(edges) == 0

    def test_only_uses_known_entities(self):
        entities = [{'name': 'Frida Kahlo', 'type': 'person'}]
        memories = [
            {'text': 'Frida met unknown', 'subject': 'Frida Kahlo',
             'related_entities': ['Unknown Person'], 'domain': 'experience'},
        ]
        edges = infer_cooccurrence_edges(entities, memories)
        assert len(edges) == 0
