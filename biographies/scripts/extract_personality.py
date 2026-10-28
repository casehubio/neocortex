#!/usr/bin/env python3
"""Step 5d: Personality profile extraction — schema-driven LLM assessment.

Reads personality-vocab.json (eidos vocabulary manifest) to build
structurally valid LLM prompts for 13 personality frameworks + 48
archetype identity matching. Validates output against the manifest.

Two parallel LLM calls:
  Call 1: 13 personality frameworks (OCEAN, MBTI, Jungian, Enneagram,
          DISC, Belbin, TK, SDI, AMPD, attachment, locus of control,
          disposition)
  Call 2: 48 archetypes from Hartwell & Chen taxonomy

Input:  enriched.json (Phase 2b output) + structure.json
Output: enriched.json (updated with personality_profile key)

Usage:
    python3 extract_personality.py <enriched.json> <structure.json> \
        --schema <personality-vocab.json> --subject "Subject Name"
"""

import argparse
import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor

from llm_client import create_client, model_id
from pipeline_state import atomic_write


AMPD_FACETS = [
    'callousness', 'manipulativeness', 'grandiosity', 'deceitfulness',
    'attention-seeking', 'hostility', 'suspiciousness', 'intimacy-avoidance',
    'emotional-lability', 'anxiousness', 'risk-taking', 'impulsivity', 'withdrawal'
]

ATTACHMENT_STYLES = [
    'secure', 'anxious-preoccupied', 'dismissive-avoidant', 'fearful-avoidant'
]


def load_vocab_schema(path: str) -> dict:
    with open(path) as f:
        return json.load(f)


def _format_framework_values(schema: dict, key: str) -> str:
    items = schema.get(key, [])
    lines = []
    for item in items:
        val = item.get('value', '')
        label = item.get('label', '')
        desc = item.get('description', '')
        lines.append(f"  - {val} ({label}): {desc}")
    return '\n'.join(lines)


def build_frameworks_prompt(schema: dict, context: str) -> str:
    mbti_values = ', '.join(t['value'] for t in schema.get('mbti', []))
    mbti_details = _format_framework_values(schema, 'mbti')

    jungian_details = _format_framework_values(schema, 'jungian')

    enneagram_details = _format_framework_values(schema, 'enneagram')

    disc_details = _format_framework_values(schema, 'disc')

    belbin_details = _format_framework_values(schema, 'belbin')

    tk_terms = schema.get('axisValues', {}).get('thomasKilmann', {}).get('terms', [])
    tk_details = '\n'.join(f"  - {t['value']} ({t['label']}): {t['description']}" for t in tk_terms)

    sdi_details = _format_framework_values(schema, 'sdi')

    bigfive_details = _format_framework_values(schema, 'bigFive')

    ampd_list = ', '.join(AMPD_FACETS)

    system = f"""You are a personality psychologist assessing a biographical subject across multiple validated frameworks.
For EACH framework, provide your assessment with specific biographical evidence citations.
Assess independently — do not let one framework's result bias another.

### 1. OCEAN / Big Five (scores 0.0–1.0 per dimension)
Dimensions: openness, conscientiousness, extraversion, agreeableness, neuroticism
Valid poles:
{bigfive_details}

### 2. MBTI (one of 16 types)
Valid types: {mbti_values}
{mbti_details}

### 3. Jungian Cognitive Function Stack (8 functions with weights summing to ~1.0)
Valid functions:
{jungian_details}
Provide the top 4 functions with weights (dominant ~0.35, auxiliary ~0.20, tertiary ~0.15, inferior ~0.10). Remaining functions share ~0.20.

### 4. Enneagram (type + optional wing)
Valid types:
{enneagram_details}
Format: "type-N" with optional wing as "Nw[wing]" (e.g. "4w5")

### 5. DISC (primary + optional secondary)
Valid quadrants:
{disc_details}

### 6. Belbin Team Roles (primary + optional secondary)
Valid roles:
{belbin_details}

### 7. Thomas-Kilmann Conflict Mode (primary style)
Valid styles:
{tk_details}

### 8. SDI — Strength Deployment Inventory (primary motivational value)
Valid values:
{sdi_details}

### 9. AMPD — Alternative Model for Personality Disorders (DSM-5)
Score each facet 0–100 (50 = population average):
Facets: {ampd_list}

### 10. Attachment Style
Valid styles: {', '.join(ATTACHMENT_STYLES)}
Also provide: anxiety (0.0–1.0), avoidance (0.0–1.0)

### 11. Locus of Control
General: internal or external
Per-domain: provide internal/external/mixed for relevant life domains (e.g. art, health, relationships)

### 12. Published Assessments
If you are aware of any published personality typings for this subject (from academic papers, typing communities, biographies), note them. DO NOT adjust your own assessment to match — assess from the biographical evidence independently, then note disagreements.

Respond with JSON only:
{{
  "frameworks": {{
    "ocean": {{"openness": 0.0, "conscientiousness": 0.0, "extraversion": 0.0, "agreeableness": 0.0, "neuroticism": 0.0, "evidence": "...", "confidence": 0.0, "source": "extracted"}},
    "mbti": {{"type": "xxxx", "evidence": "...", "confidence": 0.0, "source": "extracted"}},
    "jungian_stack": [{{"function": "xx", "weight": 0.0, "evidence": "..."}}],
    "enneagram": {{"type": "type-N", "wing": "N", "evidence": "...", "confidence": 0.0, "source": "extracted"}},
    "disc": {{"primary": "xxx", "secondary": "xxx", "evidence": "...", "confidence": 0.0, "source": "extracted"}},
    "belbin": {{"primary": "xxx", "secondary": "xxx", "evidence": "...", "confidence": 0.0, "source": "extracted"}},
    "thomas_kilmann": {{"primary": "xxx", "evidence": "...", "confidence": 0.0, "source": "extracted"}},
    "sdi": {{"primary": "xxx", "evidence": "...", "confidence": 0.0, "source": "extracted"}},
    "ampd": {{"callousness": 50, ..., "evidence": "...", "confidence": 0.0, "source": "extracted"}},
    "attachment": {{"style": "xxx", "anxiety": 0.0, "avoidance": 0.0, "evidence": "...", "confidence": 0.0, "source": "extracted"}},
    "locus_of_control": {{"general": "internal", "domains": {{"art": "internal"}}, "evidence": "...", "confidence": 0.0, "source": "extracted"}}
  }},
  "published_assessments": [{{"framework": "mbti", "published_value": "xxxx", "source_description": "..."}}]
}}"""

    return system


