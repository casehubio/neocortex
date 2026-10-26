#!/usr/bin/env python3
"""Step 7.5: Orphan closure — verification-driven edge repair for unconnected nodes.

Runs after assembly (Step 7), before verification (Step 8).
Identifies nodes with zero edges and generates targeted LLM edge repair.

Input:  corpus.json + passages.json
Output: corpus.json (modified in place) + orphan-closure-report.json

Usage:
    python3 orphan_closure.py <corpus.json> <passages.json> [--schema verify_schema.yaml] [--output corpus.json]
"""

import argparse
import json
import os
import sys

from pipeline_state import atomic_write


def find_orphans(nodes: list[dict], edges: list[dict]) -> list[dict]:
    """Identify nodes that have zero edges (neither source nor target)."""
    edge_nodes = set()
    for e in edges:
        edge_nodes.add(e['source'])
        edge_nodes.add(e['target'])
    return [n for n in nodes if n['name'] not in edge_nodes]


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


CLOSURE_SYSTEM = """You are a knowledge graph repair tool. An entity has been extracted but has ZERO edges — it is disconnected from the graph. Your job is to connect it using the relationship vocabulary, or explain why it is genuinely standalone.

You will receive:
- The orphan entity (name, type, properties)
- The source passage text where this entity was mentioned
- All other entities from the same passage
- All existing edges between those entities

Use ONLY the following edge types:
{vocabulary}

RULES:
- Use ONLY entity names from the provided entity list as source/target
- Use ONLY edge types from the vocabulary above
- The orphan MUST appear as source or target in at least one edge
- If the entity is genuinely standalone (generic concept, no specific relationship), set standalone_reason instead

Respond with JSON only:
{{"edges": [{{"source": "...", "target": "...", "type": "...", "confidence_origin": "INFERRED"}}], "standalone_reason": null}}
OR if standalone:
{{"edges": [], "standalone_reason": "explanation why this entity has no relationships"}}"""


def closure_prompt(context: dict, valid_types: list[str]) -> tuple[str, str]:
    """Build system and user prompts for orphan closure."""
    vocabulary = '\n'.join(f'  - {t}' for t in sorted(valid_types))
    system = CLOSURE_SYSTEM.format(vocabulary=vocabulary)

    entities_str = ', '.join(context['colocated_entities'])
    edges_str = '\n'.join(
        f"  {e['source']} --[{e['type']}]--> {e['target']}"
        for e in context['colocated_edges']
    ) or '  (no existing edges)'

    passages_str = '\n---\n'.join(context['passage_texts']) or '(no passage text available)'

    user = f"""Orphan entity:
  Name: {context['orphan_name']}
  Type: {context['orphan_type']}
  Properties: {json.dumps(context.get('orphan_properties', {}))}

Source passage:
{passages_str}

Other entities in this passage:
  {entities_str}

Existing edges between these entities:
{edges_str}

Connect "{context['orphan_name']}" to the graph or explain why it is standalone."""

    return system, user


def close_orphan(context: dict, valid_types: list[str],
                 client=None) -> tuple[list[dict], str | None]:
    """Call LLM to generate closure edges for one orphan."""
    if client is None:
        return [], None

    from llm_client import model_id

    system, user = closure_prompt(context, valid_types)

    try:
        response = client.messages.create(
            model=model_id(),
            max_tokens=1024,
            system=system,
            messages=[{'role': 'user', 'content': user}],
        )
        return parse_closure_response(response.content[0].text)
    except Exception as e:
        print(f"  LLM call failed for {context['orphan_name']}: {e}",
              file=sys.stderr)
        return [], None


def run_orphan_closure(corpus: dict, passages: list[dict],
                       valid_types: set[str],
                       client=None) -> dict:
    """Run orphan closure on an assembled corpus.

    Identifies orphan nodes, calls LLM for each, validates and merges edges.
    Modifies corpus in place. Returns a report dict.
    """
    nodes = corpus['nodes']
    edges = corpus['edges']
    node_names = {n['name'] for n in nodes}

    orphans = find_orphans(nodes, edges)

    report = {
        'total_orphans': len(orphans),
        'closed': 0,
        'standalone': 0,
        'failed': 0,
        'edges_added': 0,
        'details': [],
    }

    if not orphans:
        return report

    valid_types_set = set(valid_types)
    valid_types_list = sorted(valid_types)

    for orphan in orphans:
        context = build_orphan_context(orphan, passages, nodes, edges)
        raw_edges, standalone_reason = close_orphan(
            context, valid_types_list, client=client)

        detail = {
            'name': orphan['name'],
            'type': orphan.get('subgraph_type', 'concept'),
            'source_passages': orphan.get('source_passages', []),
        }

        if standalone_reason:
            detail['outcome'] = 'standalone'
            detail['reason'] = standalone_reason
            report['standalone'] += 1
        elif raw_edges:
            validated = validate_closure_edges(
                raw_edges, valid_types_set, node_names)
            if validated:
                for e in validated:
                    e['extraction_method'] = 'orphan-closure'
                    e.setdefault('confidence_origin', 'INFERRED')
                    e.setdefault('validation_tier', 'REGISTERED')
                edges.extend(validated)
                detail['outcome'] = 'closed'
                detail['edges_added'] = len(validated)
                report['closed'] += 1
                report['edges_added'] += len(validated)
            else:
                detail['outcome'] = 'failed'
                detail['reason'] = 'all generated edges rejected by validation'
                report['failed'] += 1
        else:
            detail['outcome'] = 'failed'
            detail['reason'] = 'no LLM client or LLM returned no edges'
            report['failed'] += 1

        report['details'].append(detail)

    corpus['stats']['orphan_closure'] = report
    return report


def load_valid_types(schema_path: str) -> set[str]:
    """Load valid edge types from verify_schema.yaml."""
    import yaml
    with open(schema_path) as f:
        schema = yaml.safe_load(f)
    return set(schema.get('edges', {}).get('valid_types', []))


def main():
    parser = argparse.ArgumentParser(description='Step 7.5: Orphan closure')
    parser.add_argument('corpus', help='Path to corpus.json')
    parser.add_argument('passages', help='Path to passages.json')
    parser.add_argument('--schema', '-s', default=None,
                        help='Path to verify_schema.yaml')
    parser.add_argument('--output', '-o', default=None,
                        help='Output corpus path (default: overwrite input)')
    parser.add_argument('--report', '-r', default='orphan-closure-report.json',
                        help='Closure report output')
    parser.add_argument('--dry-run', action='store_true',
                        help='Detect orphans without LLM calls')
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

    print("--- Step 7.5: Orphan Closure ---")
    report = run_orphan_closure(corpus, passages, valid_types, client=client)

    print(f"  Orphans found: {report['total_orphans']}")
    print(f"  Closed: {report['closed']} ({report['edges_added']} edges added)")
    print(f"  Standalone (justified): {report['standalone']}")
    print(f"  Failed: {report['failed']}")
    for d in report['details']:
        status = d['outcome'].upper()
        extra = ''
        if d.get('edges_added'):
            extra = f" (+{d['edges_added']} edges)"
        elif d.get('reason'):
            extra = f" ({d['reason'][:60]})"
        print(f"    [{d['type']}] {d['name']}: {status}{extra}")

    output_path = args.output or args.corpus
    atomic_write(output_path, corpus)
    print(f"  Corpus written: {output_path}")

    report_path = os.path.join(os.path.dirname(output_path), args.report)
    atomic_write(report_path, report)
    print(f"  Report written: {report_path}")


if __name__ == '__main__':
    main()
