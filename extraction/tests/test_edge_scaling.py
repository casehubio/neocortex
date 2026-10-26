"""Tests for edge extraction scaling improvements (#547)."""

import pytest

from extract_structure import build_entity_context


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