def build_archetypes_prompt(schema: dict, context: str) -> str:
    archetype_data = schema.get('archetype', {})
    families = archetype_data.get('families', [])
    terms = archetype_data.get('terms', [])

    family_section = '\n'.join(
        f"  - {f.get('name', f.get('value', ''))}: {f.get('drive', f.get('description', ''))}"
        for f in families
    )

    term_section = []
    for t in terms:
        val = t.get('value', '')
        label = t.get('label', '')
        desc = t.get('description', '')
        family = t.get('family', '')
        adj = ', '.join(t.get('validAdjectives', []))
        term_section.append(f"  - {val} ({label}) [family: {family}]: {desc}\n    Valid adjectives: {adj}")

    terms_text = '\n'.join(term_section)

    system = f"""You are a narrative psychologist identifying which archetypes a biographical subject embodies.

The archetype system has 12 families in 4 quadrants, with 4 sub-archetypes per family (48 total).

### Families
{family_section}

### Sub-Archetypes (select from these ONLY)
{terms_text}

### Instructions
1. Identify the primary, secondary, and (optionally) tertiary archetype for this subject
2. Each selection must use a valid sub-archetype value from the list above
3. Select adjectives ONLY from the valid adjectives list for each chosen sub-archetype
4. Provide specific biographical evidence for each selection
5. Rank by strength of evidence (1 = strongest)

Respond with JSON only:
{{
  "archetypes": [
    {{
      "rank": 1,
      "family": "family_name_lowercase",
      "sub_archetype": "value_from_list",
      "adjectives": ["adj1", "adj2", "adj3"],
      "evidence": "Specific biographical evidence...",
      "confidence": 0.0
    }}
  ]
}}"""

    return system


