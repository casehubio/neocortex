#!/usr/bin/env python3
"""Step 7.5: Dangling edge resolution — LLM-assisted name matching and entity back-fill.

Runs after assembly (Step 7), before gap closure (Step 7.6).
Resolves edge endpoints that reference entities not in the node set:
  - Match: dangling name → existing node (name variant)
  - Create: dangling name → new node (missing entity)
  - Drop: edge is spurious, remove it

Input:  engram.json + passages.json
Output: engram.json (modified in place) + dangling-resolution-report.json

Usage:
    python3 dangling_resolution.py <engram.json> <passages.json>
"""

import argparse
import json
import os
import sys
from collections import defaultdict

from pipeline_state import atomic_write


def find_dangling_endpoints(nodes: list[dict], edges: list[dict]) -> list[dict]:
    """Find edge endpoints that don't match any node name.

    Returns a list of dicts with name, count, passages, edge_types.
    """
    node_names = {n['name'] for n in nodes}
    dangles = {}

    for e in edges:
        for field in ('source', 'target'):
            name = e[field]
            if name not in node_names:
                if name not in dangles:
                    dangles[name] = {'name': name, 'count': 0,
                                     'passages': set(), 'edge_types': set()}
                dangles[name]['count'] += 1
                pid = e.get('source_passage')
                if pid:
                    dangles[name]['passages'].add(pid)
                dangles[name]['edge_types'].add(e.get('type', '?'))

    return sorted(dangles.values(), key=lambda d: -d['count'])


def build_dangling_context(passage_id: str, dangling_names: list[str],
                           passages: list[dict],
                           all_nodes: list[dict]) -> dict:
    """Build context for a dangling resolution prompt."""
    passage_map = {p['id']: p['text'] for p in passages}
    passage_text = passage_map.get(passage_id, '')

    passage_nodes = []
    for n in all_nodes:
        if passage_id in n.get('source_passages', []):
            passage_nodes.append(n['name'])

    all_node_names = sorted(n['name'] for n in all_nodes)

    return {
        'passage_id': passage_id,
        'passage_text': passage_text,
        'dangling_names': dangling_names,
        'passage_nodes': sorted(passage_nodes),
        'all_node_names': all_node_names,
    }


def parse_resolution_response(text: str) -> list[dict]:
    """Parse LLM response into resolution actions."""
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
        return []

    try:
        result = json.loads(content[start:end])
    except json.JSONDecodeError:
        return []

    return result.get('resolutions', [])


def apply_resolutions(resolutions: list[dict], nodes: list[dict],
                      edges: list[dict]) -> dict:
    """Apply resolution actions to nodes and edges. Modifies in place.

    Returns a report dict.
    """
    node_names = {n['name'] for n in nodes}
    report = {'matched': 0, 'created': 0, 'dropped': 0,
              'dropped_edges': 0, 'match_failed': 0, 'details': []}

    for r in resolutions:
        name = r.get('name', '')
        action = r.get('action', '')

        if action == 'match':
            target = r.get('match_to', '')
            if target not in node_names:
                report['match_failed'] += 1
                report['details'].append({
                    'name': name, 'action': 'match_failed',
                    'reason': f'target "{target}" not in node set'})
                continue
            for e in edges:
                if e['source'] == name:
                    e['source'] = target
                if e['target'] == name:
                    e['target'] = target
            report['matched'] += 1
            report['details'].append({
                'name': name, 'action': 'matched', 'match_to': target})

        elif action == 'create':
            entity = r.get('entity', {})
            if not entity.get('name'):
                continue
            created_name = entity['name']
            node = {
                'name': created_name,
                'subgraph_type': entity.get('type', 'concept'),
                'properties': entity.get('properties', {}),
                'source_passages': entity.get('source_passages', []),
                'extraction_method': 'entity-backfill',
            }
            for key in ('traits', 'trait_properties', 'confidence',
                        'pad', 'valid_from', 'valid_until'):
                if key in entity:
                    node[key] = entity[key]
            nodes.append(node)
            node_names.add(created_name)
            if created_name != name:
                for e in edges:
                    if e['source'] == name:
                        e['source'] = created_name
                    if e['target'] == name:
                        e['target'] = created_name
            report['created'] += 1
            report['details'].append({
                'name': name, 'action': 'created',
                'created_as': created_name,
                'type': entity.get('type', 'concept')})

        elif action == 'drop':
            reason = r.get('reason', '')
            before = len(edges)
            edges[:] = [e for e in edges
                        if e['source'] != name and e['target'] != name]
            removed = before - len(edges)
            report['dropped'] += 1
            report['dropped_edges'] += removed
            report['details'].append({
                'name': name, 'action': 'dropped',
                'edges_removed': removed, 'reason': reason})

    return report


