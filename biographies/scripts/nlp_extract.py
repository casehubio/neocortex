#!/usr/bin/env python3
"""Step 2: Mechanical NLP extraction — entity concordance + temporal expressions.

Input:  passages.json (from chunk.py)
Output: nlp-baseline.json — entity concordance index, temporal expressions, noun chunks

Uses spaCy for NER, noun chunks, and temporal extraction.
Produces the exhaustive candidate pool that LLM passes are audited against.

Usage:
    python3 nlp_extract.py <passages.json> [--output nlp-baseline.json]
"""

import argparse
import json
import sys
from collections import defaultdict

import spacy


ENTITY_TYPE_MAP = {
    'PERSON': 'person',
    'GPE': 'place',
    'LOC': 'place',
    'FAC': 'place',
    'ORG': 'organisation',
    'DATE': 'date',
    'TIME': 'date',
    'WORK_OF_ART': 'work-of-art',
    'EVENT': 'activity',
    'NORP': 'group',
    'PRODUCT': 'project',
}


def build_concordance(passages: list[dict], nlp) -> dict:
    """Build entity concordance index from NLP analysis of all passages."""
    entities: dict[str, dict] = {}
    temporal_expressions: list[dict] = []
    all_noun_chunks: dict[str, dict] = {}

    for passage in passages:
        doc = nlp(passage['text'])
        pid = passage['id']

        for ent in doc.ents:
            canonical = ent.text.strip()
            if not canonical or len(canonical) < 2:
                continue

            nlp_type = ENTITY_TYPE_MAP.get(ent.label_, 'unknown')

            if ent.label_ in ('DATE', 'TIME'):
                temporal_expressions.append({
                    'text': canonical,
                    'passage_id': pid,
                    'start_char': ent.start_char + passage['start'],
                    'end_char': ent.end_char + passage['start'],
                    'label': ent.label_,
                })
                continue

            key = canonical.lower()
            if key not in entities:
                entities[key] = {
                    'canonical': canonical,
                    'type': nlp_type,
                    'spacy_label': ent.label_,
                    'mentions': 0,
                    'passages': [],
                    'aliases': set(),
                    'confidence': round(1.0 if ent.label_ in ('PERSON', 'GPE', 'DATE') else 0.8, 2),
                    'positions': [],
                }

            entry = entities[key]
            entry['mentions'] += 1
            if pid not in entry['passages']:
                entry['passages'].append(pid)
            entry['aliases'].add(canonical)
            entry['positions'].append({
                'passage_id': pid,
                'start_char': ent.start_char + passage['start'],
                'end_char': ent.end_char + passage['start'],
            })

        for chunk in doc.noun_chunks:
            chunk_text = chunk.text.strip()
            if len(chunk_text) < 3:
                continue
            chunk_key = chunk_text.lower()
            if chunk_key not in all_noun_chunks:
                all_noun_chunks[chunk_key] = {
                    'text': chunk_text,
                    'mentions': 0,
                    'passages': [],
                }
            nc = all_noun_chunks[chunk_key]
            nc['mentions'] += 1
            if pid not in nc['passages']:
                nc['passages'].append(pid)

    for e in entities.values():
        e['aliases'] = sorted(e['aliases'])

    return {
        'entities': entities,
        'temporal_expressions': temporal_expressions,
        'noun_chunks': all_noun_chunks,
    }


def compute_depth_tiers(entities: dict) -> dict:
    """Assign depth tiers based on percentile ranking of prominence score."""
    if not entities:
        return {}

    scored = []
    for key, e in entities.items():
        prominence = e['mentions'] + len(e['passages']) * 2
        scored.append((key, prominence))

    scored.sort(key=lambda x: x[1], reverse=True)
    total = len(scored)

    tiers = {}
    for i, (key, score) in enumerate(scored):
        percentile = i / total
        if percentile < 0.15:
            tier = 'central'
        elif percentile < 0.50:
            tier = 'supporting'
        else:
            tier = 'peripheral'
        tiers[key] = tier
        entities[key]['depth_tier'] = tier
        entities[key]['prominence_score'] = score

    return tiers


def build_gap_template(entities: dict) -> list[dict]:
    """Create a gap report template — to be filled after LLM passes."""
    return [
        {
            'entity': e['canonical'],
            'nlp_type': e['type'],
            'mentions': e['mentions'],
            'depth_tier': e.get('depth_tier', 'unknown'),
            'llm_status': 'pending',
            'mindmap_node_id': None,
            'notes': '',
        }
        for e in sorted(entities.values(), key=lambda x: x['mentions'], reverse=True)
    ]


def main():
    parser = argparse.ArgumentParser(description='NLP entity extraction and concordance')
    parser.add_argument('passages', help='Path to passages.json from chunk.py')
    parser.add_argument('--output', '-o', default='nlp-baseline.json', help='Output JSON file')
    parser.add_argument('--model', '-m', default='en_core_web_sm', help='spaCy model name')
    args = parser.parse_args()

    print(f"Loading spaCy model: {args.model}")
    nlp = spacy.load(args.model)

    with open(args.passages, 'r') as f:
        data = json.load(f)

    passages = data['passages']
    print(f"Processing {len(passages)} passages...")

    result = build_concordance(passages, nlp)
    compute_depth_tiers(result['entities'])
    gap_template = build_gap_template(result['entities'])

    entity_list = sorted(result['entities'].values(), key=lambda x: x['mentions'], reverse=True)
    type_counts = defaultdict(int)
    tier_counts = defaultdict(int)
    for e in entity_list:
        type_counts[e['type']] += 1
        tier_counts[e.get('depth_tier', 'unknown')] += 1

    stats = {
        'total_entities': len(entity_list),
        'total_temporal': len(result['temporal_expressions']),
        'total_noun_chunks': len(result['noun_chunks']),
        'by_type': dict(type_counts),
        'by_tier': dict(tier_counts),
    }

    output = {
        'stats': stats,
        'entities': {k: v for k, v in sorted(result['entities'].items(), key=lambda x: x[1]['mentions'], reverse=True)},
        'temporal_expressions': result['temporal_expressions'],
        'noun_chunks_top50': dict(sorted(result['noun_chunks'].items(), key=lambda x: x[1]['mentions'], reverse=True)[:50]),
        'gap_template': gap_template,
    }

    with open(args.output, 'w') as f:
        json.dump(output, f, indent=2, default=str)

    print(f"\nResults:")
    print(f"  Entities: {stats['total_entities']}")
    for t, c in sorted(type_counts.items()):
        print(f"    {t}: {c}")
    print(f"  Temporal expressions: {stats['total_temporal']}")
    print(f"  Noun chunks: {stats['total_noun_chunks']}")
    print(f"  Depth tiers: {dict(tier_counts)}")
    print(f"\nOutput: {args.output}")


if __name__ == '__main__':
    main()
