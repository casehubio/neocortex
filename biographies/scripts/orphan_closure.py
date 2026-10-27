#!/usr/bin/env python3
"""Step 7.6: Co-location gap closure — edge repair for under-connected nodes.

Runs after dangling resolution (Step 7.5), before verification (Step 8).
Identifies nodes that share a passage with entities they have no edge to,
and generates targeted LLM edge repair per passage.

Subsumes zero-edge orphan detection: a global orphan is a node with
gaps in ALL its passages.

Input:  engram.json + passages.json
Output: engram.json (modified in place) + orphan-closure-report.json

Usage:
    python3 orphan_closure.py <engram.json> <passages.json> [--schema verify_schema.yaml]
"""

import argparse
import json
import os
import sys
from collections import defaultdict

from pipeline_state import atomic_write


def find_orphans(nodes: list[dict], edges: list[dict]) -> list[dict]:
    """Identify nodes that have zero edges (neither source nor target)."""
    edge_nodes = set()
    for e in edges:
        edge_nodes.add(e['source'])
        edge_nodes.add(e['target'])
    return [n for n in nodes if n['name'] not in edge_nodes]


def find_passage_gaps(nodes: list[dict], edges: list[dict]) -> list[dict]:
    """Find passages with entities that have no edge to any co-locator.

    For each passage, identifies entities that share the passage with
    other entities but have zero direct edges to any of them. Returns
    one entry per passage that has gap entities.
    """
    # Build adjacency: which nodes does each node connect to?
    neighbors = defaultdict(set)
    for e in edges:
        neighbors[e['source']].add(e['target'])
        neighbors[e['target']].add(e['source'])

    # Build passage index
    passage_nodes = defaultdict(list)
    node_map = {}
    for n in nodes:
        node_map[n['name']] = n
        for pid in n.get('source_passages', []):
            passage_nodes[pid].append(n['name'])

    gaps = []
    for pid, names in sorted(passage_nodes.items()):
        if len(names) < 2:
            continue
        name_set = set(names)
        gap_entities = []
        for name in names:
            connected_in_passage = neighbors.get(name, set()) & name_set
            if not connected_in_passage:
                gap_entities.append({
                    'name': name,
                    'subgraph_type': node_map[name].get('subgraph_type', 'concept'),
                })
        if gap_entities:
            gaps.append({
                'passage_id': pid,
                'total_entities': len(names),
                'gap_entities': gap_entities,
            })

    return gaps


def build_passage_context(passage_id: str, gap_entities: list[dict],
                          passages: list[dict], all_nodes: list[dict],
                          all_edges: list[dict]) -> dict:
    """Build context for a passage-level gap closure prompt."""
    passage_map = {p['id']: p['text'] for p in passages}
    passage_text = passage_map.get(passage_id, '')

    passage_names = set()
    for n in all_nodes:
        if passage_id in n.get('source_passages', []):
            passage_names.add(n['name'])

    existing_edges = [
        {'source': e['source'], 'target': e['target'], 'type': e['type']}
        for e in all_edges
        if e['source'] in passage_names and e['target'] in passage_names
    ]

    return {
        'passage_id': passage_id,
        'passage_text': passage_text,
        'all_entities': sorted(passage_names),
        'gap_entity_names': [g['name'] for g in gap_entities],
        'gap_entity_details': gap_entities,
        'existing_edges': existing_edges,
    }


def build_orphan_context(orphan: dict, passages: list[dict],
                         all_nodes: list[dict],
                         all_edges: list[dict]) -> dict:
    """Build context for an orphan's closure prompt.

    Gathers: source passage texts, co-located entities, co-located edges.
    """
    orphan_passages = set(orphan.get('source_passages', []))
    passage_map = {p['id']: p['text'] for p in passages}

    passage_texts = [passage_map[pid] for pid in orphan_passages
                     if pid in passage_map]

    colocated = []
    for n in all_nodes:
        if n['name'] == orphan['name']:
            continue
        if orphan_passages & set(n.get('source_passages', [])):
            colocated.append(n['name'])

    colocated_set = set(colocated) | {orphan['name']}
    colocated_edges = [
        e for e in all_edges
        if e['source'] in colocated_set or e['target'] in colocated_set
    ]

    return {
        'orphan_name': orphan['name'],
        'orphan_type': orphan.get('subgraph_type', 'concept'),
        'orphan_properties': orphan.get('properties', {}),
        'passage_texts': passage_texts,
        'colocated_entities': colocated,
        'colocated_edges': [
            {'source': e['source'], 'target': e['target'], 'type': e['type']}
            for e in colocated_edges
        ],
    }