RESOLUTION_SYSTEM = """You are a knowledge graph repair tool. Some edges reference entity names that don't match any node in the graph. For each dangling name, decide:

1. **match** — the name is a variant of an existing node. Provide the exact node name to match to.
2. **create** — the entity is real but missing from the node set. Provide a full entity record.
3. **drop** — the edge is spurious or the entity is a transient condition/state, not a graph entity. Explain why.

Use these existing nodes from the passage for matching:
{passage_nodes}

All nodes in the graph:
{all_nodes}

RULES:
- For "match": match_to MUST be an exact name from the node list above
- For "create": provide name, type (person/place/activity/project/concept/organisation), and properties
- For "drop": provide a clear reason
- Prefer "match" over "create" — only create if no existing node fits
- Medical conditions, abstract states, and transient events are usually "drop" candidates

Respond with JSON only:
{{"resolutions": [{{"name": "...", "action": "match|create|drop", "match_to": "..." | "entity": {{...}} | "reason": "..."}}]}}"""


def resolution_prompt(context: dict) -> tuple[str, str]:
    """Build system and user prompts for dangling resolution."""
    passage_nodes_str = ', '.join(context['passage_nodes']) or '(none)'

    all_nodes_truncated = context['all_node_names']
    all_nodes_str = ', '.join(all_nodes_truncated)

    system = RESOLUTION_SYSTEM.format(
        passage_nodes=passage_nodes_str,
        all_nodes=all_nodes_str)

    dangling_str = '\n'.join(f'  - {name}' for name in context['dangling_names'])

    user = f"""Passage ({context['passage_id']}):
{context['passage_text']}

Dangling names (not matching any node):
{dangling_str}

For each dangling name, decide: match to an existing node, create a new entity, or drop the edges."""

    return system, user


def resolve_passage_dangles(context: dict,
                            client=None) -> list[dict]:
    """Call LLM to resolve dangling names for one passage."""
    if client is None:
        return []

    from llm_client import model_id

    system, user = resolution_prompt(context)

    try:
        response = client.messages.create(
            model=model_id(),
            max_tokens=4096,
            system=system,
            messages=[{'role': 'user', 'content': user}],
        )
        return parse_resolution_response(response.content[0].text)
    except Exception as e:
        print(f"  LLM call failed for passage {context['passage_id']}: {e}",
              file=sys.stderr)
        return []


