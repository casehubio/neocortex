#!/usr/bin/env python3
"""Step 6: Assemble importable corpus from enriched data.

Input:  enriched.json + nlp-baseline.json
Output: corpus.json — ready for FridaSeeder or BiographyHandler import

Also produces:
- coverage-report.json — NLP coverage audit with gap classification
- model-coverage.json — which cognitive model dimensions are exercised

Usage:
    python3 assemble_corpus.py <enriched.json> <nlp-baseline.json> [--output corpus.json]
"""

import argparse
import json
import os
import sys
from collections import Counter, defaultdict

import yaml


# --- Noise detection ---

NOISE_TYPES = {'group', 'unknown'}
NOISE_WORDS = {
    'first', 'second', 'third', 'one', 'two', 'three', 'four', 'five',
    'many', 'several', 'some', 'most', 'other', 'another', 'each',
    'both', 'all', 'no', 'new', 'old', 'great', 'little', 'own',
}
NATIONALITY_SUFFIXES = {'an', 'ese', 'ish', 'ian', 'ic'}


def is_noise(entity_key: str, nlp_ent: dict) -> tuple[bool, float, str]:
    """Classify an NLP entity as noise with confidence and reason."""
    etype = nlp_ent.get('type', 'unknown')
    name = nlp_ent.get('canonical', '')

    if etype == 'unknown' and len(name) < 4:
        return True, 0.95, 'short unknown token'
    if entity_key in NOISE_WORDS:
        return True, 0.95, 'common word'
    if etype == 'group':
        return True, 0.85, 'nationality/group (NORP)'
    if etype == 'unknown':
        return True, 0.7, 'unclassified NLP entity'
    return False, 0.0, ''


# --- Fuzzy matching ---

def fuzzy_match(nlp_name: str, llm_names: set[str]) -> tuple[bool, str]:
    """Check if NLP entity name fuzzy-matches any LLM entity."""
    nlp_lower = nlp_name.lower()

    for llm in llm_names:
        if nlp_lower == llm:
            return True, llm
        shorter, longer = (nlp_lower, llm) if len(nlp_lower) <= len(llm) else (llm, nlp_lower)
        if len(shorter) >= 4 and shorter in longer and len(shorter) / len(longer) > 0.5:
            if longer.startswith(shorter + ' ') or longer.endswith(' ' + shorter) or (' ' + shorter + ' ') in longer:
                return True, llm
        if jaro_winkler(nlp_lower, llm) > 0.85:
            return True, llm
    return False, ''


def jaro_winkler(s1: str, s2: str) -> float:
    """Jaro-Winkler similarity between two strings."""
    if s1 == s2:
        return 1.0
    len1, len2 = len(s1), len(s2)
    if len1 == 0 or len2 == 0:
        return 0.0

    window = max(len1, len2) // 2 - 1
    if window < 0:
        window = 0

    s1_matches = [False] * len1
    s2_matches = [False] * len2
    matches = 0
    transpositions = 0

    for i in range(len1):
        start = max(0, i - window)
        end = min(i + window + 1, len2)
        for j in range(start, end):
            if s2_matches[j] or s1[i] != s2[j]:
                continue
            s1_matches[i] = True
            s2_matches[j] = True
            matches += 1
            break

    if matches == 0:
        return 0.0

    k = 0
    for i in range(len1):
        if not s1_matches[i]:
            continue
        while not s2_matches[k]:
            k += 1
        if s1[i] != s2[k]:
            transpositions += 1
        k += 1

    jaro = (matches / len1 + matches / len2 + (matches - transpositions / 2) / matches) / 3

    prefix = 0
    for i in range(min(4, len1, len2)):
        if s1[i] == s2[i]:
            prefix += 1
        else:
            break

    return jaro + prefix * 0.1 * (1 - jaro)


# --- Coverage report with gap classification ---

def build_coverage_report(entities: list[dict], nlp_baseline: dict) -> dict:
    """Diff LLM entities against NLP baseline with gap classification."""
    llm_names = {e['name'].lower() for e in entities}
    nlp_entities = nlp_baseline.get('entities', {})

    covered = []
    fuzzy_matched = []
    noise_dismissed = []
    real_gaps = []

    for key, nlp_ent in nlp_entities.items():
        if nlp_ent['type'] == 'date':
            continue

        entry = {
            'entity': nlp_ent['canonical'],
            'nlp_type': nlp_ent['type'],
            'mentions': nlp_ent['mentions'],
            'tier': nlp_ent.get('depth_tier', 'unknown'),
        }

        if key in llm_names or nlp_ent['canonical'].lower() in llm_names:
            entry['status'] = 'covered'
            entry['noise_confidence'] = 0.0
            covered.append(entry)
            continue

        is_n, noise_conf, reason = is_noise(key, nlp_ent)
        if is_n:
            entry['status'] = 'dismissed-noise'
            entry['noise_confidence'] = noise_conf
            entry['dismiss_reason'] = reason
            noise_dismissed.append(entry)
            continue

        matched, match_target = fuzzy_match(nlp_ent['canonical'], llm_names)
        if matched:
            entry['status'] = 'fuzzy-matched'
            entry['noise_confidence'] = 0.8
            entry['matched_to'] = match_target
            fuzzy_matched.append(entry)
            continue

        entry['status'] = 'real-gap'
        entry['noise_confidence'] = 0.1 if nlp_ent['mentions'] >= 3 else 0.3
        real_gaps.append(entry)

    total = len(covered) + len(fuzzy_matched) + len(noise_dismissed) + len(real_gaps)
    effective_covered = len(covered) + len(fuzzy_matched)
    effective_total = effective_covered + len(real_gaps)
    coverage_pct = round(effective_covered / max(effective_total, 1) * 100, 1)

    return {
        'overall_coverage_pct': coverage_pct,
        'covered': len(covered),
        'fuzzy_matched': len(fuzzy_matched),
        'noise_dismissed': len(noise_dismissed),
        'real_gaps': len(real_gaps),
        'total_nlp_entities': total,
        'classification': {
            'covered': covered,
            'fuzzy_matched': sorted(fuzzy_matched, key=lambda x: x['mentions'], reverse=True),
            'noise_dismissed': sorted(noise_dismissed, key=lambda x: x['mentions'], reverse=True),
            'real_gaps': sorted(real_gaps, key=lambda x: x['mentions'], reverse=True),
        },
    }


# --- Cognitive model coverage audit ---

ALL_OCC_EMOTIONS = {
    'joy', 'distress', 'hope', 'fear', 'pride', 'shame', 'admiration', 'reproach',
    'love', 'hate', 'happy-for', 'sorry-for', 'resentment', 'gloating',
    'gratitude', 'anger', 'gratification', 'remorse', 'interest', 'disgust',
    'surprise', 'relief',
}

ALL_ACTION_TENDENCIES = {
    'approach', 'avoidance', 'attendance', 'rejection', 'nonattendance',
    'antagonism', 'interruption', 'dominance', 'submission',
}

ALL_DRIVE_AXES = {'curiosity', 'competence', 'affiliation', 'autonomy'}

ALL_SEC_DIMS = {
    'relevance', 'novelty', 'urgency', 'conduciveness',
    'controllability', 'adjustability', 'internal_standards', 'external_standards',
}

ALL_SUB_THOUGHT_TYPES = {
    'affect-observation', 'causal-inference', 'evaluative', 'intention',
    'self-reflection', 'association', 'concern',
}