def build_context_for_subject(subject_name: str, entities: list, edges: list,
                               memories: list, max_memories: int = 40) -> str:
    name_lower = subject_name.lower()

    subject_entity = None
    for e in entities:
        if e.get('name', '').lower() == name_lower:
            subject_entity = e
            break

    subject_edges = [e for e in edges
                     if e.get('source', '').lower() == name_lower
                     or e.get('target', '').lower() == name_lower]

    related_mems = []
    for mem in memories:
        related = [r.lower() for r in mem.get('related_entities', [])]
        if name_lower in related or mem.get('subject', '').lower() == name_lower:
            related_mems.append(mem)

    related_mems.sort(
        key=lambda m: abs(m.get('cognitive', {}).get('pad', {}).get('pleasure', 0) or 0),
        reverse=True
    )
    shown = related_mems[:max_memories]

    mem_text = '\n'.join(
        f"  [{i}] ({m.get('domain', 'experience')}/{m.get('event_type', '?')}) "
        f"{m.get('text', '')[:150]}..."
        for i, m in enumerate(shown)
    )
    edge_text = '\n'.join(
        f"  {e['source']} --[{e['type']}]--> {e['target']}"
        for e in subject_edges[:60]
    )

    return f"""Subject: {subject_name}

Edges ({len(subject_edges)} total):
{edge_text if edge_text else '  (none)'}

Related memories ({len(related_mems)} total, showing top {len(shown)}):
{mem_text if mem_text else '  (none)'}"""


def validate_output(result: dict, schema: dict) -> tuple[dict, list[str]]:
    warnings = []
    validated = json.loads(json.dumps(result))

    frameworks = validated.get('frameworks', {})

    valid_mbti = {t['value'] for t in schema.get('mbti', [])}
    mbti = frameworks.get('mbti', {})
    if mbti and mbti.get('type') and mbti['type'] not in valid_mbti:
        warnings.append(f"Invalid MBTI type: {mbti['type']} — not in {valid_mbti}")
        mbti['type'] = None

    valid_enneagram = {t['value'] for t in schema.get('enneagram', [])}
    enn = frameworks.get('enneagram', {})
    if enn and enn.get('type') and enn['type'] not in valid_enneagram:
        warnings.append(f"Invalid Enneagram type: {enn['type']} — not in {valid_enneagram}")
        enn['type'] = None

    valid_disc = {t['value'] for t in schema.get('disc', [])}
    disc = frameworks.get('disc', {})
    if disc and disc.get('primary') and disc['primary'] not in valid_disc:
        warnings.append(f"Invalid DISC primary: {disc['primary']}")
        disc['primary'] = None

    valid_belbin = {t['value'] for t in schema.get('belbin', [])}
    belbin = frameworks.get('belbin', {})
    if belbin and belbin.get('primary') and belbin['primary'] not in valid_belbin:
        warnings.append(f"Invalid Belbin primary: {belbin['primary']}")
        belbin['primary'] = None

    tk_terms = schema.get('axisValues', {}).get('thomasKilmann', {}).get('terms', [])
    valid_tk = {t['value'] for t in tk_terms}
    tk = frameworks.get('thomas_kilmann', {})
    if tk and tk.get('primary') and tk['primary'] not in valid_tk:
        warnings.append(f"Invalid TK primary: {tk['primary']}")
        tk['primary'] = None

    valid_sdi = {t['value'] for t in schema.get('sdi', [])}
    sdi = frameworks.get('sdi', {})
    if sdi and sdi.get('primary') and sdi['primary'] not in valid_sdi:
        warnings.append(f"Invalid SDI primary: {sdi['primary']}")
        sdi['primary'] = None

    valid_jungian = {t['value'] for t in schema.get('jungian', [])}
    stack = frameworks.get('jungian_stack', [])
    for entry in stack:
        if entry.get('function') and entry['function'] not in valid_jungian:
            warnings.append(f"Invalid Jungian function: {entry['function']}")
            entry['function'] = None

    attachment = frameworks.get('attachment', {})
    if attachment and attachment.get('style') and attachment['style'] not in ATTACHMENT_STYLES:
        warnings.append(f"Invalid attachment style: {attachment['style']}")
        attachment['style'] = None

    archetype_terms = {t['value'] for t in schema.get('archetype', {}).get('terms', [])}
    archetype_adj_map = {}
    for t in schema.get('archetype', {}).get('terms', []):
        archetype_adj_map[t['value']] = set(t.get('validAdjectives', []))

    for arch in validated.get('archetypes', []):
        sub = arch.get('sub_archetype')
        if sub and sub not in archetype_terms:
            warnings.append(f"Invalid archetype sub_archetype: {sub}")
            arch['sub_archetype'] = None
        elif sub and sub in archetype_adj_map:
            valid_adj = archetype_adj_map[sub]
            if arch.get('adjectives'):
                invalid = [a for a in arch['adjectives'] if a not in valid_adj]
                if invalid:
                    warnings.append(f"Invalid adjectives for {sub}: {invalid}")
                    arch['adjectives'] = [a for a in arch['adjectives'] if a in valid_adj]

    return validated, warnings


