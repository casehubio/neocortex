"""Tests for entity PAD derivation from memory averages (#548)."""

import pytest

from assemble_corpus import derive_entity_pad


class TestDeriveEntityPad:
    def test_derives_pad_from_memories(self):
        nodes = [
            {'name': 'Mexico City', 'type': 'place'},
        ]
        memories = [
            {'subject': 'Frida Kahlo', 'related_entities': ['Mexico City'],
             'pad': {'pleasure': 0.6, 'arousal': 0.4, 'dominance': 0.2}},
            {'subject': 'Frida Kahlo', 'related_entities': ['Mexico City'],
             'pad': {'pleasure': 0.8, 'arousal': 0.6, 'dominance': 0.4}},
        ]
        derive_entity_pad(nodes, memories)
        assert nodes[0]['pad'] is not None
        assert abs(nodes[0]['pad']['pleasure'] - 0.7) < 0.01
        assert abs(nodes[0]['pad']['arousal'] - 0.5) < 0.01
        assert abs(nodes[0]['pad']['dominance'] - 0.3) < 0.01

    def test_skips_entities_with_existing_pad(self):
        nodes = [
            {'name': 'Frida Kahlo', 'type': 'person',
             'pad': {'pleasure': 0.1, 'arousal': 0.2, 'dominance': 0.3}},
        ]
        memories = [
            {'subject': 'Frida Kahlo', 'related_entities': [],
             'pad': {'pleasure': 0.9, 'arousal': 0.9, 'dominance': 0.9}},
        ]
        derive_entity_pad(nodes, memories)
        assert nodes[0]['pad']['pleasure'] == 0.1

    def test_uses_subject_field_too(self):
        nodes = [
            {'name': 'Frida Kahlo', 'type': 'person'},
        ]
        memories = [
            {'subject': 'Frida Kahlo', 'related_entities': [],
             'pad': {'pleasure': 0.5, 'arousal': 0.5, 'dominance': 0.5}},
        ]
        derive_entity_pad(nodes, memories)
        assert nodes[0]['pad'] is not None
        assert nodes[0]['pad']['pleasure'] == 0.5

    def test_skips_when_no_memories_match(self):
        nodes = [
            {'name': 'Unknown Place', 'type': 'place'},
        ]
        memories = [
            {'subject': 'Frida Kahlo', 'related_entities': ['Mexico City'],
             'pad': {'pleasure': 0.5, 'arousal': 0.5, 'dominance': 0.5}},
        ]
        derive_entity_pad(nodes, memories)
        assert 'pad' not in nodes[0]

    def test_skips_memories_without_pad(self):
        nodes = [
            {'name': 'Mexico City', 'type': 'place'},
        ]
        memories = [
            {'subject': 'Frida Kahlo', 'related_entities': ['Mexico City']},
        ]
        derive_entity_pad(nodes, memories)
        assert 'pad' not in nodes[0]

    def test_case_insensitive_matching(self):
        nodes = [
            {'name': 'mexico city', 'type': 'place'},
        ]
        memories = [
            {'subject': 'Frida', 'related_entities': ['Mexico City'],
             'pad': {'pleasure': 0.4, 'arousal': 0.3, 'dominance': 0.2}},
        ]
        derive_entity_pad(nodes, memories)
        assert nodes[0]['pad'] is not None

    def test_rounds_to_two_decimals(self):
        nodes = [
            {'name': 'Place', 'type': 'place'},
        ]
        memories = [
            {'subject': 'A', 'related_entities': ['Place'],
             'pad': {'pleasure': 0.333, 'arousal': 0.666, 'dominance': 0.111}},
            {'subject': 'A', 'related_entities': ['Place'],
             'pad': {'pleasure': 0.334, 'arousal': 0.667, 'dominance': 0.112}},
        ]
        derive_entity_pad(nodes, memories)
        pad = nodes[0]['pad']
        assert pad['pleasure'] == round(pad['pleasure'], 2)
        assert pad['arousal'] == round(pad['arousal'], 2)
        assert pad['dominance'] == round(pad['dominance'], 2)
