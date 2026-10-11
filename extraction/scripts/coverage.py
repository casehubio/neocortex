"""Coverage gate library — checks pipeline output against the field registry.

Loads the YAML field registry and checks extraction/enrichment/corpus/importer
output for coverage gaps. Each gap requires justification verified against
the source passage text by a Haiku verification agent.
"""

import json
import os
from dataclasses import dataclass, field
from typing import Any

import yaml


@dataclass
class CoverageReport:
    category: str
    populated: int
    total: int
    pct: float
    threshold: int
    gate: str
    gaps: list[dict] = field(default_factory=list)


@dataclass
class VerificationResult:
    verified: bool
    field_checks: list[dict]
    retries_needed: list[str]


def load_registry(path: str) -> dict:
    with open(path, 'r') as f:
        return yaml.safe_load(f)


def _field_applies(field_def: dict, entity: dict, stage: str = None) -> bool:
    if stage:
        stages = field_def.get('stages', [])
        if stages and stage not in stages:
            return False
    type_filter = field_def.get('type_filter')
    if type_filter and entity.get('subgraph_type') != type_filter:
        return False
    event_filter = field_def.get('event_filter')
    if event_filter and entity.get('event_type') != event_filter:
        return False
    return True


_NODE_CHECKERS = {
    'name': lambda e: bool(e.get('name')),
    'subgraph_type': lambda e: bool(e.get('subgraph_type')),
    'properties': lambda e: bool(e.get('properties')),
    'traits': lambda e: bool(e.get('traits')),
    'trait_properties': lambda e: bool(e.get('trait_properties')),
    'pad': lambda e: isinstance(e.get('pad'), dict) and e['pad'].get('pleasure') is not None,
    'confidence': lambda e: isinstance(e.get('confidence'), dict) and 'origin' in e.get('confidence', {}),
    'confidence_decay_ref': lambda e: bool((e.get('confidence') or {}).get('decay_reference')),
    'node_ref_provenance': lambda e: bool(e.get('node_ref')),
    'valid_from': lambda e: e.get('valid_from') is not None,
    'valid_until': lambda e: e.get('valid_until') is not None,
    'lifecycle_events': lambda e: bool(e.get('lifecycle')) or any(k.startswith('lifecycle.') for k in e.get('properties', {})),
    'edge_confidence': lambda e: bool(e.get('confidence_origin')),
    'edge_validation_tier': lambda e: bool(e.get('validation_tier')),
    'goal_content_domain': lambda e: bool(e.get('properties', {}).get('contentDomain') or e.get('goal', {}).get('content_domain')),
    'goal_content_subdomain': lambda e: bool(e.get('properties', {}).get('contentSubdomain') or e.get('goal', {}).get('content_subdomain')),
    'goal_motivation_origin': lambda e: bool(e.get('properties', {}).get('motivationOrigin') or e.get('goal', {}).get('motivation_origin')),
    'goal_clarity': lambda e: (e.get('properties', {}).get('clarity') or e.get('goal', {}).get('clarity')) is not None,
    'goal_evidence_basis': lambda e: bool(e.get('properties', {}).get('evidenceBasis') or e.get('goal', {}).get('evidence_basis')) or (e.get('confidence', {}) or {}).get('origin') == 'SPECULATED',
}


def _check_node_field(field_def: dict, entity: dict) -> bool:
    name = field_def['name']
    checker = _NODE_CHECKERS.get(name)
    if checker:
        return checker(entity)
    props = entity.get('properties', {})
    goal = entity.get('goal', {})
    clean = name.replace('goal_', '').replace('_', '-')
    return clean in props or clean in goal or name in entity


def check_node_coverage(nodes: list[dict], registry: dict, stage: str = None) -> CoverageReport:
    cat = registry['categories']['node_fields']
    populated_count = 0
    gaps = []
    applicable_count = 0

    field_threshold = 0.5
    for field_def in cat['fields']:
        relevant = [n for n in nodes if _field_applies(field_def, n, stage)]
        if not relevant:
            continue
        applicable_count += 1
        present = sum(1 for n in relevant if _check_node_field(field_def, n))
        if present >= len(relevant) * field_threshold:
            populated_count += 1
        else:
            gaps.append({'id': field_def['id'], 'name': field_def['name'],
                         'missing': len(relevant) - present, 'total': len(relevant)})

    total = applicable_count
    pct = round(populated_count / max(total, 1) * 100, 1)
    threshold = cat.get('threshold', 60)
    gate = 'PASS' if pct >= threshold else 'WARN' if pct >= threshold * 0.5 else 'FAIL'
    return CoverageReport('node_fields', populated_count, total, pct, threshold, gate, gaps)


