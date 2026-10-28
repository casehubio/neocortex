#!/usr/bin/env python3
"""Tests for extract_personality.py — schema loader, prompt builder, and validation."""

import os
import sys
import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'scripts'))

SCHEMA_PATH = os.path.join(os.path.dirname(__file__), '..', 'schemas', 'personality-vocab.json')


def test_load_vocab_schema_returns_all_framework_keys():
    from extract_personality import load_vocab_schema
    schema = load_vocab_schema(SCHEMA_PATH)
    assert 'mbti' in schema
    assert 'bigFive' in schema
    assert 'disc' in schema
    assert 'enneagram' in schema
    assert 'belbin' in schema
    assert 'sdi' in schema
    assert 'jungian' in schema
    assert 'archetype' in schema
    assert 'axisMappings' in schema


def test_build_frameworks_prompt_contains_valid_values():
    from extract_personality import load_vocab_schema, build_frameworks_prompt
    schema = load_vocab_schema(SCHEMA_PATH)
    prompt = build_frameworks_prompt(schema, "Test subject biography text")
    prompt_lower = prompt.lower()
    assert 'intj' in prompt_lower
    assert 'openness' in prompt_lower
    assert 'dominance' in prompt_lower
    assert 'reformer' in prompt_lower


def test_build_frameworks_prompt_contains_ampd_facets():
    from extract_personality import load_vocab_schema, build_frameworks_prompt
    schema = load_vocab_schema(SCHEMA_PATH)
    prompt = build_frameworks_prompt(schema, "Test subject biography text")
    prompt_lower = prompt.lower()
    assert 'callousness' in prompt_lower
    assert 'emotional-lability' in prompt_lower or 'emotional lability' in prompt_lower
    assert 'anxiousness' in prompt_lower


def test_build_frameworks_prompt_contains_attachment_and_locus():
    from extract_personality import load_vocab_schema, build_frameworks_prompt
    schema = load_vocab_schema(SCHEMA_PATH)
    prompt = build_frameworks_prompt(schema, "Test subject biography text")
    prompt_lower = prompt.lower()
    assert 'attachment' in prompt_lower
    assert 'locus' in prompt_lower or 'internal' in prompt_lower


def test_build_archetypes_prompt_contains_all_families():
    from extract_personality import load_vocab_schema, build_archetypes_prompt
    schema = load_vocab_schema(SCHEMA_PATH)
    prompt = build_archetypes_prompt(schema, "Test subject biography text")
    prompt_lower = prompt.lower()
    for family in ['caregiver', 'creator', 'everyman', 'explorer', 'hero',
                    'innocent', 'jester', 'lover', 'magician', 'rebel', 'sage', 'sovereign']:
        assert family in prompt_lower, f"Missing family: {family}"


def test_build_archetypes_prompt_contains_sub_archetypes_with_adjectives():
    from extract_personality import load_vocab_schema, build_archetypes_prompt
    schema = load_vocab_schema(SCHEMA_PATH)
    prompt = build_archetypes_prompt(schema, "Test subject biography text")
    prompt_lower = prompt.lower()
    assert 'angel' in prompt_lower
    assert 'ruler' in prompt_lower
    assert 'artist' in prompt_lower
    assert 'expressive' in prompt_lower  # artist's validAdjective


# --- Validation tests ---

def test_validate_output_accepts_valid_mbti():
    from extract_personality import load_vocab_schema, validate_output
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {'frameworks': {'mbti': {'type': 'infp', 'evidence': 'test', 'confidence': 0.8}}}
    validated, warnings = validate_output(result, schema)
    assert validated['frameworks']['mbti']['type'] == 'infp'
    assert len(warnings) == 0


def test_validate_output_rejects_invalid_mbti():
    from extract_personality import load_vocab_schema, validate_output
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {'frameworks': {'mbti': {'type': 'intj-a', 'evidence': 'test', 'confidence': 0.8}}}
    validated, warnings = validate_output(result, schema)
    assert validated['frameworks']['mbti']['type'] is None
    assert len(warnings) == 1