def parse_closure_response(text: str) -> tuple[list[dict], str | None]:
    """Parse LLM response into edges and optional standalone reason."""
    content = text.strip()
    if '```' in content:
        lines = content.split('\n')
        inside = False
        json_lines = []
        for line in lines:
            if line.strip().startswith('```'):
                inside = not inside
                continue
            if inside:
                json_lines.append(line)
        content = '\n'.join(json_lines)

    if not content:
        content = text

    start = content.find('{')
    end = content.rfind('}') + 1
    if start < 0 or end <= start:
        return [], None

    try:
        result = json.loads(content[start:end])
    except json.JSONDecodeError:
        return [], None

    edges = result.get('edges', [])
    reason = result.get('standalone_reason')
    return edges, reason


def validate_closure_edges(edges: list[dict], valid_types: set[str],
                           node_names: set[str]) -> list[dict]:
    """Filter closure edges: only registered vocabulary types, known endpoints."""
    validated = []
    for e in edges:
        if e.get('type') not in valid_types:
            print(f"  Rejected edge: {e.get('source')} --[{e.get('type')}]--> {e.get('target')} "
                  f"(unknown type)", file=sys.stderr)
            continue
        src, tgt = e.get('source'), e.get('target')
        if src not in node_names or tgt not in node_names:
            unknown = src if src not in node_names else tgt
            print(f"  Rejected edge: {src} --[{e.get('type')}]--> {tgt} "
                  f"(unknown endpoint: {unknown})", file=sys.stderr)
            continue
        validated.append(e)
    return validated


GAP_CLOSURE_SYSTEM = """You are a knowledge graph reviewer. You are reviewing a passage from a biography and the entities and edges extracted from it. Some entities in this passage have NO edges to any other entity in the same passage — they are disconnected from their local context.

Your job: identify missing relationships between entities in this passage. Only add edges where a real relationship exists in the text.

Use ONLY the following edge types:
{vocabulary}

RULES:
- Use ONLY entity names from the provided entity list as source/target
- Use ONLY edge types from the vocabulary above
- Focus on the flagged gap entities, but you may also add edges between other entities if clearly missing
- If a gap entity is genuinely standalone in this passage (no relationship to any other entity here), list it in standalone_entities with a reason

Respond with JSON only:
{{"edges": [{{"source": "...", "target": "...", "type": "...", "confidence_origin": "INFERRED"}}], "standalone_entities": [{{"name": "...", "reason": "..."}}]}}"""


def gap_closure_prompt(context: dict, valid_types: list[str]) -> tuple[str, str]:
    """Build system and user prompts for passage-level gap closure."""
    vocabulary = '\n'.join(f'  - {t}' for t in sorted(valid_types))
    system = GAP_CLOSURE_SYSTEM.format(vocabulary=vocabulary)

    all_entities_str = ', '.join(context['all_entities'])
    gap_str = ', '.join(context['gap_entity_names'])
    edges_str = '\n'.join(
        f"  {e['source']} --[{e['type']}]--> {e['target']}"
        for e in context['existing_edges']
    ) or '  (no existing edges in this passage)'

    user = f"""Passage ({context['passage_id']}):
{context['passage_text']}

All entities in this passage:
  {all_entities_str}

Existing edges between these entities:
{edges_str}

Entities with NO edges to any other entity in this passage (gaps):
  {gap_str}

Add missing edges for the gap entities, or explain why each is standalone in this passage."""

    return system, user


def close_passage_gaps(context: dict, valid_types: list[str],
                       client=None) -> tuple[list[dict], list[dict]]:
    """Call LLM to generate closure edges for a passage's gap entities.

    Returns (edges, standalone_entities).
    """
    if client is None:
        return [], []

    from llm_client import model_id

    system, user = gap_closure_prompt(context, valid_types)

    try:
        response = client.messages.create(
            model=model_id(),
            max_tokens=2048,
            system=system,
            messages=[{'role': 'user', 'content': user}],
        )
        text = response.content[0].text
        edges, _ = parse_closure_response(text)

        # Parse standalone entities from response
        standalone = []
        content = text.strip()
        start = content.find('{')
        end = content.rfind('}') + 1
        if start >= 0 and end > start:
            try:
                result = json.loads(content[start:end])
                standalone = result.get('standalone_entities', [])
            except json.JSONDecodeError:
                pass

        return edges, standalone
    except Exception as e:
        print(f"  LLM call failed for passage {context['passage_id']}: {e}",
              file=sys.stderr)
        return [], []