ALL_DISTORTION_TYPES = {
    'all_or_nothing', 'catastrophizing', 'overgeneralization', 'mental_filter',
    'disqualifying_positive', 'mind_reading', 'fortune_telling', 'magnification',
    'emotional_reasoning', 'should_statements', 'labelling', 'personalization',
}


def flatten_tendencies(at):
    """Flatten structured action tendencies dict into a list of items."""
    if isinstance(at, list):
        return at
    if isinstance(at, dict):
        result = []
        dominant = at.get('dominant')
        if isinstance(dominant, dict):
            result.append(dominant)
        for sec in at.get('secondary', []):
            if isinstance(sec, dict):
                result.append(sec)
        return result
    return []


def audit_model_coverage(memories: list[dict]) -> dict:
    """Check which cognitive model dimensions are actually exercised."""
    occ_found = Counter()
    tendencies_found = Counter()
    drives_found = Counter()
    sec_found = Counter()
    sub_thoughts_found = Counter()
    distortions_found = Counter()
    pad_count = 0
    enriched_count = 0

    for mem in memories:
        cog = mem.get('cognitive', {})
        if not cog:
            continue
        enriched_count += 1

        pad = cog.get('pad', {})
        if isinstance(pad, dict) and pad.get('pleasure') is not None:
            pad_count += 1

        for emo in cog.get('occ_emotions', []):
            name = emo.get('type', emo.get('emotion', '')) if isinstance(emo, dict) else str(emo)
            occ_found[name.lower()] += 1

        at = cog.get('action_tendencies', [])
        for t in flatten_tendencies(at):
            name = t if isinstance(t, str) else t.get('type', '')
            if name:
                tendencies_found[name.lower()] += 1

        drives = cog.get('drive_impact', {})
        if isinstance(drives, dict):
            for axis in ALL_DRIVE_AXES:
                if drives.get(axis) is not None:
                    drives_found[axis] += 1

        sec = cog.get('sec', {})
        if isinstance(sec, dict):
            for dim in ALL_SEC_DIMS:
                if sec.get(dim) is not None:
                    sec_found[dim] += 1

        for st in cog.get('sub_thoughts', []):
            name = st.get('type', st) if isinstance(st, dict) else str(st)
            sub_thoughts_found[name.lower()] += 1

        for d in cog.get('cognitive_distortions', []):
            if isinstance(d, dict):
                name = d.get('type', '')
            elif isinstance(d, str):
                name = d
            else:
                continue
            if name:
                distortions_found[name.lower()] += 1

    def coverage_table(all_items, found_counter):
        present = {k for k in found_counter if k in all_items}
        missing = all_items - present
        return {
            'total': len(all_items),
            'present': len(present),
            'missing': sorted(missing),
            'coverage_pct': round(len(present) / max(len(all_items), 1) * 100, 1),
            'counts': {k: found_counter[k] for k in sorted(present)},
        }

    return {
        'enriched_memories': enriched_count,
        'pad_coverage': pad_count,
        'occ_emotions': coverage_table(ALL_OCC_EMOTIONS, occ_found),
        'action_tendencies': coverage_table(ALL_ACTION_TENDENCIES, tendencies_found),
        'drive_axes': coverage_table(ALL_DRIVE_AXES, drives_found),
        'sec_dimensions': coverage_table(ALL_SEC_DIMS, sec_found),
        'sub_thought_types': coverage_table(ALL_SUB_THOUGHT_TYPES, sub_thoughts_found),
        'cognitive_distortions': coverage_table(ALL_DISTORTION_TYPES, distortions_found),
    }


# --- Corpus assembly ---

OCC_CANONICAL = frozenset({
    'joy', 'distress', 'hope', 'fear', 'pride', 'shame', 'admiration', 'reproach',
    'love', 'hate', 'happy-for', 'sorry-for', 'resentment', 'gloating', 'gratitude',
    'anger', 'gratification', 'remorse', 'interest', 'disgust', 'surprise', 'relief',
})

OCC_NORMALISATION = {
    'despair': 'distress', 'sadness': 'distress', 'grief': 'distress',
    'disappointment': 'distress', 'melancholy': 'distress', 'loss': 'distress',
    'helplessness': 'distress', 'longing': 'distress', 'homesickness': 'distress',
    'frustration': 'anger', 'indignation': 'anger',
    'anxiety': 'fear', 'abandonment_anxiety': 'fear',
    'betrayal': 'reproach', 'betrayal_analog_reproach': 'reproach',
    'contempt': 'reproach',
    'regret': 'remorse', 'guilt': 'remorse',
    'sympathy': 'sorry-for',
    'satisfaction': 'gratification', 'resolve': 'gratification',
    'jealousy': 'resentment',
    'awe': 'admiration', 'inspiration': 'admiration',
    'passion': 'love',
    'yearning': 'hope',
    'courage': 'hope', 'determination': 'hope', 'defiance': 'pride',
    'creative_defiance': 'pride',
    'betrayal_analog_hate': 'hate',
}


def normalise_occ_emotions(emotions: list) -> list:
    """Map non-canonical OCC emotion types to nearest canonical type."""
    if not emotions:
        return emotions
    result = []
    for e in emotions:
        if not isinstance(e, dict):
            continue
        t = e.get('type', '')
        if t in OCC_CANONICAL:
            result.append(e)
        elif t in OCC_NORMALISATION:
            normalised = dict(e)
            normalised['type'] = OCC_NORMALISATION[t]
            normalised['original_type'] = t
            result.append(normalised)
    return result


_CONTENT_DOMAIN_NORMALISATION = {
    'career-development': 'financial-and-occupational',
    'professional-growth': 'financial-and-occupational',
    'work-success': 'financial-and-occupational',
    'achievement-and-competence': 'mastery-and-competence',
    'skill-building': 'mastery-and-competence',
    'self-improvement': 'self-actualisation',
    'personal-growth': 'self-actualisation',
    'self-development': 'self-actualisation',
    'relationships': 'social-relating',
    'social-connection': 'social-relating',
    'belonging': 'social-relating',
    'safety': 'avoidance',
    'security': 'avoidance',
    'harm-avoidance': 'avoidance',
    'faith': 'religion-and-spirituality',
    'spirituality': 'religion-and-spirituality',
    'ethics': 'morality-and-virtue',
    'values': 'morality-and-virtue',
    'doing-good': 'morality-and-virtue',
}

_CONTENT_DOMAIN_CANONICAL = {
    'morality-and-virtue', 'religion-and-spirituality', 'self-actualisation',
    'avoidance', 'social-relating', 'health', 'family',
    'mastery-and-competence', 'financial-and-occupational',
}

_MOTIVATION_ORIGIN_NORMALISATION = {
    'self-driven': 'intrinsic',
    'autonomous': 'intrinsic',
    'self-motivated': 'intrinsic',
    'personally-valued': 'identified',
    'self-endorsed': 'identified',
    'guilt-driven': 'introjected',
    'shame-based': 'introjected',
    'obligatory': 'introjected',
    'socially-imposed': 'external',
    'reward-driven': 'external',
    'compliance': 'external',
}

_MOTIVATION_ORIGIN_CANONICAL = {'intrinsic', 'identified', 'introjected', 'external'}

_CONTENT_SUBDOMAIN_CANONICAL = {
    'social-values', 'personal-morals', 'help-others', 'highly-regarded',
    'religion-and-spirituality',
    'wisdom-and-serenity', 'self-knowledge-and-happiness', 'appreciating-beauty',
    'exploration-and-passions', 'enjoy-life',
    'avoid-stress-harm-rejection-conflict', 'avoid-hassles',
    'relate-and-belong', 'intimacy', 'stability', 'power', 'leadership',
    'health', 'family',
    'achievement', 'self-efficacy', 'practical-competence', 'intellectual-competence',
    'finances', 'occupational-success',
}


