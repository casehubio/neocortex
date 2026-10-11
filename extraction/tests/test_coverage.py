"""Tests for coverage gate library."""

import os
import pytest

REGISTRY_PATH = os.path.join(os.path.dirname(__file__), '..', 'docs', 'field-registry.yaml')


def test_load_registry_returns_categories():
    from coverage import load_registry
    registry = load_registry(REGISTRY_PATH)
    assert 'node_fields' in registry['categories']
    assert 'enrichment_fields' in registry['categories']
    assert 'entity_level_fields' in registry['categories']
    assert 'importer_derivations' in registry['categories']
    assert len(registry['categories']) == 5


def test_load_registry_fields_have_required_keys():
    from coverage import load_registry
    registry = load_registry(REGISTRY_PATH)
    for cat_name, cat in registry['categories'].items():
        for field in cat['fields']:
            assert 'id' in field, f"Field missing id in {cat_name}"
            assert 'name' in field, f"Field missing name in {cat_name}"
            assert 'stages' in field, f"Field {field['id']} missing stages"
            assert 'status' in field, f"Field {field['id']} missing status"


def test_load_registry_has_85_fields():
    from coverage import load_registry
    registry = load_registry(REGISTRY_PATH)
    total = sum(len(c['fields']) for c in registry['categories'].values())
    assert total == 85


def test_check_node_coverage_complete_node():
    from coverage import load_registry, check_node_coverage
    node = {
        'name': 'Frida Kahlo', 'subgraph_type': 'person',
        'properties': {'role': 'artist'},
        'traits': ['Personable'],
        'trait_properties': {'role': 'artist', 'occupation': 'painter'},
        'pad': {'pleasure': 0.5, 'arousal': 0.3, 'dominance': 0.7},
        'confidence': {'origin': 'STATED', 'value': 0.9},
        'node_ref': {'scheme': 'biography', 'id': 'frida', 'qualifier': '001'},
        'valid_from': '1907-07-06T00:00:00Z',
        'valid_until': '1954-07-13T00:00:00Z',
    }
    registry = load_registry(REGISTRY_PATH)
    report = check_node_coverage([node], registry)
    assert report.populated > 5
    assert report.pct > 40


def test_check_node_coverage_minimal_node():
    from coverage import load_registry, check_node_coverage
    node = {'name': 'Test', 'subgraph_type': 'concept'}
    registry = load_registry(REGISTRY_PATH)
    report = check_node_coverage([node], registry)
    assert len(report.gaps) > 0
    assert report.pct < 100


def test_goal_fields_only_checked_for_goals():
    from coverage import load_registry, check_node_coverage
    node = {'name': 'Art', 'subgraph_type': 'concept'}
    registry = load_registry(REGISTRY_PATH)
    report = check_node_coverage([node], registry)
    goal_gap_ids = {g['id'] for g in report.gaps}
    assert 'N13' not in goal_gap_ids
    assert 'N14' not in goal_gap_ids
    assert 'N20' not in goal_gap_ids


def test_goal_fields_checked_for_goals():
    from coverage import load_registry, check_node_coverage
    node = {'name': 'Paint masterpiece', 'subgraph_type': 'goal'}
    registry = load_registry(REGISTRY_PATH)
    report = check_node_coverage([node], registry)
    goal_gap_ids = {g['id'] for g in report.gaps}
    assert 'N13' in goal_gap_ids  # goal_horizon missing


def test_check_memory_coverage_all_experience():
    from coverage import load_registry, check_memory_coverage
    memories = [
        {'text': 'Test', 'domain': 'experience', 'event_type': 'observation',
         'source_passage': 'p-001', 'enrichment_version': 'v1.0', 'period': '1920'},
    ]
    registry = load_registry(REGISTRY_PATH)
    report = check_memory_coverage(memories, registry)
    domain_gaps = [g for g in report.gaps if g['id'] == 'M2']
    assert len(domain_gaps) == 1
    assert 'hardcoded' in domain_gaps[0].get('note', '')


def test_check_memory_coverage_domain_split():
    from coverage import load_registry, check_memory_coverage
    memories = [
        {'text': 'Test1', 'domain': 'experience', 'event_type': 'observation',
         'source_passage': 'p-001', 'enrichment_version': 'v1.0', 'period': '1920'},
        {'text': 'Test2', 'domain': 'relationship', 'event_type': 'observation',
         'source_passage': 'p-002', 'enrichment_version': 'v1.0', 'period': '1920'},
    ]
    registry = load_registry(REGISTRY_PATH)
    report = check_memory_coverage(memories, registry)
    domain_gaps = [g for g in report.gaps if g['id'] == 'M2']
    assert len(domain_gaps) == 0


def test_check_enrichment_coverage_with_intensity():
    from coverage import load_registry, check_enrichment_coverage
    memory = {
        'pad': {'pleasure': 0.5, 'arousal': 0.3, 'dominance': 0.7},
        'occ_emotions': [{'type': 'joy', 'intensity': 0.8, 'source': 'INTRINSIC'}],
        'sec_appraisal': {'relevance': 0.9},
        'action_tendencies': [{'type': 'approach', 'intensity': 0.85}],
        'cognitive_distortions': [{'type': 'all_or_nothing', 'strength': 0.7}],
        'drive_impact': {'curiosity': 0.5},
        'sub_thoughts': [{'type': 'affect-observation', 'text': 'felt joy'}],
        'confidence': {'origin': 'STATED', 'value': 0.9},
    }
    registry = load_registry(REGISTRY_PATH)
    report = check_enrichment_coverage([memory], registry)
    assert report.populated > 5


def test_check_enrichment_coverage_old_format():
    from coverage import load_registry, check_enrichment_coverage
    memory = {
        'pad': {'pleasure': 0.5, 'arousal': 0.3, 'dominance': 0.7},
        'occ_emotions': [{'type': 'joy', 'intensity': 0.8}],
        'sec_appraisal': {'relevance': 0.9},
        'action_tendencies': ['approach', 'avoidance'],  # old string format
        'cognitive_distortions': [{'type': 'all_or_nothing'}],  # no strength
        'drive_impact': {'curiosity': 0.5},
        'confidence': {'origin': 'STATED', 'value': 0.9},
    }
    registry = load_registry(REGISTRY_PATH)
    report = check_enrichment_coverage([memory], registry)
    intensity_gaps = [g for g in report.gaps if g['id'] == 'E6']
    assert len(intensity_gaps) == 1  # old format lacks intensity


def test_format_report():
    from coverage import CoverageReport, format_report
    reports = [
        CoverageReport('node_fields', 5, 23, 21.7, 60, 'FAIL', [{'id': 'N4', 'name': 'traits', 'missing': 166, 'total': 166}]),
        CoverageReport('memory_fields', 8, 17, 47.1, 50, 'WARN', []),
    ]
    text = format_report(reports)
    assert 'node_fields' in text
    assert 'FAIL' in text
    assert 'WARN' in text
    assert 'Overall:' in text