def run_dangling_resolution(corpus: dict, passages: list[dict],
                            client=None) -> dict:
    """Run dangling edge resolution on an assembled corpus.

    Modifies corpus in place. Returns a report dict.
    """
    nodes = corpus['nodes']
    edges = corpus['edges']

    dangles = find_dangling_endpoints(nodes, edges)

    report = {
        'total_dangling': len(dangles),
        'passages_checked': 0,
        'matched': 0,
        'created': 0,
        'dropped': 0,
        'dropped_edges': 0,
        'match_failed': 0,
        'details': [],
    }

    if not dangles:
        # Still clean up orphan backfills from prior runs
        edge_names = set()
        for e in edges:
            edge_names.add(e['source'])
            edge_names.add(e['target'])
        before_cleanup = len(nodes)
        nodes[:] = [n for n in nodes
                    if n.get('extraction_method') != 'entity-backfill'
                    or n['name'] in edge_names]
        report['orphan_backfills_removed'] = before_cleanup - len(nodes)
        return report

    # Group dangles by passage
    by_passage = defaultdict(list)
    for d in dangles:
        for pid in d['passages']:
            if d['name'] not in by_passage[pid]:
                by_passage[pid].append(d['name'])
        if not d['passages']:
            by_passage['unknown'].append(d['name'])

    report['passages_checked'] = len(by_passage)

    for pid, names in sorted(by_passage.items()):
        context = build_dangling_context(pid, names, passages, nodes)
        resolutions = resolve_passage_dangles(context, client=client)

        passage_detail = {
            'passage_id': pid,
            'dangling_names': names,
            'resolutions': [],
        }

        if resolutions:
            sub_report = apply_resolutions(resolutions, nodes, edges)
            report['matched'] += sub_report['matched']
            report['created'] += sub_report['created']
            report['dropped'] += sub_report['dropped']
            report['dropped_edges'] += sub_report['dropped_edges']
            report['match_failed'] += sub_report['match_failed']
            passage_detail['resolutions'] = sub_report['details']

        report['details'].append(passage_detail)

    # Remove back-filled nodes that ended up with zero edges
    edge_names = set()
    for e in edges:
        edge_names.add(e['source'])
        edge_names.add(e['target'])
    before_cleanup = len(nodes)
    nodes[:] = [n for n in nodes
                if n.get('extraction_method') != 'entity-backfill'
                or n['name'] in edge_names]
    report['orphan_backfills_removed'] = before_cleanup - len(nodes)

    corpus['stats']['dangling_resolution'] = report
    return report


def main():
    parser = argparse.ArgumentParser(description='Step 7.6: Dangling edge resolution')
    parser.add_argument('corpus', help='Path to corpus.json')
    parser.add_argument('passages', help='Path to passages.json')
    parser.add_argument('--output', '-o', default=None,
                        help='Output corpus path (default: overwrite input)')
    parser.add_argument('--report', '-r', default='dangling-resolution-report.json',
                        help='Resolution report output')
    parser.add_argument('--dry-run', action='store_true',
                        help='Detect dangles without LLM calls')
    args = parser.parse_args()

    with open(args.corpus) as f:
        corpus = json.load(f)
    with open(args.passages) as f:
        passage_data = json.load(f)
    passages = passage_data.get('passages', passage_data) if isinstance(passage_data, dict) else passage_data

    from llm_client import create_client
    client = None
    if not args.dry_run:
        client = create_client()

    print("--- Step 7.5: Dangling Edge Resolution ---")
    report = run_dangling_resolution(corpus, passages, client=client)

    print(f"  Dangling names: {report['total_dangling']}")
    print(f"  Passages checked: {report['passages_checked']}")
    print(f"  Matched to existing: {report['matched']}")
    print(f"  New entities created: {report['created']}")
    print(f"  Dropped (spurious): {report['dropped']} ({report['dropped_edges']} edges removed)")
    if report['match_failed']:
        print(f"  Match failures: {report['match_failed']}")
    for d in report['details']:
        names = ', '.join(d['dangling_names'][:4])
        if len(d['dangling_names']) > 4:
            names += f' (+{len(d["dangling_names"]) - 4} more)'
        resolutions = len(d.get('resolutions', []))
        print(f"    {d['passage_id']}: {len(d['dangling_names'])} dangles, {resolutions} resolved — {names}")

    output_path = args.output or args.corpus
    atomic_write(output_path, corpus)
    print(f"  Corpus written: {output_path}")

    report_path = os.path.join(os.path.dirname(output_path), args.report)
    atomic_write(report_path, report)
    print(f"  Report written: {report_path}")


if __name__ == '__main__':
    main()