def check_consistency(result: dict, schema: dict) -> list[dict]:
    inconsistencies = []
    frameworks = result.get('frameworks', {})
    axis_mappings = schema.get('axisMappings', {})

    mbti_type = frameworks.get('mbti', {}).get('type')
    ocean = frameworks.get('ocean', {})

    if mbti_type and ocean and mbti_type in axis_mappings.get('mbti', {}):
        mbti_mapping = axis_mappings['mbti'][mbti_type]
        mbti_social = mbti_mapping.get('socialOrient')

        extraversion = ocean.get('extraversion')
        if extraversion is not None:
            if extraversion > 0.65 and mbti_social == 'independent':
                inconsistencies.append({
                    'frameworks': ['ocean', 'mbti'],
                    'axis': 'socialOrient',
                    'description': f"High OCEAN extraversion ({extraversion}) conflicts with "
                                   f"MBTI {mbti_type} mapping to socialOrient={mbti_social}"
                })
            elif extraversion < 0.35 and mbti_social in ('collaborative', 'facilitative'):
                inconsistencies.append({
                    'frameworks': ['ocean', 'mbti'],
                    'axis': 'socialOrient',
                    'description': f"Low OCEAN extraversion ({extraversion}) conflicts with "
                                   f"MBTI {mbti_type} mapping to socialOrient={mbti_social}"
                })

    disc_primary = frameworks.get('disc', {}).get('primary')
    tk_primary = frameworks.get('thomas_kilmann', {}).get('primary')

    if disc_primary and tk_primary:
        disc_mapping = axis_mappings.get('disc', {}).get(disc_primary, {})
        disc_conflict = disc_mapping.get('conflictMode')

        if disc_conflict and tk_primary:
            if disc_conflict == 'competing' and tk_primary == 'accommodating':
                inconsistencies.append({
                    'frameworks': ['disc', 'thomas_kilmann'],
                    'axis': 'conflictMode',
                    'description': f"DISC {disc_primary} maps to conflictMode={disc_conflict} "
                                   f"but TK primary is {tk_primary}"
                })
            elif disc_conflict == 'accommodating' and tk_primary == 'competing':
                inconsistencies.append({
                    'frameworks': ['disc', 'thomas_kilmann'],
                    'axis': 'conflictMode',
                    'description': f"DISC {disc_primary} maps to conflictMode={disc_conflict} "
                                   f"but TK primary is {tk_primary}"
                })

    return inconsistencies


def call_llm(client, system_prompt: str, user_prompt: str, label: str) -> dict:
    try:
        response = client.messages.create(
            model=model_id(),
            max_tokens=8192,
            system=system_prompt,
            messages=[{'role': 'user', 'content': user_prompt}],
        )
        text = response.content[0].text
        start = text.find('{')
        end = text.rfind('}') + 1
        if start >= 0 and end > start:
            return json.loads(text[start:end])
    except Exception as e:
        print(f"  Warning: {label} LLM call failed: {e}", file=sys.stderr)
    return {}


def derive_disposition(result: dict, schema: dict) -> dict:
    frameworks = result.get('frameworks', {})
    axis_mappings = schema.get('axisMappings', {})

    axis_votes = {
        'socialOrient': [],
        'ruleFollowing': [],
        'riskAppetite': [],
        'autonomy': [],
        'conflictMode': []
    }

    for fw_key in ['mbti', 'enneagram', 'disc', 'belbin', 'sdi']:
        fw = frameworks.get(fw_key, {})
        fw_type = fw.get('type') or fw.get('primary')
        if fw_type and fw_type in axis_mappings.get(fw_key, {}):
            mapping = axis_mappings[fw_key][fw_type]
            for axis, term in mapping.items():
                if axis in axis_votes:
                    axis_votes[axis].append(term)

    ocean = frameworks.get('ocean', {})
    ocean_axis_map = {
        'openness': {'ruleFollowing': lambda v: 'flexible' if v > 0.6 else 'strict' if v < 0.4 else 'principled',
                     'riskAppetite': lambda v: 'bold' if v > 0.6 else 'conservative' if v < 0.4 else 'measured'},
        'extraversion': {'socialOrient': lambda v: 'collaborative' if v > 0.6 else 'independent' if v < 0.4 else 'semi-autonomous'},
        'agreeableness': {'conflictMode': lambda v: 'accommodating' if v > 0.6 else 'competing' if v < 0.4 else 'compromising'},
    }
    for dim, mappings in ocean_axis_map.items():
        val = ocean.get(dim)
        if val is not None:
            for axis, fn in mappings.items():
                axis_votes[axis].append(fn(val))

    disposition = {}
    for axis, votes in axis_votes.items():
        if votes:
            from collections import Counter
            counts = Counter(votes)
            winner = counts.most_common(1)[0][0]
            disposition[axis] = {
                'score': 0.5,
                'term': winner,
                'justification': f"Consensus from {len(votes)} framework(s)"
            }
        else:
            disposition[axis] = {'score': 0.5, 'term': 'moderate', 'justification': 'No framework data'}

    return disposition


