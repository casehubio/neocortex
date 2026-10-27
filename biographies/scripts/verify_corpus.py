"""Import readiness verification for cognitive corpus output.

Validates corpus.json against the Java-side import contract declared in
verify_schema.yaml. Diagnoses issues, attributes them to pipeline stages,
and optionally applies safe fixes.

Usage:
  verify_corpus.py <corpus.json> [--schema <path>] [--fix] [--output <dir>]
"""

import argparse
import copy
import json
import os
import sys
from collections import Counter
from datetime import datetime, timezone

import yaml

from pipeline_state import atomic_write


def load_schema(path):
    with open(path) as f:
        return yaml.safe_load(f)


def _resolve_nested(obj, dotted_key):
    parts = dotted_key.split('.')
    current = obj
    for part in parts:
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def _set_nested(obj, dotted_key, value):
    parts = dotted_key.split('.')
    current = obj
    for part in parts[:-1]:
        if part not in current or not isinstance(current[part], dict):
            current[part] = {}
        current = current[part]
    current[parts[-1]] = value


def verify(corpus, schema):
    findings = []
    checks_passed = []
    finding_counter = [0]

    def add_finding(category, severity, source_stage, fix_level, fix_description,
                    count=1, details=None):
        finding_counter[0] += 1
        findings.append({
            'id': f'F{finding_counter[0]:03d}',
            'category': category,
            'severity': severity,
            'source_stage': source_stage,
            'fix_level': fix_level,
            'fix_description': fix_description,
            'count': count,
            'details': details or {},
            'fixed': False,
        })

    nodes = corpus.get('nodes', [])
    edges = corpus.get('edges', [])
    memories = corpus.get('memories', [])
    node_names = {n['name'] for n in nodes if n.get('name')}
    aliases = schema.get('entity_aliases', {})

    # --- Dangling edges ---
    dangling_by_alias = Counter()
    dangling_unresolvable = Counter()
    for edge in edges:
        src, tgt = edge.get('source', ''), edge.get('target', '')
        for ref in (src, tgt):
            if ref and ref not in node_names:
                if ref in aliases and aliases[ref] in node_names:
                    dangling_by_alias[ref] += 1
                else:
                    dangling_unresolvable[ref] += 1

    for alias, count in dangling_by_alias.items():
        canonical = aliases[alias]
        affected_types = Counter()
        for e in edges:
            if e.get('source') == alias or e.get('target') == alias:
                affected_types[e.get('type', 'unknown')] += 1
        add_finding(
            'dangling_edge', 'FIXABLE', 'extraction', 'post_processing',
            f"Resolve alias: '{alias}' → '{canonical}'",
            count=count,
            details={'alias': alias, 'canonical': canonical,
                     'affected_edges': count,
                     'edge_types': list(affected_types.keys())},
        )

    for ref, count in dangling_unresolvable.items():
        add_finding(
            'dangling_edge', 'CRITICAL', 'extraction', 'extraction',
            f"Edge references unknown entity: '{ref}'",
            count=count,
            details={'reference': ref, 'affected_edges': count},
        )

    total_dangling = sum(dangling_by_alias.values()) + sum(dangling_unresolvable.values())
    if total_dangling == 0:
        checks_passed.append('dangling_edges')

    # --- Field alignment ---
    for type_name, type_cfg in schema.get('entity_types', {}).items():
        mappings = type_cfg.get('field_mappings', {})
        if not mappings:
            continue
        cfg_sg = (type_cfg.get('subgraph_type') or '').upper()
        if not cfg_sg:
            continue
        typed_nodes = [n for n in nodes
                       if (n.get('subgraph_type') or '').upper() == cfg_sg]
        found_misalignment = False
        for corpus_field, java_field in mappings.items():
            affected = [n for n in typed_nodes if corpus_field in n]
            if affected:
                found_misalignment = True
                add_finding(
                    'field_alignment', 'CRITICAL', 'assembly', 'assembler',
                    f"Rename '{corpus_field}' → '{java_field}' "
                    f"(Java @JsonProperty expects unprefixed)",
                    count=len(affected),
                    details={'corpus_field': corpus_field, 'java_field': java_field,
                             'entity_type': type_name, 'affected_nodes': len(affected)},
                )
        if not found_misalignment:
            checks_passed.append(f'field_alignment_{type_name}')

    # --- Vocabulary validation ---
    for type_name, type_cfg in schema.get('entity_types', {}).items():
        vocabs = type_cfg.get('vocabularies', {})
        if not vocabs:
            continue
        cfg_sg = (type_cfg.get('subgraph_type') or '').upper()
        if not cfg_sg:
            continue
        typed_nodes = [n for n in nodes
                       if (n.get('subgraph_type') or '').upper() == cfg_sg]
        for field_name, vocab_cfg in vocabs.items():
            valid_values = set(vocab_cfg.get('values', []))
            violations = []
            for n in typed_nodes:
                val = n.get(field_name)
                if val and val not in valid_values:
                    violations.append({'node': n.get('name'), 'value': val})
            if violations:
                add_finding(
                    'vocabulary_violation', 'WARNING', 'extraction', 'assembler',
                    f"Invalid {field_name} values not in "
                    f"{vocab_cfg.get('source', 'canonical set')}",
                    count=len(violations),
                    details={'field': field_name, 'violations': violations[:20],
                             'valid_values': sorted(valid_values)},
                )
            else:
                checks_passed.append(f'vocabulary_{field_name}')

    # --- Range validation (nodes) ---
    for type_name, type_cfg in schema.get('entity_types', {}).items():
        ranges = type_cfg.get('ranges', {})
        if not ranges:
            continue
        cfg_sg = (type_cfg.get('subgraph_type') or '').upper()
        if not cfg_sg:
            continue
        typed_nodes = [n for n in nodes
                       if (n.get('subgraph_type') or '').upper() == cfg_sg]
        for dotted_key, bounds in ranges.items():
            lo, hi = bounds[0], bounds[1]
            violations = []
            for n in typed_nodes:
                val = _resolve_nested(n, dotted_key)
                if val is not None and isinstance(val, (int, float)):
                    if val < lo or val > hi:
                        violations.append({
                            'node': n.get('name', n.get('description', '?')),
                            'field': dotted_key, 'value': val,
                            'range': [lo, hi],
                        })
            if violations:
                add_finding(
                    'range_violation', 'WARNING', 'enrichment', 'post_processing',
                    f"{dotted_key} values outside [{lo}, {hi}]",
                    count=len(violations),
                    details={'field': dotted_key, 'range': [lo, hi],
                             'violations': violations[:20]},
                )

    # --- Range validation (memories) ---
    mem_ranges = schema.get('memories', {}).get('ranges', {})
    for dotted_key, bounds in mem_ranges.items():
        lo, hi = bounds[0], bounds[1]
        violations = []
        for m in memories:
            val = _resolve_nested(m, dotted_key)
            if val is not None and isinstance(val, (int, float)):
                if val < lo or val > hi:
                    violations.append({
                        'memory': m.get('text', '?')[:60],
                        'field': dotted_key, 'value': val,
                        'range': [lo, hi],
                    })
        if violations:
            add_finding(
                'range_violation', 'WARNING', 'enrichment', 'post_processing',
                f"Memory {dotted_key} values outside [{lo}, {hi}]",
                count=len(violations),
                details={'field': dotted_key, 'range': [lo, hi],
                         'violations': violations[:20]},
            )

    # --- Missing required fields ---
    for type_name, type_cfg in schema.get('entity_types', {}).items():
        required = type_cfg.get('required_fields', [])
        if not required:
            continue
        cfg_sg = (type_cfg.get('subgraph_type') or '').upper()
        if not cfg_sg:
            continue
        typed_nodes = [n for n in nodes
                       if (n.get('subgraph_type') or '').upper() == cfg_sg]
        for field in required:
            missing = [n for n in typed_nodes if not n.get(field)]
            if missing:
                add_finding(
                    'missing_required', 'CRITICAL', 'extraction', 'extraction',
                    f"Required field '{field}' missing on {type_name} nodes",
                    count=len(missing),
                    details={'field': field, 'entity_type': type_name,
                             'missing_nodes': [n.get('name', '?') for n in missing[:20]]},
                )

    # --- Volume anomaly ---
    for type_name, type_cfg in schema.get('entity_types', {}).items():
        vol = type_cfg.get('volume', {})
        if not vol:
            continue
        cfg_sg = (type_cfg.get('subgraph_type') or '').upper()
        if not cfg_sg:
            continue
        typed_nodes = [n for n in nodes
                       if (n.get('subgraph_type') or '').upper() == cfg_sg]
        warn_above = vol.get('warn_above', float('inf'))
        if len(typed_nodes) > warn_above:
            add_finding(
                'volume_anomaly', 'INFO', 'extraction', 'extraction',
                f"{len(typed_nodes)} {type_name} nodes (warn threshold: {warn_above})",
                count=len(typed_nodes),
                details={'entity_type': type_name, 'count': len(typed_nodes),
                         'threshold': warn_above},
            )

    # --- Subject alignment ---
    subject_aliases = Counter()
    subject_unknown = Counter()
    for m in memories:
        subj = m.get('subject', '')
        if subj and subj not in node_names:
            if subj in aliases and aliases[subj] in node_names:
                subject_aliases[subj] += 1
            else:
                subject_unknown[subj] += 1

    for alias, count in subject_aliases.items():
        canonical = aliases[alias]
        add_finding(
            'subject_alignment', 'FIXABLE', 'extraction', 'post_processing',
            f"Memory subject '{alias}' → '{canonical}'",
            count=count,
            details={'alias': alias, 'canonical': canonical,
                     'affected_memories': count},
        )

    for subj, count in subject_unknown.items():
        add_finding(
            'subject_alignment', 'WARNING', 'extraction', 'extraction',
            f"Memory subject '{subj}' not found in nodes",
            count=count,
            details={'subject': subj, 'affected_memories': count},
        )

    if not subject_aliases and not subject_unknown:
        checks_passed.append('subject_alignment')

    # --- Enrichment gap ---
    enrich_cfg = schema.get('memories', {})
    enrich_fields = enrich_cfg.get('enrichment_fields', [])
    enrich_threshold = enrich_cfg.get('enrichment_threshold', 80)
    experience_memories = [m for m in memories if m.get('domain') == 'experience']
    if experience_memories:
        enriched_count = sum(
            1 for m in experience_memories
            if any(m.get(f) for f in enrich_fields)
        )
        enrich_pct = enriched_count / len(experience_memories) * 100
        if enrich_pct < enrich_threshold:
            missing_count = len(experience_memories) - enriched_count
            add_finding(
                'enrichment_gap', 'INFO', 'enrichment', 'enrichment',
                f"{missing_count} experience memories missing cognitive enrichment "
                f"({enrich_pct:.0f}% < {enrich_threshold}% threshold)",
                count=missing_count,
                details={'enriched': enriched_count,
                         'total': len(experience_memories),
                         'pct': round(enrich_pct, 1),
                         'threshold': enrich_threshold},
            )
        else:
            checks_passed.append('enrichment_coverage')

    # --- Edge type validation ---
    valid_types = set(schema.get('edges', {}).get('valid_types', []))
    if valid_types:
        unknown_types = Counter()
        for e in edges:
            et = e.get('type', '')
            if et and et not in valid_types:
                unknown_types[et] += 1
        for et, count in unknown_types.items():
            add_finding(
                'edge_type_unknown', 'WARNING', 'extraction', 'extraction',
                f"Unknown edge type: '{et}'",
                count=count,
                details={'edge_type': et, 'occurrences': count},
            )
        if not unknown_types:
            checks_passed.append('edge_type_validity')

    # --- Sub-thought type validation ---
    valid_st = set(schema.get('memories', {}).get('sub_thought_types', []))
    if valid_st:
        invalid_st = Counter()
        for m in memories:
            for st in m.get('sub_thoughts', []) or []:
                st_type = st.get('type', '') if isinstance(st, dict) else ''
                if st_type and st_type not in valid_st:
                    invalid_st[st_type] += 1
        for st_type, count in invalid_st.items():
            add_finding(
                'sub_thought_type', 'WARNING', 'enrichment', 'enrichment',
                f"Invalid sub-thought type: '{st_type}'",
                count=count,
                details={'type': st_type, 'valid_types': sorted(valid_st)},
            )
        if not invalid_st:
            checks_passed.append('sub_thought_types')

    # --- Memory domain validation (R1-04 fix) ---
    valid_domains = set(schema.get('memories', {}).get('valid_domains', []))
    if valid_domains:
        invalid_domains = Counter()
        for m in memories:
            d = m.get('domain', '')
            if d and d not in valid_domains:
                invalid_domains[d] += 1
        for domain, count in invalid_domains.items():
            add_finding(
                'memory_domain_invalid', 'WARNING', 'extraction', 'extraction',
                f"Invalid memory domain: '{domain}'",
                count=count,
                details={'domain': domain, 'valid_domains': sorted(valid_domains),
                         'occurrences': count},
            )
        if not invalid_domains:
            checks_passed.append('memory_domain_validity')

    # --- Build summary ---
    counts = Counter(f['severity'] for f in findings)
    summary = {
        'total_findings': len(findings),
        'critical': counts.get('CRITICAL', 0),
        'fixable': counts.get('FIXABLE', 0),
        'warning': counts.get('WARNING', 0),
        'info': counts.get('INFO', 0),
        'gate': 'FAIL' if counts.get('CRITICAL', 0) > 0 or counts.get('FIXABLE', 0) > 0
                else ('WARN' if counts.get('WARNING', 0) > 0 else 'PASS'),
    }

    return {
        'format': 'import-readiness-report/v1',
        'corpus': corpus.get('format', ''),
        'timestamp': datetime.now(timezone.utc).isoformat(),
        'summary': summary,
        'findings': findings,
        'checks_passed': sorted(set(checks_passed)),
    }


