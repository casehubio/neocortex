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

            is_mechanical_match = (jw >= jw_threshold and jac >= jaccard_threshold
                                   and tier_a == tier_b)

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


def assemble(enriched: dict) -> dict:
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
                     'goal_need_tier', 'goal_priority', 'goal_feasibility', 'goal_target_date'):
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
        if 'validation_tier' in edge:
            e['validation_tier'] = edge['validation_tier']
        edges.append(e)

    memories = []
    for mem in enriched.get('memories', []):
        domain = mem.get('domain', 'experience')
        if domain == 'formative-experience':
            domain = 'experience'
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

    dedup_report, nodes = dedup_goals(nodes)

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
    args = parser.parse_args()

    with open(args.enriched, 'r') as f:
        enriched = json.load(f)
    with open(args.baseline, 'r') as f:
        baseline = json.load(f)

    corpus = assemble(enriched)
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