def extract_personality(client, subject_name: str, context: str,
                        schema: dict, is_famous: bool = True) -> dict:
    fw_prompt = build_frameworks_prompt(schema, context)
    arch_prompt = build_archetypes_prompt(schema, context)

    results = {}
    with ThreadPoolExecutor(max_workers=2) as pool:
        fw_future = pool.submit(call_llm, client, fw_prompt, context, 'frameworks')
        arch_future = pool.submit(call_llm, client, arch_prompt, context, 'archetypes')
        results['frameworks'] = fw_future.result()
        results['archetypes'] = arch_future.result()

    fw_result = results.get('frameworks', {})
    arch_result = results.get('archetypes', {})

    combined = {}
    if 'frameworks' in fw_result:
        combined['frameworks'] = fw_result['frameworks']
    elif fw_result:
        combined['frameworks'] = fw_result

    if 'archetypes' in arch_result:
        combined['archetypes'] = arch_result['archetypes']
    elif arch_result:
        combined['archetypes'] = arch_result.get('archetypes', [])

    published = fw_result.get('published_assessments', [])

    validated, warnings = validate_output(combined, schema)
    for w in warnings:
        print(f"  Validation warning: {w}", file=sys.stderr)

    inconsistencies = check_consistency(validated, schema)
    if inconsistencies:
        for inc in inconsistencies:
            print(f"  Inconsistency: {inc['description']}", file=sys.stderr)

    validated['disposition'] = derive_disposition(validated, schema)
    validated['subject'] = subject_name
    validated['inconsistencies'] = [i['description'] for i in inconsistencies]

    if published:
        validated['reconciliation'] = {
            'published_sources': [f"{p['published_value']} ({p.get('source_description', '?')})"
                                  for p in published],
            'disagreements': []
        }

    return validated


def main():
    parser = argparse.ArgumentParser(description='Extract personality profile from biography')
    parser.add_argument('enriched_json', help='Path to enriched.json')
    parser.add_argument('structure_json', help='Path to structure.json')
    parser.add_argument('--schema', required=True, help='Path to personality-vocab.json')
    parser.add_argument('--subject', required=True, help='Primary subject name')
    args = parser.parse_args()

    schema = load_vocab_schema(args.schema)

    with open(args.enriched_json) as f:
        enriched = json.load(f)
    with open(args.structure_json) as f:
        structure = json.load(f)

    entities = structure.get('entities', [])
    edges = structure.get('edges', [])
    memories = enriched.get('memories', [])

    context = build_context_for_subject(args.subject, entities, edges, memories)

    client = create_client()
    print(f"  Extracting personality profile for {args.subject}...")
    start = time.time()

    profile = extract_personality(client, args.subject, context, schema)

    elapsed = time.time() - start
    print(f"  Personality extraction complete in {elapsed:.1f}s")

    fw = profile.get('frameworks', {})
    print(f"  MBTI: {fw.get('mbti', {}).get('type', '?')}")
    print(f"  Enneagram: {fw.get('enneagram', {}).get('type', '?')}")
    archetypes = profile.get('archetypes', [])
    if archetypes:
        primary = archetypes[0] if archetypes else {}
        print(f"  Primary archetype: {primary.get('family', '?')}/{primary.get('sub_archetype', '?')}")

    enriched['personality_profile'] = profile
    if 'disposition_profile' in enriched:
        del enriched['disposition_profile']

    atomic_write(args.enriched_json, enriched)
    print(f"  Written to {args.enriched_json}")


if __name__ == '__main__':
    main()