def apply_fixes(corpus, report, schema):
    fixed = copy.deepcopy(corpus)
    updated_findings = copy.deepcopy(report['findings'])
    aliases = schema.get('entity_aliases', {})

    for finding in updated_findings:
        if finding['fixed']:
            continue

        if finding['category'] == 'dangling_edge' and finding['severity'] == 'FIXABLE':
            alias = finding['details'].get('alias', '')
            canonical = finding['details'].get('canonical', '')
            if alias and canonical:
                for edge in fixed['edges']:
                    if edge.get('source') == alias:
                        edge['source'] = canonical
                    if edge.get('target') == alias:
                        edge['target'] = canonical
                finding['fixed'] = True

        elif finding['category'] == 'field_alignment':
            corpus_field = finding['details'].get('corpus_field', '')
            java_field = finding['details'].get('java_field', '')
            entity_type = finding['details'].get('entity_type', '')
            type_cfg = schema.get('entity_types', {}).get(entity_type, {})
            cfg_sg = (type_cfg.get('subgraph_type') or '').upper()
            for node in fixed['nodes']:
                if (node.get('subgraph_type') or '').upper() == cfg_sg:
                    if corpus_field in node:
                        node[java_field] = node.pop(corpus_field)
            finding['fixed'] = True

        elif finding['category'] == 'range_violation':
            lo, hi = finding['details'].get('range', [-1, 1])
            dotted_key = finding['details'].get('field', '')
            for node in fixed['nodes']:
                val = _resolve_nested(node, dotted_key)
                if val is not None and isinstance(val, (int, float)):
                    if val < lo or val > hi:
                        _set_nested(node, dotted_key, max(lo, min(hi, val)))
            for mem in fixed['memories']:
                val = _resolve_nested(mem, dotted_key)
                if val is not None and isinstance(val, (int, float)):
                    if val < lo or val > hi:
                        _set_nested(mem, dotted_key, max(lo, min(hi, val)))
            finding['fixed'] = True

        elif finding['category'] == 'subject_alignment' and finding['severity'] == 'FIXABLE':
            alias = finding['details'].get('alias', '')
            canonical = finding['details'].get('canonical', '')
            if alias and canonical:
                for mem in fixed['memories']:
                    if mem.get('subject') == alias:
                        mem['subject'] = canonical
                finding['fixed'] = True

    unfixed = [f for f in updated_findings if not f['fixed']]
    counts = Counter(f['severity'] for f in unfixed)
    updated_report = copy.deepcopy(report)
    updated_report['findings'] = updated_findings
    updated_report['summary'] = {
        'total_findings': len(updated_findings),
        'critical': counts.get('CRITICAL', 0),
        'fixable': counts.get('FIXABLE', 0),
        'warning': counts.get('WARNING', 0),
        'info': counts.get('INFO', 0),
        'gate': 'FAIL' if counts.get('CRITICAL', 0) > 0 or counts.get('FIXABLE', 0) > 0
                else ('WARN' if counts.get('WARNING', 0) > 0 else 'PASS'),
    }

    return fixed, updated_report