def run_closure(corpus: dict, passages: list[dict],
                valid_types: set[str],
                client=None) -> dict:
    """Run co-location gap closure on an assembled corpus.

    Identifies passages with gap entities (nodes that have no edge to any
    co-locator in that passage), sends one LLM call per passage, validates
    and merges edges. Modifies corpus in place. Returns a report dict.
    """
    nodes = corpus['nodes']
    edges = corpus['edges']
    node_names = {n['name'] for n in nodes}

    orphans = find_orphans(nodes, edges)
    passage_gaps = find_passage_gaps(nodes, edges)

    total_gap_entities = sum(len(g['gap_entities']) for g in passage_gaps)

    report = {
        'total_orphans': len(orphans),
        'passages_checked': len(passage_gaps),
        'gap_entities_found': total_gap_entities,
        'edges_added': 0,
        'standalone': 0,
        'failed_passages': 0,
        'details': [],
    }

    if not passage_gaps:
        return report

    valid_types_set = set(valid_types)
    valid_types_list = sorted(valid_types)

    for gap in passage_gaps:
        context = build_passage_context(
            gap['passage_id'], gap['gap_entities'],
            passages, nodes, edges)

        raw_edges, standalone_entities = close_passage_gaps(
            context, valid_types_list, client=client)

        detail = {
            'passage_id': gap['passage_id'],
            'total_entities': gap['total_entities'],
            'gap_entities': [g['name'] for g in gap['gap_entities']],
        }

        standalone_names = {s.get('name') for s in standalone_entities} if standalone_entities else set()
        report['standalone'] += len(standalone_names)

        if raw_edges:
            validated = validate_closure_edges(
                raw_edges, valid_types_set, node_names)
            if validated:
                for e in validated:
                    e['extraction_method'] = 'gap-closure'
                    e.setdefault('confidence_origin', 'INFERRED')
                    e.setdefault('validation_tier', 'REGISTERED')
                edges.extend(validated)
                detail['edges_added'] = len(validated)
                report['edges_added'] += len(validated)
            else:
                detail['edges_added'] = 0
        elif client is not None:
            detail['edges_added'] = 0
        else:
            report['failed_passages'] += 1
            detail['edges_added'] = 0
            detail['reason'] = 'no LLM client'

        if standalone_entities:
            detail['standalone'] = [
                {'name': s.get('name'), 'reason': s.get('reason', '')}
                for s in standalone_entities
            ]

        report['details'].append(detail)

    corpus['stats']['gap_closure'] = report
    return report


# Backward compatibility
def run_orphan_closure(corpus, passages, valid_types, client=None):
    return run_closure(corpus, passages, valid_types, client=client)


def load_valid_types(schema_path: str) -> set[str]:
    """Load valid edge types from verify_schema.yaml."""
    import yaml
    with open(schema_path) as f:
        schema = yaml.safe_load(f)
    return set(schema.get('edges', {}).get('valid_types', []))


def main():
    parser = argparse.ArgumentParser(description='Step 7.5: Co-location gap closure')
    parser.add_argument('corpus', help='Path to corpus.json')
    parser.add_argument('passages', help='Path to passages.json')
    parser.add_argument('--schema', '-s', default=None,
                        help='Path to verify_schema.yaml')
    parser.add_argument('--output', '-o', default=None,
                        help='Output corpus path (default: overwrite input)')
    parser.add_argument('--report', '-r', default='orphan-closure-report.json',
                        help='Closure report output')
    parser.add_argument('--dry-run', action='store_true',
                        help='Detect gaps without LLM calls')
    args = parser.parse_args()

    with open(args.corpus) as f:
        corpus = json.load(f)
    with open(args.passages) as f:
        passage_data = json.load(f)
    passages = passage_data.get('passages', passage_data) if isinstance(passage_data, dict) else passage_data

    schema_path = args.schema
    if not schema_path:
        schema_path = os.path.join(os.path.dirname(__file__), '..', 'docs', 'verify_schema.yaml')
    valid_types = load_valid_types(schema_path)

    from llm_client import create_client
    client = None
    if not args.dry_run:
        client = create_client()

    print("--- Step 7.6: Co-location Gap Closure ---")
    report = run_closure(corpus, passages, valid_types, client=client)

    print(f"  Global orphans: {report['total_orphans']}")
    print(f"  Passages with gaps: {report['passages_checked']}")
    print(f"  Gap entities found: {report['gap_entities_found']}")
    print(f"  Edges added: {report['edges_added']}")
    print(f"  Standalone (justified): {report['standalone']}")
    for d in report['details']:
        gap_names = ', '.join(d['gap_entities'][:5])
        if len(d['gap_entities']) > 5:
            gap_names += f' (+{len(d["gap_entities"]) - 5} more)'
        added = d.get('edges_added', 0)
        print(f"    {d['passage_id']}: {len(d['gap_entities'])} gaps, +{added} edges — {gap_names}")

    output_path = args.output or args.corpus
    atomic_write(output_path, corpus)
    print(f"  Corpus written: {output_path}")

    report_path = os.path.join(os.path.dirname(output_path), args.report)
    atomic_write(report_path, report)
    print(f"  Report written: {report_path}")


if __name__ == '__main__':
    main()