def normalise_goal_vocabulary(nodes: list[dict]) -> list[dict]:
    """Normalise content_domain, content_subdomain, and motivation_origin on goal nodes."""
    for node in nodes:
        if node.get('subgraph_type') != 'goal':
            continue
        cd = node.get('content_domain')
        if cd:
            if cd in _CONTENT_DOMAIN_CANONICAL:
                pass
            elif cd in _CONTENT_DOMAIN_NORMALISATION:
                node['original_content_domain'] = cd
                node['content_domain'] = _CONTENT_DOMAIN_NORMALISATION[cd]
            else:
                node['original_content_domain'] = cd
                node['content_domain'] = None

        cs = node.get('content_subdomain')
        if cs and cs not in _CONTENT_SUBDOMAIN_CANONICAL:
            node['original_content_subdomain'] = cs
            node['content_subdomain'] = None

        mo = node.get('motivation_origin')
        if mo:
            if mo in _MOTIVATION_ORIGIN_CANONICAL:
                pass
            elif mo in _MOTIVATION_ORIGIN_NORMALISATION:
                node['original_motivation_origin'] = mo
                node['motivation_origin'] = _MOTIVATION_ORIGIN_NORMALISATION[mo]
            else:
                node['original_motivation_origin'] = mo
                node['motivation_origin'] = None
    return nodes


_LIFECYCLE_STANDARD_LABELS = {
    'person': {'born', 'died'},
    'project': {'conceived', 'started', 'completed', 'first-exhibited',
                'published', 'sold', 'destroyed', 'lost'},
    'place': {'founded', 'built', 'renamed', 'demolished', 'opened-as-museum'},
    'organisation': {'founded', 'merged', 'dissolved'},
    'activity': {'occurred', 'started', 'ended'},
    'concept': {'coined', 'popularised'},
}

_LIFECYCLE_NORMALISATION = {
    'birth': 'born', 'date-of-birth': 'born', 'dob': 'born',
    'death': 'died', 'date-of-death': 'died', 'dod': 'died', 'passed': 'died',
    'created': 'conceived', 'inception': 'conceived', 'began': 'started',
    'finished': 'completed', 'done': 'completed', 'ended': 'completed',
    'exhibited': 'first-exhibited', 'shown': 'first-exhibited', 'displayed': 'first-exhibited',
    'printed': 'published', 'released': 'published',
    'built-in': 'built', 'constructed': 'built', 'erected': 'built',
    'torn-down': 'demolished', 'razed': 'demolished',
    'established': 'founded', 'incorporated': 'founded',
    'disbanded': 'dissolved', 'closed': 'dissolved',
    'happened': 'occurred', 'took-place': 'occurred',
    'introduced': 'coined', 'invented': 'coined',
}

_ALL_STANDARD_LABELS = set()
for _labels in _LIFECYCLE_STANDARD_LABELS.values():
    _ALL_STANDARD_LABELS.update(_labels)


def normalise_lifecycle_labels(nodes: list[dict]) -> list[dict]:
    """Normalise ad-hoc lifecycle event labels to standard set."""
    for node in nodes:
        props = node.get('properties', {})
        lifecycle_keys = [k for k in props if k.startswith('lifecycle.')]
        for key in lifecycle_keys:
            label = key[len('lifecycle.'):]
            normalised = _LIFECYCLE_NORMALISATION.get(label)
            if normalised and normalised != label:
                new_key = f'lifecycle.{normalised}'
                if new_key not in props:
                    props[new_key] = props.pop(key)
                    props[f'lifecycle.original.{normalised}'] = label
                else:
                    props.pop(key)
            elif label not in _ALL_STANDARD_LABELS and label not in _LIFECYCLE_NORMALISATION:
                props[f'lifecycle.nonstandard.{label}'] = props.pop(key)
    return nodes


def _word_set(name: str) -> set[str]:
    """Extract content words from a goal name, ignoring stop words."""
    stops = {'a', 'an', 'the', 'and', 'or', 'in', 'of', 'to', 'for', 'from',
             'with', 'as', 'by', 'on', 'at', 'into', 'through', 'during',
             'despite', 'within', 'between', 'after', 'before', 'own', 'my'}
    return {w for w in name.lower().split() if w not in stops and len(w) > 2}