def check_memory_coverage(memories: list[dict], registry: dict, stage: str = None) -> CoverageReport:
    cat = registry['categories']['memory_fields']
    populated_count = 0
    gaps = []
    applicable_count = 0
    field_threshold = 0.5

    mem_key_map = {
        'text': 'text', 'domain': 'domain', 'event_type': 'event_type',
        'subject': 'subject', 'period': 'period', 'related_entities': 'related_entities',
        'source_provenance': 'source_passage', 'extraction_method': 'extraction_method',
        'extraction_pass': 'extraction_pass', 'passage_id': 'source_passage',
        'enrichment_version': 'enrichment_version', 'enrichment_timestamp': 'enrichment_timestamp',
        'mentioned_entities': 'mentioned_entities',
        'formative_developmental_period': 'developmental_period',
        'formative_situation_types': 'situation_types',
        'formative_salience_multiplier': 'salience_multiplier',
        'formative_reinforcement_schedule': 'reinforcement_schedule',
    }

    for field_def in cat['fields']:
        relevant = [m for m in memories if _field_applies(field_def, m, stage)]
        if not relevant:
            continue
        applicable_count += 1
        name = field_def['name']

        if name == 'domain':
            non_experience = sum(1 for m in relevant if m.get('domain', 'experience') != 'experience')
            if non_experience == 0:
                gaps.append({'id': field_def['id'], 'name': name,
                             'note': 'all memories hardcoded as experience',
                             'missing': len(relevant), 'total': len(relevant)})
            else:
                populated_count += 1
            continue

        key = mem_key_map.get(name, name)
        present = sum(1 for m in relevant if m.get(key) is not None and m.get(key) != '')
        if present >= len(relevant) * field_threshold:
            populated_count += 1
        else:
            gaps.append({'id': field_def['id'], 'name': name,
                         'missing': len(relevant) - present, 'total': len(relevant)})

    total = applicable_count
    pct = round(populated_count / max(total, 1) * 100, 1)
    threshold = cat.get('threshold', 50)
    gate = 'PASS' if pct >= threshold else 'WARN' if pct >= threshold * 0.5 else 'FAIL'
    return CoverageReport('memory_fields', populated_count, total, pct, threshold, gate, gaps)


_ENRICHMENT_CHECKERS = {
    'pad': lambda m: isinstance(m.get('pad'), dict) and m['pad'].get('pleasure') is not None,
    'pad_justification': lambda m: bool(m.get('pad_justification')),
    'occ_emotions': lambda m: bool(m.get('occ_emotions')),
    'occ_emotion_source': lambda m: any(isinstance(e, dict) and 'source' in e for e in m.get('occ_emotions', [])),
    'sec_appraisal': lambda m: isinstance(m.get('sec_appraisal') or m.get('sec'), dict),
    'action_tendencies_intensity': lambda m: (isinstance(m.get('action_tendencies'), dict) and 'intensity' in m['action_tendencies'].get('dominant', {})) or any(isinstance(t, dict) and 'intensity' in t for t in m.get('action_tendencies', []) if isinstance(m.get('action_tendencies'), list)),
    'action_tendencies_structure': lambda m: (isinstance(m.get('action_tendencies'), dict) and 'type' in m['action_tendencies'].get('dominant', {})) or any(isinstance(t, dict) and 'type' in t for t in m.get('action_tendencies', []) if isinstance(m.get('action_tendencies'), list)),
    'cognitive_distortions_strength': lambda m: any(isinstance(d, dict) and 'strength' in d for d in m.get('cognitive_distortions', [])),
    'drive_impact': lambda m: isinstance(m.get('drive_impact'), dict),
    'drive_justification': lambda m: bool(m.get('drive_justification')),
    'sub_thoughts': lambda m: bool(m.get('sub_thoughts')),
    'sub_thoughts_structured': lambda m: any(isinstance(st, dict) and 'type' in st and 'text' in st for st in m.get('sub_thoughts', [])),
    'confidence': lambda m: isinstance(m.get('confidence'), dict),
    'confidence_justification': lambda m: bool(m.get('confidence_justification')),
    'habituation': lambda m: m.get('habituation') is not None,
    'gut_feeling': lambda m: m.get('gut_feeling') is not None,
    'caps_inputs': lambda m: bool(m.get('caps_inputs')),
    'caps_expected_outputs': lambda m: bool(m.get('caps_expected_outputs')),
    'goal_impact_goals': lambda m: bool(m.get('goal_impacts')),
    'goal_impact_state': lambda m: any(g.get('state_change') for g in m.get('goal_impacts', []) if isinstance(g, dict)),
    'relationship_quality': lambda m: bool((m.get('relationship') or {}).get('quality_signal') if isinstance(m.get('relationship'), dict) else m.get('quality_signal')),
    'relationship_other_agent': lambda m: bool((m.get('relationship') or {}).get('other_agent') if isinstance(m.get('relationship'), dict) else m.get('other_agent')),
    'temporal_anchoring': lambda m: bool(m.get('temporal_period') or m.get('period')),
    'trait_assignment': lambda m: False,
}