def test_validate_output_accepts_valid_enneagram():
    from extract_personality import load_vocab_schema, validate_output
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {'frameworks': {'enneagram': {'type': 'type-4', 'wing': '5', 'evidence': 'test', 'confidence': 0.7}}}
    validated, warnings = validate_output(result, schema)
    assert validated['frameworks']['enneagram']['type'] == 'type-4'
    assert len(warnings) == 0


def test_validate_output_rejects_invalid_archetype():
    from extract_personality import load_vocab_schema, validate_output
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {'archetypes': [{'rank': 1, 'family': 'creator', 'sub_archetype': 'inventor',
                               'adjectives': ['bold'], 'evidence': 'test', 'confidence': 0.9}]}
    validated, warnings = validate_output(result, schema)
    assert validated['archetypes'][0]['sub_archetype'] is None
    assert len(warnings) >= 1


def test_validate_output_filters_invalid_adjectives():
    from extract_personality import load_vocab_schema, validate_output
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {'archetypes': [{'rank': 1, 'family': 'creator', 'sub_archetype': 'artist',
                               'adjectives': ['expressive', 'badword', 'authentic'],
                               'evidence': 'test', 'confidence': 0.9}]}
    validated, warnings = validate_output(result, schema)
    assert validated['archetypes'][0]['sub_archetype'] == 'artist'
    assert 'expressive' in validated['archetypes'][0]['adjectives']
    assert 'authentic' in validated['archetypes'][0]['adjectives']
    assert 'badword' not in validated['archetypes'][0]['adjectives']
    assert len(warnings) == 1


def test_validate_output_rejects_invalid_attachment():
    from extract_personality import load_vocab_schema, validate_output
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {'frameworks': {'attachment': {'style': 'disorganized', 'anxiety': 0.5, 'avoidance': 0.5}}}
    validated, warnings = validate_output(result, schema)
    assert validated['frameworks']['attachment']['style'] is None
    assert len(warnings) == 1


# --- Consistency tests ---

def test_check_consistency_flags_extraversion_intj():
    from extract_personality import load_vocab_schema, check_consistency
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {
        'frameworks': {
            'ocean': {'extraversion': 0.85, 'openness': 0.5, 'conscientiousness': 0.5,
                      'agreeableness': 0.5, 'neuroticism': 0.5},
            'mbti': {'type': 'intj'}
        }
    }
    inconsistencies = check_consistency(result, schema)
    assert len(inconsistencies) >= 1
    assert any('socialOrient' in str(i) for i in inconsistencies)


def test_check_consistency_accepts_coherent_profile():
    from extract_personality import load_vocab_schema, check_consistency
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {
        'frameworks': {
            'ocean': {'extraversion': 0.3, 'openness': 0.8, 'conscientiousness': 0.4,
                      'agreeableness': 0.4, 'neuroticism': 0.7},
            'mbti': {'type': 'infp'},
            'disc': {'primary': 'influence'},
            'thomas_kilmann': {'primary': 'collaborating'}
        }
    }
    inconsistencies = check_consistency(result, schema)
    assert len(inconsistencies) == 0


def test_check_consistency_flags_disc_tk_contradiction():
    from extract_personality import load_vocab_schema, check_consistency
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {
        'frameworks': {
            'disc': {'primary': 'dominance'},
            'thomas_kilmann': {'primary': 'accommodating'}
        }
    }
    inconsistencies = check_consistency(result, schema)
    assert len(inconsistencies) >= 1
    assert any('conflictMode' in str(i) for i in inconsistencies)


# --- Disposition derivation tests ---

def test_derive_disposition_from_frameworks():
    from extract_personality import load_vocab_schema, derive_disposition
    schema = load_vocab_schema(SCHEMA_PATH)
    result = {
        'frameworks': {
            'mbti': {'type': 'infp'},
            'ocean': {'openness': 0.85, 'conscientiousness': 0.35, 'extraversion': 0.3,
                      'agreeableness': 0.6, 'neuroticism': 0.7}
        }
    }
    disposition = derive_disposition(result, schema)
    assert 'socialOrient' in disposition
    assert 'ruleFollowing' in disposition
    assert disposition['socialOrient']['term'] is not None
    assert disposition['ruleFollowing']['term'] is not None