def compute_exit_code(report):
    s = report['summary']
    if s.get('critical', 0) > 0 or s.get('fixable', 0) > 0:
        return 1
    if s.get('warning', 0) > 0:
        return 2
    return 0


def format_text_report(report):
    lines = []
    s = report['summary']
    gate_icon = {'PASS': 'PASS', 'WARN': 'WARN', 'FAIL': 'FAIL'}.get(s['gate'], '?')
    lines.append(f"Import Readiness: {gate_icon}")
    lines.append(f"  Findings: {s['total_findings']} "
                 f"(critical={s['critical']}, fixable={s['fixable']}, "
                 f"warning={s['warning']}, info={s['info']})")
    lines.append('')

    for f in report['findings']:
        fixed_tag = ' [FIXED]' if f.get('fixed') else ''
        lines.append(f"  [{f['severity']}] {f['category']}: "
                     f"{f['fix_description']} (x{f['count']}){fixed_tag}")
        lines.append(f"    Source: {f['source_stage']} -> Fix: {f['fix_level']}")

    if report.get('checks_passed'):
        lines.append('')
        lines.append(f"  Checks passed: {', '.join(report['checks_passed'])}")

    return '\n'.join(lines)


def main():
    parser = argparse.ArgumentParser(
        description='Verify corpus.json import readiness against Java-side contract')
    parser.add_argument('corpus', help='Path to corpus.json')
    parser.add_argument('--schema', '-s', default=None,
                        help='Path to verify_schema.yaml (default: docs/verify_schema.yaml)')
    parser.add_argument('--fix', action='store_true',
                        help='Apply safe fixes and write corpus-verified.json')
    parser.add_argument('--output', '-o', default=None,
                        help='Output directory (default: same as corpus)')
    args = parser.parse_args()

    schema_path = args.schema or os.path.join(
        os.path.dirname(__file__), '..', 'docs', 'verify_schema.yaml')
    output_dir = args.output or os.path.dirname(args.corpus) or '.'

    schema = load_schema(schema_path)
    with open(args.corpus) as f:
        corpus = json.load(f)

    report = verify(corpus, schema)

    if args.fix:
        fixed_corpus, report = apply_fixes(corpus, report, schema)
        verified_path = os.path.join(output_dir, 'corpus-verified.json')
        atomic_write(verified_path, fixed_corpus)
        print(f"Fixed corpus: {verified_path}")

    report_path = os.path.join(output_dir, 'verification-report.json')
    atomic_write(report_path, report)

    text_report = format_text_report(report)
    text_path = os.path.join(output_dir, 'verification-report.txt')
    with open(text_path, 'w') as f:
        f.write(text_report)

    print(text_report)
    print(f"\nReport: {report_path}")

    sys.exit(compute_exit_code(report))


if __name__ == '__main__':
    main()