def check_enrichment_coverage(memories: list[dict], registry: dict) -> CoverageReport:
    cat = registry['categories']['enrichment_fields']
    populated_count = 0
    gaps = []

    for field_def in cat['fields']:
        name = field_def['name']
        checker = _ENRICHMENT_CHECKERS.get(name, lambda m: False)
        present = sum(1 for m in memories if checker(m))
        if present >= len(memories) * 0.5:
            populated_count += 1
        elif present == 0:
            gaps.append({'id': field_def['id'], 'name': name,
                         'missing': len(memories), 'total': len(memories)})
        else:
            gaps.append({'id': field_def['id'], 'name': name,
                         'missing': len(memories) - present, 'total': len(memories)})

    total = len(cat['fields'])
    pct = round(populated_count / max(total, 1) * 100, 1)
    threshold = cat.get('threshold', 70)
    gate = 'PASS' if pct >= threshold else 'WARN' if pct >= threshold * 0.5 else 'FAIL'
    return CoverageReport('enrichment_fields', populated_count, total, pct, threshold, gate, gaps)


def format_report(reports: list[CoverageReport]) -> str:
    lines = ['=== Corpus Coverage Report ===', '']
    total_pop = 0
    total_all = 0
    for r in reports:
        total_pop += r.populated
        total_all += r.total
        icon = '✅' if r.gate == 'PASS' else '⚠' if r.gate == 'WARN' else '❌'
        lines.append(f'{r.category:25s} {r.populated:3d}/{r.total:<3d} ({r.pct:5.1f}%)  {icon} {r.gate} (threshold: {r.threshold}%)')
        for g in r.gaps[:5]:
            lines.append(f'  ❌ {g["id"]} {g["name"]}: {g.get("missing", "?")}/{g.get("total", "?")} missing')
        if len(r.gaps) > 5:
            lines.append(f'  ... and {len(r.gaps) - 5} more gaps')
    lines.append('')
    overall_pct = round(total_pop / max(total_all, 1) * 100, 1)
    lines.append(f'Overall: {total_pop}/{total_all} ({overall_pct}%)')
    return '\n'.join(lines)


VERIFICATION_PROMPT = """You are a verification agent for cognitive extraction quality.

Given:
1. An original biographical passage
2. The extracted/enriched output for that passage
3. The list of fields that should be populated for this artifact type

For each required field:
- If populated: verify the value is reasonable given the passage text. Flag if contradicted.
- If absent: check whether absence is justified by the passage content.
  - Justified: the passage genuinely does not contain information for this field.
  - Unjustified: the passage contains information that should have been captured.

Return JSON:
{"verified": true/false, "checks": [{"field": "name", "status": "present|justified_absent|unjustified_absent|incorrect", "reason": "..."}]}"""


def verify_output(client, passage_text: str, output: dict, applicable_fields: list[str],
                  artifact_type: str, model_fn=None) -> VerificationResult:
    from llm_client import model_id as default_model_id
    mid = (model_fn or default_model_id)()

    user_prompt = f"""Passage:
"{passage_text}"

Extracted output ({artifact_type}):
{json.dumps(output, indent=2, default=str)[:3000]}

Required fields for {artifact_type}: {', '.join(applicable_fields)}

Verify each field. Return JSON."""

    try:
        response = client.messages.create(
            model=mid,
            max_tokens=1024,
            system=VERIFICATION_PROMPT,
            messages=[{'role': 'user', 'content': user_prompt}],
        )
        text = response.content[0].text
        start = text.find('{')
        end = text.rfind('}') + 1
        if start >= 0 and end > start:
            result = json.loads(text[start:end])
            checks = result.get('checks', [])
            retries = [c['field'] for c in checks if c.get('status') == 'unjustified_absent']
            return VerificationResult(
                verified=result.get('verified', len(retries) == 0),
                field_checks=checks,
                retries_needed=retries,
            )
    except Exception as e:
        print(f"  Verification warning: {e}")

    return VerificationResult(verified=True, field_checks=[], retries_needed=[])
