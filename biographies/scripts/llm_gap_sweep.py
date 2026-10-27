#!/usr/bin/env python3
"""Step 3: LLM gap sweep — find entities NLP missed.

Input:  passages.json + nlp-baseline.json
Output: gap-sweep.json — new entities to merge into candidate pool

One focused LLM call per passage: "What entities are in this passage
that are NOT in this list?"

Usage:
    python3 llm_gap_sweep.py <passages.json> <nlp-baseline.json> [--output gap-sweep.json]
"""

import argparse
import json
import os
import sys
import time

from llm_client import create_client, model_id

SYSTEM_PROMPT = """You are an entity extraction specialist. You will be given a passage of biographical text and a list of entities already identified by NLP tools.

Your job: find any entities mentioned in the passage that are NOT in the provided list. Focus on:
- People referred to by pronoun, title, or relationship ("her husband", "the doctor")
- Places referred to by description ("the Blue House", "home")
- Works of art, paintings, or creative projects
- Abstract concepts or themes important to the narrative
- Organizations, political parties, movements
- Events not captured as named entities

For each new entity, provide:
- name: the canonical name (resolve "the Blue House" to "Casa Azul" if inferable)
- type: person | place | activity | goal | project | concept | organisation
- confidence: 0.0-1.0 (how certain you are this is a real entity worth tracking)
- passage_text: the exact text in the passage that refers to this entity

Respond with JSON only: {"new_entities": [...]}
If no new entities found: {"new_entities": []}"""


def sweep_passage(client, passage: dict, known_entities: list[str]) -> list[dict]:
    """Ask LLM to find entities NLP missed in one passage."""
    entity_list = ', '.join(known_entities[:50]) if known_entities else '(none found)'

    user_prompt = f"""Passage (ID: {passage['id']}, section: {passage['section']}):

{passage['text']}

---

Entities already identified by NLP in this passage: {entity_list}

What entities are mentioned in this passage that are NOT in the list above?"""

    try:
        response = client.messages.create(
            model=model_id(),
            max_tokens=1000,
            system=SYSTEM_PROMPT,
            messages=[{'role': 'user', 'content': user_prompt}],
        )
        text = response.content[0].text
        start = text.find('{')
        end = text.rfind('}') + 1
        if start >= 0 and end > start:
            result = json.loads(text[start:end])
            return result.get('new_entities', [])
    except Exception as e:
        print(f"  Warning: failed on {passage['id']}: {e}", file=sys.stderr)
    return []


def main():
    parser = argparse.ArgumentParser(description='LLM gap sweep for missed entities')
    parser.add_argument('passages', help='Path to passages.json')
    parser.add_argument('baseline', help='Path to nlp-baseline.json')
    parser.add_argument('--output', '-o', default='gap-sweep.json', help='Output JSON file')
    args = parser.parse_args()

    client = create_client()

    with open(args.passages, 'r') as f:
        passages_data = json.load(f)
    with open(args.baseline, 'r') as f:
        baseline = json.load(f)

    passages = passages_data['passages']
    entities_by_passage = {}
    for key, ent in baseline.get('entities', {}).items():
        for pid in ent.get('passages', []):
            entities_by_passage.setdefault(pid, []).append(ent['canonical'])

    all_new = []
    for i, passage in enumerate(passages):
        known = entities_by_passage.get(passage['id'], [])
        print(f"  [{i+1}/{len(passages)}] {passage['id']}: {passage['section'][:40]}... ({len(known)} known entities)")
        new_entities = sweep_passage(client, passage, known)
        for ne in new_entities:
            ne['source_passage'] = passage['id']
        all_new.extend(new_entities)
        time.sleep(0.2)

    output = {
        'stats': {
            'passages_scanned': len(passages),
            'new_entities_found': len(all_new),
        },
        'new_entities': all_new,
    }

    with open(args.output, 'w') as f:
        json.dump(output, f, indent=2)

    print(f"\nGap sweep complete: {len(all_new)} new entities found across {len(passages)} passages")
    print(f"Output: {args.output}")


if __name__ == '__main__':
    main()
