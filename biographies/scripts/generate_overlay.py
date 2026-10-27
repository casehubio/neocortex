#!/usr/bin/env python3
"""Step 5b: Generate reference overlay — brief descriptions for all entities.

Input:  structure.json + passages.json
Output: reference-overlay.json — entity descriptions from LLM general knowledge + source context

Separate from the cognitive model. Provides navigational context for the workbench UI.

Usage:
    python3 generate_overlay.py <structure.json> <passages.json> [--output reference-overlay.json]
"""

import argparse
import json
import os
import sys
import time
from datetime import datetime

from llm_client import create_client, model_id
from normalize import clean_entity_name

SYSTEM_PROMPT = """You are a reference knowledge provider. For each entity, provide:

1. description: A one-line general knowledge description (what this entity IS, independent of any biography). For well-known people, places, or concepts, draw on general knowledge. For obscure entities, describe what category they belong to.

2. context: A one-line summary of this entity's specific role in the subject's biography. Why does this entity matter in this person's story?

3. links: External reference links for this entity. Include where relevant:
   - wikipedia: English Wikipedia article title (e.g., "André_Breton")
   - wikidata: Wikidata entity ID (e.g., "Q154371")
   - Any other authoritative external link (museum page, official site, etc.)
   Return as an object: {"wikipedia": "...", "wikidata": "..."} or null if none exist.

Respond with JSON: {"entries": [{"name": "...", "description": "...", "context": "...", "links": {...}}]}

IMPORTANT: Do NOT append type labels like "(person)" or "(place)" to entity names. Return the bare name exactly as given.

Keep descriptions factual and concise — one sentence each, no more."""


def generate_batch(client, entities: list[dict], subject: str) -> list[dict]:
    """Generate overlay entries for a batch of entities."""
    entity_list = '\n'.join(
        f"  - {e['name']} ({e.get('type', '?')})"
        + (f" — mentioned in: {', '.join(e.get('source_passages', [])[:3])}" if e.get('source_passages') else '')
        for e in entities
    )

    user_prompt = f"""Subject biography: {subject}

Entities to describe:
{entity_list}

For each entity, provide a general knowledge description and their role in {subject}'s life."""

    try:
        response = client.messages.create(
            model=model_id(),
            max_tokens=4096,
            system=SYSTEM_PROMPT,
            messages=[{'role': 'user', 'content': user_prompt}],
        )
        text = response.content[0].text
        start = text.find('{')
        end = text.rfind('}') + 1
        if start >= 0 and end > start:
            result = json.loads(text[start:end])
            return result.get('entries', [])
    except Exception as e:
        print(f"  Warning: batch failed: {e}", file=sys.stderr)
    return []


def main():
    parser = argparse.ArgumentParser(description='Generate reference overlay')
    parser.add_argument('structure', help='Path to structure.json')
    parser.add_argument('passages', help='Path to passages.json')
    parser.add_argument('--output', '-o', default='reference-overlay.json', help='Output file')
    parser.add_argument('--subject', '-s', required=True, help='Biography subject name')
    parser.add_argument('--batch-size', '-b', type=int, default=15, help='Entities per LLM call')
    args = parser.parse_args()

    client = create_client()

    with open(args.structure, 'r') as f:
        structure = json.load(f)
    with open(args.passages, 'r') as f:
        passages_data = json.load(f)

    entities = structure.get('entities', [])
    print(f"Generating overlay for {len(entities)} entities...")

    all_entries = {}
    for i in range(0, len(entities), args.batch_size):
        batch = entities[i:i + args.batch_size]
        print(f"  [{i+1}–{min(i+args.batch_size, len(entities))}/{len(entities)}]")
        results = generate_batch(client, batch, args.subject)
        for entry in results:
            name = entry.get('name', '')
            if name:
                clean_name = clean_entity_name(name)
                e = {
                    'description': entry.get('description', ''),
                    'context': entry.get('context', ''),
                }
                links = entry.get('links')
                if links and isinstance(links, dict):
                    e['links'] = links
                all_entries[clean_name] = e
        time.sleep(0.3)

    output = {
        'source': f'LLM general knowledge ({model_id()})',
        'generated': datetime.utcnow().strftime('%Y-%m-%d'),
        'version': 'v1.0',
        'subject': args.subject,
        'total_entries': len(all_entries),
        'entries': all_entries,
    }

    with open(args.output, 'w') as f:
        json.dump(output, f, indent=2)

    print(f"\nOverlay generated: {len(all_entries)} entries")
    print(f"Output: {args.output}")


if __name__ == '__main__':
    main()