def _jaccard(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def dedup_entities(nodes: list[dict], jw_threshold: float = 0.92,
                   jaccard_threshold: float = 0.5) -> tuple[list[dict], dict]:
    """Merge duplicate entities across all types via name similarity.

    Handles short-name/long-name pairs like "Frida" / "Frida Kahlo" by
    treating a shorter name as a potential prefix/substring of the longer one.
    Only merges within the same subgraph_type. Goals are handled by dedup_goals.
    """
    non_goals = [(i, n) for i, n in enumerate(nodes) if n.get('subgraph_type') != 'goal']
    if not non_goals:
        return nodes, {'merged': 0, 'clusters': [], 'before': 0, 'after': 0}

    merged_into = {}
    clusters = []

    for ai, (idx_a, a) in enumerate(non_goals):
        if idx_a in merged_into:
            continue
        cluster = [a['name']]
        type_a = a.get('subgraph_type', '')
        name_a = a['name'].lower()

        for bi in range(ai + 1, len(non_goals)):
            idx_b, b = non_goals[bi]
            if idx_b in merged_into:
                continue
            type_b = b.get('subgraph_type', '')
            if type_a != type_b:
                continue
            name_b = b['name'].lower()

            jw = jaro_winkler(name_a, name_b)
            words_a = _word_set(a['name'])
            words_b = _word_set(b['name'])
            jac = _jaccard(words_a, words_b)

            is_substring = name_a in name_b or name_b in name_a
            is_match = (jw >= jw_threshold and jac >= jaccard_threshold) or (is_substring and jw >= 0.85)

            if is_match:
                if len(a['name']) >= len(b['name']):
                    canonical, duplicate, dup_idx = a, b, idx_b
                else:
                    canonical, duplicate, dup_idx = b, a, idx_a
                    merged_into[idx_a] = idx_b
                    idx_a = idx_b
                    a = b

                cluster.append(duplicate['name'])
                merged_into[dup_idx] = idx_a
                canonical.setdefault('merged_from', []).append(duplicate['name'])
                for pid in duplicate.get('source_passages', []):
                    if pid not in canonical.get('source_passages', []):
                        canonical.setdefault('source_passages', []).append(pid)

        if len(cluster) > 1:
            clusters.append({'canonical': a['name'], 'merged': cluster[1:], 'type': type_a})

    remove_indices = set(merged_into.keys())
    surviving = [n for i, n in enumerate(nodes) if i not in remove_indices]
    report = {
        'merged': len(remove_indices),
        'clusters': clusters,
        'before': len(non_goals),
        'after': len(non_goals) - len(remove_indices),
    }
    return surviving, report


def dedup_goals(nodes: list[dict], jw_threshold: float = 0.90,
                jaccard_threshold: float = 0.5) -> dict:
    """Conservative goal dedup — precision-first, never merge non-duplicates.

    Two guards must BOTH pass:
    1. Jaro-Winkler ≥ 0.90 (string similarity)
    2. Jaccard word overlap ≥ 0.5 (semantic content guard)

    Only merges within the same goal_tier.

    Returns (report, surviving_nodes). Report includes near-misses
    for downstream LLM judgment.
    """
    goals = [(i, n) for i, n in enumerate(nodes) if n.get('subgraph_type') == 'goal']
    if not goals:
        return {'merged': 0, 'clusters': [], 'near_misses': [], 'before': 0, 'after': 0}, nodes

    merged_into = {}
    clusters = []
    near_misses = []

    for ai, (idx_a, a) in enumerate(goals):
        if idx_a in merged_into:
            continue
        cluster = [a['name']]
        tier_a = a.get('goal_tier', '')
        name_a = a['name'].lower()
        words_a = _word_set(a['name'])

        for bi in range(ai + 1, len(goals)):
            idx_b, b = goals[bi]
            if idx_b in merged_into:
                continue
            tier_b = b.get('goal_tier', '')
            name_b = b['name'].lower()
            jw = jaro_winkler(name_a, name_b)
            words_b = _word_set(b['name'])
            jac = _jaccard(words_a, words_b)

            domain_a = a.get('content_domain')
            domain_b = b.get('content_domain')
            domain_compatible = (domain_a is None or domain_b is None
                                 or domain_a == domain_b)
            is_mechanical_match = (jw >= jw_threshold and jac >= jaccard_threshold
                                   and tier_a == tier_b and domain_compatible)

            if is_mechanical_match:
                cluster.append(b['name'])
                merged_into[idx_b] = idx_a
                a.setdefault('merged_from', []).append(b['name'])
                for pid in b.get('source_passages', []):
                    if pid not in a.get('source_passages', []):
                        a.setdefault('source_passages', []).append(pid)
            elif jw >= 0.75 or jac >= 0.6:
                near_misses.append({
                    'a': a['name'], 'b': b['name'],
                    'jw_score': round(jw, 3), 'jaccard': round(jac, 3),
                    'same_tier': tier_a == tier_b,
                    'tier_a': tier_a, 'tier_b': tier_b,
                })

        if len(cluster) > 1:
            clusters.append({'canonical': a['name'], 'merged': cluster[1:],
                             'tier': tier_a})

    remove_indices = set(merged_into.keys())
    surviving = [n for i, n in enumerate(nodes) if i not in remove_indices]

    report = {
        'before': len(goals),
        'after': len(goals) - len(remove_indices),
        'merged': len(remove_indices),
        'clusters': clusters,
        'near_misses': sorted(near_misses, key=lambda x: -x['jw_score'])[:20],
        'needs_llm': len(near_misses),
    }
    return report, surviving


def derive_entity_pad(nodes: list[dict], memories: list[dict]) -> None:
    """Derive PAD for entities without it from memory PAD averages.

    For each node missing PAD, collect PAD values from memories that mention
    the entity (as subject or in related_entities), and average them.
    """
    entity_pads: dict[str, list[dict]] = {}
    for mem in memories:
        pad = mem.get('pad')
        if not isinstance(pad, dict) or pad.get('pleasure') is None:
            continue
        subject = mem.get('subject', '')
        if subject:
            entity_pads.setdefault(subject.lower(), []).append(pad)
        for rel in mem.get('related_entities', []):
            if rel:
                entity_pads.setdefault(rel.lower(), []).append(pad)

    for node in nodes:
        if node.get('pad'):
            continue
        key = node['name'].lower()
        pads = entity_pads.get(key, [])
        if not pads:
            continue
        avg_pad = {}
        for dim in ('pleasure', 'arousal', 'dominance'):
            vals = [p[dim] for p in pads if isinstance(p.get(dim), (int, float))]
            if vals:
                avg_pad[dim] = round(sum(vals) / len(vals), 2)
        if len(avg_pad) == 3:
            node['pad'] = avg_pad


def resolve_edge_endpoints(nodes: list[dict], edges: list[dict],
                           alias_file: str = None) -> dict:
    """Resolve dangling edge endpoints to canonical node names.

    Resolution strategies applied in order:
    1. Explicit aliases — from aliases.yaml file (per-biography overrides)
    2. Dedup aliases — names recorded in node['merged_from'] during dedup
    3. Parenthetical cleanup — strip '(goal, tactical, health)' suffixes
    4. Case-insensitive match
    5. First-name matching — 'Frida' → 'Frida Kahlo' (single-word name
       matching first word of a two-word entity name, excludes possessives
       and compound names)

    Returns a resolution report dict. Modifies edges in place.
    """
    import re

    node_names = {n['name'] for n in nodes}
    node_names_lower = {n['name'].lower(): n['name'] for n in nodes}
    node_type = {n['name']: n.get('subgraph_type', '') for n in nodes}

    alias_map = {}
    alias_source = {}

    if alias_file and os.path.exists(alias_file):
        with open(alias_file) as f:
            explicit = yaml.safe_load(f) or {}
        for alias, canonical in explicit.items():
            if canonical in node_names:
                alias_map[alias] = canonical
                alias_source[alias] = 'explicit-alias'

    for n in nodes:
        for old_name in n.get('merged_from', []):
            if old_name not in alias_map:
                alias_map[old_name] = n['name']
                alias_source[old_name] = 'dedup-alias'

    for n in nodes:
        name_lower = n['name'].lower()
        if name_lower != n['name'] and name_lower not in alias_map:
            alias_map[name_lower] = n['name']
            alias_source[name_lower] = 'case-insensitive'

    paren_re = re.compile(r'\s*\([^)]*\)\s*$')
    first_name_re = re.compile(r'^(\S+)\s+[A-ZÁÉÍÓÚÑ]')

    entity_nodes = {n['name'] for n in nodes if node_type.get(n['name'], '') != 'goal'}

    first_name_index = {}
    for n_name in entity_nodes:
        m = first_name_re.match(n_name)
        if m:
            first = m.group(1)
            if not n_name[len(first):].lstrip().startswith("'") and ' and ' not in n_name:
                first_name_index.setdefault(first, []).append(n_name)

    def _resolve(name: str) -> tuple[str, str]:
        if name in node_names:
            return name, 'exact'
        if name in alias_map:
            return alias_map[name], alias_source.get(name, 'alias')
        stripped = paren_re.sub('', name).strip()
        if stripped != name:
            if stripped in node_names:
                alias_map[name] = stripped
                alias_source[name] = 'paren-strip'
                return stripped, 'paren-strip'
            if stripped in alias_map:
                resolved = alias_map[stripped]
                alias_map[name] = resolved
                alias_source[name] = 'paren-strip+alias'
                return resolved, 'paren-strip+alias'
        lower = name.lower()
        if lower in node_names_lower:
            resolved = node_names_lower[lower]
            alias_map[name] = resolved
            alias_source[name] = 'case-insensitive'
            return resolved, 'case-insensitive'
        if ' ' not in name and name in first_name_index:
            candidates = first_name_index[name]
            if len(candidates) == 1:
                alias_map[name] = candidates[0]
                alias_source[name] = 'first-name'
                return candidates[0], 'first-name'
        return name, 'unresolved'

    resolved_count = 0
    unresolved = Counter()
    resolution_methods = Counter()

    for edge in edges:
        for field in ('source', 'target'):
            original = edge[field]
            if original not in node_names:
                resolved, method = _resolve(original)
                if method != 'unresolved':
                    edge[field] = resolved
                    resolved_count += 1
                    resolution_methods[method] += 1
                else:
                    unresolved[original] += 1

    report = {
        'resolved': resolved_count,
        'unresolved_unique': len(unresolved),
        'unresolved_total': sum(unresolved.values()),
        'resolution_methods': dict(resolution_methods),
        'unresolved_names': [{'name': n, 'count': c} for n, c in unresolved.most_common(20)],
        'alias_map_size': len(alias_map),
    }
    return report


def correct_edge_errors(edges: list[dict], nodes: list[dict],
                        corrections_file: str = None) -> tuple[list[dict], dict]:
    """Fix structural edge errors: inversions, invalid types, duplicates.

    Also applies per-biography factual corrections from edge-corrections.yaml.
    """
    node_types = {}
    for n in nodes:
        node_types[n['name']] = n.get('subgraph_type', '')

    corrections = []
    removals = set()

    factual_rules = []
    if corrections_file and os.path.exists(corrections_file):
        with open(corrections_file) as f:
            factual_rules = yaml.safe_load(f) or []

    for i, e in enumerate(edges):
        src, tgt, typ = e.get('source', ''), e.get('target', ''), e.get('type', '')
        for rule in factual_rules:
            match = rule.get('match', {})
            if match.get('type') and match['type'] != typ:
                continue
            if match.get('source') and match['source'] != src:
                continue
            if match.get('target') and match['target'] != tgt:
                continue
            if match.get('target_contains') and match['target_contains'] not in tgt:
                continue
            action = rule.get('action')
            if action == 'retype':
                e['type'] = rule['new_type']
                corrections.append({'index': i, 'fix': f'factual retype to {rule["new_type"]}',
                                    'original': f'{typ}: {src} → {tgt}', 'reason': rule.get('reason', '')})
            elif action == 'remove':
                removals.add(i)
                corrections.append({'index': i, 'fix': 'factual removal',
                                    'original': f'{typ}: {src} → {tgt}', 'reason': rule.get('reason', '')})
            break

    for i, e in enumerate(edges):
        src, tgt, typ = e.get('source', ''), e.get('target', ''), e.get('type', '')

        if typ == 'parent-of':
            src_type = node_types.get(src, '')
            tgt_type = node_types.get(tgt, '')
            if src_type == 'person' and tgt_type == 'person':
                src_lower = src.lower()
                tgt_lower = tgt.lower()
                if ('mother' in tgt_lower or 'father' in tgt_lower or 'parent' in tgt_lower):
                    e['source'], e['target'] = tgt, src
                    e['type'] = 'parent-of'
                    corrections.append({'index': i, 'fix': 'inverted parent-of', 'original': f'{src} → {tgt}'})

        if typ in ('married-to', 'affair-with', 'sibling-of'):
            pair = tuple(sorted([src, tgt]))
            seen_key = (typ, pair)
            for j in range(i):
                ej = edges[j]
                if j in removals:
                    continue
                if ej.get('type') == typ and tuple(sorted([ej.get('source', ''), ej.get('target', '')])) == pair:
                    removals.add(i)
                    corrections.append({'index': i, 'fix': f'duplicate {typ}', 'original': f'{src} → {tgt}'})
                    break

        if typ == 'UNVALIDATED':
            e['type'] = 'associated-with'
            e['validation_tier'] = 'UNVALIDATED'
            corrections.append({'index': i, 'fix': 'UNVALIDATED type → associated-with', 'original': f'{src} → {tgt}'})

    result = [e for i, e in enumerate(edges) if i not in removals]
    report = {
        'corrections': len(corrections),
        'removals': len(removals),
        'details': corrections,
    }
    return result, report


ENRICHMENT_EXPECTATIONS = {
    'experience': {
        'required': ['pad', 'occ_emotions', 'sec_appraisal', 'action_tendencies',
                     'sub_thoughts', 'drive_impact', 'confidence', 'habituation',
                     'caps_inputs', 'goal_impacts'],
        'recommended': ['cognitive_distortions', 'gut_feeling', 'relationship'],
    },
    'reflection': {
        'required': ['pad', 'occ_emotions', 'sec_appraisal', 'drive_impact', 'confidence'],
        'recommended': ['action_tendencies', 'sub_thoughts', 'cognitive_distortions',
                        'goal_impacts', 'gut_feeling'],
    },
    'mood': {
        'required': ['pad'],
        'recommended': ['occ_emotions', 'drive_impact', 'confidence'],
    },
    'relationship': {
        'required': ['pad', 'occ_emotions', 'drive_impact', 'confidence'],
        'recommended': ['sec_appraisal', 'action_tendencies', 'relationship'],
    },
}


def audit_enrichment_completeness(memories: list[dict]) -> dict:
    """Check enrichment coverage per domain against expected standards."""
    by_domain = defaultdict(list)
    for m in memories:
        by_domain[m.get('domain', 'unknown')].append(m)

    report = {}
    warnings = []
    for domain, mems in sorted(by_domain.items()):
        expectations = ENRICHMENT_EXPECTATIONS.get(domain, {})
        required = expectations.get('required', [])
        recommended = expectations.get('recommended', [])

        domain_report = {'count': len(mems), 'required': {}, 'recommended': {}}
        for field in required:
            present = sum(1 for m in mems if field in m and m[field])
            pct = round(present / len(mems) * 100, 1) if mems else 0
            domain_report['required'][field] = {'present': present, 'total': len(mems), 'pct': pct}
            if pct < 90:
                warnings.append(f'{domain}.{field}: {present}/{len(mems)} ({pct}%) — required field below 90%')
        for field in recommended:
            present = sum(1 for m in mems if field in m and m[field])
            pct = round(present / len(mems) * 100, 1) if mems else 0
            domain_report['recommended'][field] = {'present': present, 'total': len(mems), 'pct': pct}

        report[domain] = domain_report

    report['warnings'] = warnings
    return report


def audit_pad_distribution(memories: list[dict]) -> dict:
    """Check PAD value distributions for compression or bias."""
    import math

    dims = {'pleasure': [], 'arousal': [], 'dominance': []}
    for m in memories:
        pad = m.get('pad')
        if not pad or not isinstance(pad, dict):
            continue
        for dim in dims:
            v = pad.get(dim)
            if v is not None:
                dims[dim].append(v)

    report = {}
    warnings = []
    for dim, values in dims.items():
        if not values:
            report[dim] = {'count': 0}
            continue
        n = len(values)
        mean = sum(values) / n
        variance = sum((v - mean) ** 2 for v in values) / max(n - 1, 1)
        stdev = math.sqrt(variance)
        report[dim] = {
            'count': n,
            'min': round(min(values), 2),
            'max': round(max(values), 2),
            'mean': round(mean, 3),
            'stdev': round(stdev, 3),
        }
        if stdev < 0.2:
            warnings.append(f'{dim} compressed: stdev={stdev:.3f} (threshold 0.2), mean={mean:.3f}')
    report['warnings'] = warnings
    return report


_TIER_ORDER = {'tactical': 0, 'strategic': 1, 'thematic': 2}


def create_cross_tier_edges(nodes: list[dict]) -> list[dict]:
    """Create contributes-to edges between adjacent goal tiers within same subdomain.

    Scoped to content_subdomain (not domain) and adjacent tiers only
    (tactical→strategic, strategic→thematic). This produces a meaningful
    hierarchy rather than a saturated Cartesian product.
    """
    goals_by_subdomain_tier = {}
    for node in nodes:
        if node.get('subgraph_type') != 'goal':
            continue
        subdomain = node.get('content_subdomain')
        tier = node.get('goal_tier', '')
        if not subdomain or tier not in _TIER_ORDER:
            continue
        goals_by_subdomain_tier.setdefault(subdomain, {}).setdefault(tier, []).append(node)

    edges = []
    adjacent_pairs = [('tactical', 'strategic'), ('strategic', 'thematic')]
    for subdomain, tiers in goals_by_subdomain_tier.items():
        for lower_tier, upper_tier in adjacent_pairs:
            lower_goals = tiers.get(lower_tier, [])
            upper_goals = tiers.get(upper_tier, [])
            for g in lower_goals:
                for other in upper_goals:
                    edges.append({
                        'source': g['name'],
                        'target': other['name'],
                        'type': 'contributes-to',
                        'confidence_origin': 'INFERRED',
                        'confidence_value': 0.6,
                        'validation_tier': 'REGISTERED',
                        'source_passage': 'cross-tier-inference',
                    })
    return edges


LLM_DEDUP_SYSTEM = """You are a knowledge graph deduplication expert. For each pair of goals, classify as:
- DUPLICATE: same goal expressed differently — should be merged
- RELATED: connected goals but distinct — should NOT be merged
- DISTINCT: unrelated goals — should NOT be merged

Consider the full goal description, tier, and domain. Two goals about the same broad topic
at different tiers (tactical vs thematic) are RELATED, not DUPLICATE.

Respond with JSON: {"verdicts": [{"pair_index": 0, "verdict": "DUPLICATE|RELATED|DISTINCT", "reason": "..."}]}"""


def build_dedup_batches(near_misses: list[dict], batch_size: int = 20) -> list[list[dict]]:
    """Split near-miss pairs into batches for LLM classification."""
    if not near_misses:
        return []
    batches = []
    for i in range(0, len(near_misses), batch_size):
        batches.append(near_misses[i:i + batch_size])
    return batches


def union_find_merge(verdicts: list[dict]) -> list[list[str]]:
    """Merge DUPLICATE pairs transitively via union-find."""
    parent = {}

    def find(x):
        while parent.get(x, x) != x:
            parent[x] = parent.get(parent[x], parent[x])
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    for v in verdicts:
        if v.get('verdict') == 'DUPLICATE':
            union(v['a'], v['b'])

    groups_map: dict[str, list[str]] = {}
    all_names = set()
    for v in verdicts:
        if v.get('verdict') == 'DUPLICATE':
            all_names.add(v['a'])
            all_names.add(v['b'])
    for name in all_names:
        root = find(name)
        groups_map.setdefault(root, []).append(name)

    return [sorted(set(members)) for members in groups_map.values() if len(members) > 1]


def apply_llm_dedup_verdicts(nodes: list[dict],
                             merge_groups: list[list[str]]) -> tuple[list[dict], dict]:
    """Apply LLM dedup merge groups to nodes. Keeps longest name as canonical."""
    if not merge_groups:
        return nodes, {'llm_merged': 0, 'llm_clusters': []}

    names_to_remove = set()
    clusters = []
    node_by_name = {n['name']: n for n in nodes if n.get('subgraph_type') == 'goal'}

    for group in merge_groups:
        present = [name for name in group if name in node_by_name]
        if len(present) < 2:
            continue
        canonical_name = max(present, key=len)
        canonical = node_by_name[canonical_name]
        for name in present:
            if name == canonical_name:
                continue
            duplicate = node_by_name[name]
            canonical.setdefault('merged_from', []).append(name)
            for pid in duplicate.get('source_passages', []):
                if pid not in canonical.get('source_passages', []):
                    canonical.setdefault('source_passages', []).append(pid)
            names_to_remove.add(name)
        clusters.append({'canonical': canonical_name, 'merged': [n for n in present if n != canonical_name]})

    surviving = [n for n in nodes if n['name'] not in names_to_remove]
    report = {
        'llm_merged': len(names_to_remove),
        'llm_clusters': clusters,
    }
    return surviving, report


def llm_semantic_dedup(nodes: list[dict], near_misses: list[dict],
                       client=None) -> tuple[list[dict], dict]:
    """Run LLM semantic dedup on near-miss goal pairs.

    Requires an LLM client. If client is None, returns nodes unchanged.
    """
    if not near_misses or client is None:
        return nodes, {'llm_merged': 0, 'llm_clusters': [], 'skipped': client is None}

    from llm_client import model_id
    batches = build_dedup_batches(near_misses)
    all_verdicts = []

    for batch_idx, batch in enumerate(batches):
        pairs_text = '\n'.join(
            f"  [{i}] \"{p['a']}\" vs \"{p['b']}\" (JW={p['jw_score']}, Jaccard={p.get('jaccard', '?')})"
            for i, p in enumerate(batch)
        )
        user_prompt = f"Classify these {len(batch)} goal pairs:\n{pairs_text}"

        try:
            response = client.messages.create(
                model=model_id(),
                max_tokens=2048,
                system=LLM_DEDUP_SYSTEM,
                messages=[{'role': 'user', 'content': user_prompt}],
            )
            text = response.content[0].text
            start = text.find('{')
            end = text.rfind('}') + 1
            if start >= 0 and end > start:
                result = json.loads(text[start:end])
                for v in result.get('verdicts', []):
                    idx = v.get('pair_index', -1)
                    if 0 <= idx < len(batch):
                        all_verdicts.append({
                            'a': batch[idx]['a'],
                            'b': batch[idx]['b'],
                            'verdict': v.get('verdict', 'DISTINCT'),
                            'reason': v.get('reason', ''),
                        })
        except Exception as e:
            print(f"    Warning: LLM dedup batch {batch_idx} failed: {e}", file=sys.stderr)

    merge_groups = union_find_merge(all_verdicts)
    return apply_llm_dedup_verdicts(nodes, merge_groups)


def assemble(enriched: dict, llm_client=None, alias_file: str = None) -> dict:
    """Assemble the final corpus structure with all prescribed fields."""
    nodes = []
    for ent in enriched.get('entities', []):
        node = {
            'name': ent['name'],
            'subgraph_type': ent.get('type', ent.get('subgraph_type', 'concept')),
            'properties': dict(ent.get('properties', {})),
            'source_passages': ent.get('source_passages', []),
        }
        for key in ('traits', 'trait_properties', 'confidence', 'valid_from', 'valid_until',
                     'node_ref', 'pad',
                     'goal_tier', 'goal_horizon', 'goal_status', 'goal_urgency',
                     'goal_need_tier', 'goal_priority', 'goal_feasibility', 'goal_target_date',
                     'content_domain', 'content_subdomain', 'motivation_origin',
                     'clarity', 'evidence_basis'):
            if key in ent:
                node[key] = ent[key]
        for event in ent.get('lifecycle', []):
            lc_key = f"lifecycle.{event['event']}"
            lc_value = event['date']
            if event.get('context'):
                lc_value += f" ({event['context']})"
            node['properties'][lc_key] = lc_value
        if ent.get('is_formative'):
            node['is_formative'] = True
            node['developmental_period'] = ent.get('developmental_period')
        nodes.append(node)

    edges = []
    for edge in enriched.get('edges', []):
        e = {
            'source': edge['source'],
            'target': edge['target'],
            'type': edge['type'],
            'source_passage': edge.get('source_passage'),
        }
        if 'confidence_origin' in edge:
            e['confidence_origin'] = edge['confidence_origin']
        if 'confidence_value' in edge:
            e['confidence_value'] = edge['confidence_value']
        if 'validation_tier' in edge:
            e['validation_tier'] = edge['validation_tier']
        if 'extraction_method' in edge:
            e['extraction_method'] = edge['extraction_method']
        edges.append(e)

    memories = []
    reclassified_count = 0
    for mem in enriched.get('memories', []):
        domain = mem.get('domain', 'experience')
        if domain == 'formative-experience':
            domain = 'experience'
        if mem.get('event_type') == 'formative-experience' and domain != 'experience':
            domain = 'experience'
        if (domain == 'mood' and mem.get('extraction_method') == 'llm-extract'
                and mem.get('event_type') in ('observation', 'outcome', 'action')):
            domain = 'experience'
            reclassified_count += 1
        memory = {
            'text': mem['text'],
            'domain': domain,
            'event_type': mem.get('event_type', 'observation'),
            'subject': mem.get('subject', 'frida-kahlo'),
            'period': mem.get('period'),
            'related_entities': mem.get('related_entities', []),
            'mentioned_entities': mem.get('related_entities', []),
            'source_passage': mem.get('source_passage'),
            'source_start': mem.get('source_start'),
            'source_end': mem.get('source_end'),
            'enrichment_version': mem.get('enrichment_version', 'v1.0'),
        }
        for key in ('extraction_method', 'extraction_pass', 'enrichment_timestamp',
                     'developmental_period', 'situation_types', 'salience_multiplier',
                     'reinforcement_schedule', 'contradicts',
                     'pad_justification', 'drive_justification', 'confidence_justification',
                     'habituation', 'caps_inputs', 'caps_expected_outputs',
                     'goal_impacts', 'relationship', 'gut_feeling', 'temporal_period',
                     'quality_signal', 'other_agent', 'trust_trajectory'):
            if key in mem:
                memory[key] = mem[key]

        cognitive = mem.get('cognitive', {})
        if cognitive:
            memory.setdefault('pad', cognitive.get('pad'))
            memory.setdefault('occ_emotions', cognitive.get('occ_emotions', []))
            memory.setdefault('sec_appraisal', cognitive.get('sec'))
            memory.setdefault('action_tendencies', flatten_tendencies(cognitive.get('action_tendencies', [])))
            memory.setdefault('sub_thoughts', cognitive.get('sub_thoughts', []))
            memory.setdefault('cognitive_distortions', cognitive.get('cognitive_distortions', []))
            memory.setdefault('drive_impact', cognitive.get('drive_impact'))
            memory.setdefault('confidence', cognitive.get('confidence'))
        for key in ('pad', 'occ_emotions', 'sec', 'action_tendencies', 'sub_thoughts',
                     'cognitive_distortions', 'drive_impact', 'confidence'):
            if key in mem and key not in memory:
                memory[key] = mem[key]
        if 'sec' in memory and 'sec_appraisal' not in memory:
            memory['sec_appraisal'] = memory.pop('sec')
        if memory.get('occ_emotions'):
            memory['occ_emotions'] = normalise_occ_emotions(memory['occ_emotions'])
        memories.append(memory)

    for ref in enriched.get('phase2b_reflections', []):
        ref_mem = {
            'text': ref.get('text', ''),
            'domain': 'reflection',
            'event_type': 'reflection',
            'subject': ref.get('subject', ''),
            'enrichment_version': ref.get('enrichment_version', 'v1.0-phase2b'),
            'extraction_method': 'llm-synthesis',
            'extraction_pass': 'phase-2b-entity',
            'reflection_level': ref.get('level', 1),
            'source_memory_indices': ref.get('source_memory_indices', []),
        }
        if ref.get('enrichment_timestamp'):
            ref_mem['enrichment_timestamp'] = ref['enrichment_timestamp']
        cognitive = ref.get('cognitive', {})
        if cognitive:
            for key in ('pad', 'occ_emotions', 'sec', 'action_tendencies', 'sub_thoughts',
                         'cognitive_distortions', 'drive_impact', 'confidence',
                         'pad_justification', 'drive_justification', 'confidence_justification',
                         'habituation', 'caps_inputs', 'goal_impacts', 'relationship', 'gut_feeling'):
                if key in cognitive:
                    ref_mem[key] = cognitive[key]
            if 'sec' in ref_mem and 'sec_appraisal' not in ref_mem:
                ref_mem['sec_appraisal'] = ref_mem.pop('sec')
        memories.append(ref_mem)

    for mood in enriched.get('phase2b_mood_snapshots', []):
        memories.append({
            'text': f"Mood during {mood.get('period', '?')}: {mood.get('context', '')}",
            'domain': 'mood',
            'event_type': 'mood-snapshot',
            'subject': mood.get('subject', ''),
            'period': mood.get('period'),
            'pad': {'pleasure': mood.get('pleasure', 0), 'arousal': mood.get('arousal', 0), 'dominance': mood.get('dominance', 0)},
            'enrichment_version': mood.get('enrichment_version', 'v1.0-phase2b'),
            'extraction_method': 'llm-synthesis',
            'extraction_pass': 'phase-2b-entity',
        })

    derive_entity_pad(nodes, memories)
    nodes = normalise_goal_vocabulary(nodes)
    nodes = normalise_lifecycle_labels(nodes)
    nodes, entity_dedup_report = dedup_entities(nodes)
    dedup_report, nodes = dedup_goals(nodes)

    llm_dedup_report = {'llm_merged': 0, 'llm_clusters': []}
    near_misses = dedup_report.get('near_misses', [])
    if near_misses and llm_client is not None:
        nodes, llm_dedup_report = llm_semantic_dedup(nodes, near_misses, llm_client)

    for node in nodes:
        st = node.get('subgraph_type', 'concept')
        node['subgraph'] = st

    corrections_file_path = None
    if alias_file:
        candidate = os.path.join(os.path.dirname(alias_file), 'edge-corrections.yaml')
        if os.path.exists(candidate):
            corrections_file_path = candidate
    edges, edge_correction_report = correct_edge_errors(edges, nodes, corrections_file=corrections_file_path)

    cross_tier_edges = create_cross_tier_edges(nodes)
    edges.extend(cross_tier_edges)

    edge_resolution_report = resolve_edge_endpoints(nodes, edges, alias_file=alias_file)

    corpus = {
        'format': 'cognitive-workbench-corpus/v2',
        'subject': enriched.get('subject', 'unknown'),
        'stats': {
            'nodes': len(nodes),
            'edges': len(edges),
            'memories': len(memories),
            'enriched_memories': sum(1 for m in memories if m.get('pad')),
            'domains': dict(Counter(m.get('domain', 'experience') for m in memories)),
            'goal_dedup': dedup_report,
            'entity_dedup': entity_dedup_report,
            'llm_dedup': llm_dedup_report,
            'edge_correction': edge_correction_report,
            'edge_resolution': edge_resolution_report,
            'pad_distribution': audit_pad_distribution(memories),
            'enrichment_completeness': audit_enrichment_completeness(memories),
        },
        'nodes': nodes,
        'edges': edges,
        'memories': memories,
    }

    if enriched.get('entity_enrichments'):
        corpus['entity_enrichments'] = enriched['entity_enrichments']
    if enriched.get('disposition_profile'):
        corpus['disposition_profile'] = enriched['disposition_profile']

    return corpus


def main():
    parser = argparse.ArgumentParser(description='Assemble importable corpus')
    parser.add_argument('enriched', help='Path to enriched.json')
    parser.add_argument('baseline', help='Path to nlp-baseline.json')
    parser.add_argument('--output', '-o', default='corpus.json', help='Output corpus file')
    parser.add_argument('--report', '-r', default='coverage-report.json', help='Coverage report')
    parser.add_argument('--model-report', '-m', default='model-coverage.json', help='Model coverage report')
    parser.add_argument('--gate-report', '-g', default='coverage-gate-report.json', help='Coverage gate report')
    parser.add_argument('--llm-dedup', action='store_true', help='Enable LLM semantic dedup of near-miss goals')
    parser.add_argument('--aliases', '-a', default=None, help='Path to aliases.yaml for entity name resolution')
    args = parser.parse_args()

    with open(args.enriched, 'r') as f:
        enriched = json.load(f)
    with open(args.baseline, 'r') as f:
        baseline = json.load(f)

    llm_client = None
    if args.llm_dedup:
        from llm_client import create_client
        llm_client = create_client()

    alias_file = args.aliases
    if not alias_file:
        default_aliases = os.path.join(os.path.dirname(args.enriched), 'aliases.yaml')
        if os.path.exists(default_aliases):
            alias_file = default_aliases
    corpus = assemble(enriched, llm_client=llm_client, alias_file=alias_file)
    coverage = build_coverage_report(enriched.get('entities', []), baseline)
    model_cov = audit_model_coverage(enriched.get('memories', []))

    registry_path = os.path.join(os.path.dirname(__file__), '..', 'docs', 'field-registry.yaml')
    from coverage import load_registry, check_node_coverage, check_memory_coverage, check_enrichment_coverage, format_report
    registry = load_registry(registry_path)
    node_report = check_node_coverage(corpus['nodes'], registry, stage='corpus')
    mem_report = check_memory_coverage(corpus['memories'], registry, stage='corpus')
    enrich_report = check_enrichment_coverage(corpus['memories'], registry)
    reports = [node_report, mem_report, enrich_report]

    gate_report = {
        'format': 'coverage-gate-report/v1',
        'category_breakdown': {},
        'gaps': {},
        'overall': {},
    }
    total_pop = 0
    total_all = 0
    for r in reports:
        total_pop += r.populated
        total_all += r.total
        gate_report['category_breakdown'][r.category] = {
            'populated': r.populated, 'total': r.total,
            'pct': r.pct, 'threshold': r.threshold, 'gate': r.gate,
        }
        gate_report['gaps'][r.category] = r.gaps
    gate_report['overall'] = {
        'populated': total_pop, 'total': total_all,
        'pct': round(total_pop / max(total_all, 1) * 100, 1),
    }

    with open(args.output, 'w') as f:
        json.dump(corpus, f, indent=2)
    with open(args.report, 'w') as f:
        json.dump(coverage, f, indent=2)
    with open(args.model_report, 'w') as f:
        json.dump(model_cov, f, indent=2)
    with open(args.gate_report, 'w') as f:
        json.dump(gate_report, f, indent=2)

    print(f"Corpus assembled:")
    print(f"  Nodes: {corpus['stats']['nodes']}")
    print(f"  Edges: {corpus['stats']['edges']}")
    print(f"  Memories: {corpus['stats']['memories']} ({corpus['stats']['enriched_memories']} enriched)")
    if corpus['stats'].get('domains'):
        print(f"  Domains: {corpus['stats']['domains']}")
    if corpus.get('disposition_profile'):
        print(f"  Disposition profile: derived")
    dd = corpus['stats'].get('goal_dedup', {})
    if dd.get('merged', 0) > 0:
        print(f"\n--- Goal Dedup (JW≥0.92 + Jaccard≥0.5, same tier) ---")
        print(f"  Before: {dd['before']} goals")
        print(f"  Merged: {dd['merged']} (mechanical, high-confidence only)")
        print(f"  After: {dd['after']} goals")
        for c in dd.get('clusters', []):
            print(f"    [{c['tier']}] \"{c['canonical']}\" ← {c['merged']}")
        if dd.get('near_misses'):
            print(f"  Near misses (need LLM judgment): {dd['needs_llm']}")
            for nm in dd['near_misses'][:8]:
                print(f"    JW={nm['jw_score']:.3f} J={nm['jaccard']:.2f} \"{nm['a']}\" vs \"{nm['b']}\" ({nm['tier_a']}/{nm['tier_b']})")
    elif dd:
        print(f"\n--- Goal Dedup ---")
        print(f"  {dd['before']} goals, 0 mechanical duplicates (JW≥0.92 + Jaccard≥0.5)")
        if dd.get('near_misses'):
            print(f"  Near misses (need LLM judgment): {dd['needs_llm']}")
            for nm in dd['near_misses'][:8]:
                print(f"    JW={nm['jw_score']:.3f} J={nm['jaccard']:.2f} \"{nm['a']}\" vs \"{nm['b']}\"")

    ec = corpus['stats'].get('enrichment_completeness', {})
    if ec:
        print(f"\n--- Enrichment Completeness (per domain) ---")
        for domain in sorted(ec.keys()):
            if domain == 'warnings':
                continue
            d = ec[domain]
            req_fields = d.get('required', {})
            rec_fields = d.get('recommended', {})
            req_ok = all(v['pct'] >= 90 for v in req_fields.values()) if req_fields else True
            status = 'PASS' if req_ok else 'GAPS'
            print(f"  {domain} ({d['count']} memories): {status}")
            for field, v in req_fields.items():
                if v['pct'] < 100:
                    print(f"    {field}: {v['present']}/{v['total']} ({v['pct']}%) [required]")
            for field, v in rec_fields.items():
                if v['pct'] < 50:
                    print(f"    {field}: {v['present']}/{v['total']} ({v['pct']}%) [recommended]")
        if ec.get('warnings'):
            for w in ec['warnings']:
                print(f"  ⚠ {w}")

    pad_dist = corpus['stats'].get('pad_distribution', {})
    if pad_dist.get('warnings'):
        print(f"\n--- PAD Distribution Warnings ---")
        for w in pad_dist['warnings']:
            print(f"  ⚠ {w}")
    else:
        print(f"\n--- PAD Distribution ---")
        for dim in ('pleasure', 'arousal', 'dominance'):
            d = pad_dist.get(dim, {})
            if d.get('count'):
                print(f"  {dim}: mean={d['mean']:.3f} stdev={d['stdev']:.3f} [{d['min']}, {d['max']}]")

    er = corpus['stats'].get('edge_resolution', {})
    if er:
        print(f"\n--- Edge Resolution ---")
        print(f"  Resolved: {er['resolved']} references")
        for method, count in er.get('resolution_methods', {}).items():
            print(f"    {method}: {count}")
        print(f"  Unresolved: {er['unresolved_unique']} unique names ({er['unresolved_total']} references)")
        if er.get('unresolved_names'):
            print(f"  Top unresolved:")
            for u in er['unresolved_names'][:10]:
                print(f"    {u['count']:4d}  {u['name']}")

    print(f"\n--- NLP Coverage (with gap classification) ---")
    print(f"  Covered: {coverage['covered']}")
    print(f"  Fuzzy matched: {coverage['fuzzy_matched']}")
    print(f"  Noise dismissed: {coverage['noise_dismissed']}")
    print(f"  Real gaps: {coverage['real_gaps']}")
    print(f"  Effective coverage: {coverage['overall_coverage_pct']}%")
    if coverage['classification']['real_gaps']:
        print(f"  Top real gaps:")
        for g in coverage['classification']['real_gaps'][:5]:
            print(f"    {g['entity']} ({g['nlp_type']}, {g['mentions']} mentions)")

    print(f"\n--- Cognitive Model Coverage ---")
    for dim, data in model_cov.items():
        if isinstance(data, dict) and 'coverage_pct' in data:
            missing = data['missing']
            status = f"{data['present']}/{data['total']} ({data['coverage_pct']}%)"
            print(f"  {dim}: {status}")
            if missing:
                print(f"    Missing: {', '.join(missing)}")

    print(f"\n--- Coverage Gate Report ---")
    print(format_report(reports))

    print(f"\nOutput: {args.output}")
    print(f"Coverage: {args.report}")
    print(f"Model coverage: {args.model_report}")
    print(f"Gate report: {args.gate_report}")


if __name__ == '__main__':
    main()
