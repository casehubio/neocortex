#!/usr/bin/env python3
"""Step 5b: Entity-level enrichment — Phase 2b.

Decomposes enrichment into 3 independent LLM calls per entity:
  1. Drives + CAPS — drive baselines and activation patterns
  2. Goals + Relationships — goal coherence and stage transitions
  3. Reflections + Mood + Disposition — synthesised insights and affect

Input:  enriched.json (Phase 2a output)
Output: enriched.json (updated in-place with Phase 2b additions)

Usage:
    python3 enrich_entity.py <enriched.json> <structure.json> [--subject "Subject Name"]
"""

import argparse
import json
import os
import sys
import time

from llm_client import create_client, model_id
from pipeline_state import atomic_write, clamp_confidence

DRIVES_CAPS_SYSTEM = """You are a cognitive psychologist assessing an entity's motivational drives and situational activation patterns from biographical data.

### 1. Drive Profile (4 axes)
For each axis, provide a BASELINE level (not a delta) and justification:
- curiosity: baseline 0.0-1.0 + justification
- competence: baseline 0.0-1.0 + justification
- affiliation: baseline 0.0-1.0 + justification
- autonomy: baseline 0.0-1.0 + justification

### 2. CAPS Patterns
Identify recurring activation patterns: what situations consistently trigger what responses?

Each pattern MUST include:
- pattern: narrative description "trigger → mediating → RESPONSE"
- frequency: how often this pattern fires (1-5)
- confidence: how certain the pattern is (0.0-1.0)
- input_activations: a map of CAPS input node IDs to activation levels (0.0-1.0)

Valid input node IDs (use ONLY these):
  relationship: secure_attachment, inconsistent_care, rejection, betrayal, abandonment, neglect
  threat: physical_threat, social_threat, psychological_threat, unpredictable_danger
  achievement: mastery, failure, performance_judged, competence_recognition
  social: acceptance, exclusion, dominance, submission, modeling_observed
  resource: scarcity, abundance, competition, sharing
  autonomy: agency_granted, controlled, choice_available, powerlessness
  consequence: reward, punishment, reward_removal, aversive_removal

Format: [{"pattern": "betrayal → trust_violation → AUTONOMY_ASSERTION", "frequency": 3, "confidence": 0.87, "input_activations": {"betrayal": 0.9, "abandonment": 0.6}}]

Respond with JSON only: {"drive_baselines": {...}, "caps_patterns": [...]}"""

GOALS_RELATIONS_SYSTEM = """You are a cognitive psychologist assessing an entity's goal system and relationship dynamics from biographical data.

### 1. Goal Coherence
Assess the goal system: summary, conflicts between goals, synergies between goals.
Format: {"summary": "...", "conflicts": [{"goals": ["A", "B"], "nature": "..."}], "synergies": [{"goals": ["A", "B"], "nature": "..."}]}

### 2. Relationship Stage Transitions
For key relationships, identify stage transitions across time:
Format: [{"other_agent": "a close associate", "transitions": [{"from": "passionate", "to": "estranged", "period": "YYYY", "trigger": "betrayal"}]}]

Respond with JSON only: {"goal_coherence": {...}, "relationship_stages": [...]}"""

REFLECTIONS_MOOD_SYSTEM = """You are a cognitive psychologist synthesising reflections, mood patterns, and behavioural tendencies from biographical data.

### 1. Reflections (synthesised insights)
Generate 2-5 reflection memories: insights that emerge from the PATTERN of experiences, not from any single event.
Format: [{"text": "...", "level": 1, "source_memory_indices": [0, 5, 12]}]

### 2. Mood Snapshots
For each major life period, provide a PAD mood snapshot:
Format: [{"period": "1920-1925", "pleasure": 0.3, "arousal": 0.5, "dominance": 0.6, "context": "..."}]

### 3. Behavioural Attractors
What recurring approach/avoidance patterns exist? What situations trigger consistent responses?
Format: [{"name": "defiance-through-art", "description": "...", "trigger": "...", "response": "...", "strength": 0.8}]

{disposition_section}

Respond with JSON only: {response_format}"""

DISPOSITION_SECTION = """### 4. Disposition Profile (PRIMARY SUBJECT ONLY)
Infer position on each of 5 disposition axes:
- socialOrient: 0.0 (introvert) to 1.0 (extrovert) + justification
- ruleFollowing: 0.0 (flexible) to 1.0 (strict) + justification
- riskAppetite: 0.0 (cautious) to 1.0 (bold) + justification
- autonomy: 0.0 (dependent) to 1.0 (independent) + justification
- conflictMode: 0.0 (avoidant) to 1.0 (confrontational) + justification"""


def gather_entity_context(entity_name: str, entities: list, edges: list, memories: list, max_memories: int = 20):
    """Gather full context for an entity: edges, related memories, neighbourhood."""
    name_lower = entity_name.lower()

    entity_edges = [e for e in edges if e.get('source', '').lower() == name_lower or e.get('target', '').lower() == name_lower]

    related_mems = []
    for i, mem in enumerate(memories):
        related = [r.lower() for r in mem.get('related_entities', [])]
        if name_lower in related or mem.get('subject', '').lower() == name_lower:
            related_mems.append((i, mem))

    neighbours = set()
    for e in entity_edges:
        neighbours.add(e.get('source', ''))
        neighbours.add(e.get('target', ''))
    neighbours.discard(entity_name)

    if len(related_mems) > max_memories:
        related_mems.sort(key=lambda x: abs(x[1].get('cognitive', {}).get('pad', {}).get('pleasure', 0) or 0), reverse=True)
        top_mems = related_mems[:max_memories]
        summary = {
            'total': len(related_mems),
            'shown': max_memories,
            'domains': {},
        }
        for _, m in related_mems:
            d = m.get('domain', 'experience')
            summary['domains'][d] = summary['domains'].get(d, 0) + 1
        return entity_edges, top_mems, list(neighbours), summary
    return entity_edges, related_mems, list(neighbours), None


def build_context_prompt(entity_name, entity, entity_edges, related_mems, neighbours, summary):
    """Build the shared context block used by all chunk calls."""
    mem_text = '\n'.join(
        f"  [{i}] ({m.get('domain', 'experience')}/{m.get('event_type', '?')}) {m['text'][:100]}..."
        for i, m in related_mems[:20]
    )
    edge_text = '\n'.join(
        f"  {e['source']} --[{e['type']}]--> {e['target']}"
        for e in entity_edges[:50]
    )
    return f"""Entity: {entity_name} ({entity.get('type', entity.get('subgraph_type', '?'))})

Edges ({len(entity_edges)} total):
{edge_text if edge_text else '  (none)'}

Related memories ({len(related_mems)} total{f', showing top {min(20, len(related_mems))}' if summary else ''}):
{mem_text if mem_text else '  (none)'}
{f'Summary of remaining: {json.dumps(summary)}' if summary else ''}

Neighbours: {', '.join(neighbours[:20])}"""


def call_llm(client, system_prompt, user_prompt, chunk_name, entity_name):
    """Make a single focused LLM call and parse JSON response."""
    try:
        response = client.messages.create(
            model=model_id(),
            max_tokens=4096,
            system=system_prompt,
            messages=[{'role': 'user', 'content': user_prompt}],
        )
        text = response.content[0].text
        start = text.find('{')
        end = text.rfind('}') + 1
        if start >= 0 and end > start:
            return json.loads(text[start:end])
    except Exception as e:
        print(f"    Warning: {chunk_name} failed for {entity_name}: {e}", file=sys.stderr)
    return {}


def enrich_entity(client, entity_name, entity, edges, memories, is_primary):
    """Run Phase 2b enrichment via 3 independent LLM calls."""
    entity_edges, related_mems, neighbours, summary = gather_entity_context(
        entity_name, [], edges, memories)
    context = build_context_prompt(entity_name, entity, entity_edges, related_mems, neighbours, summary)

    drives = call_llm(client, DRIVES_CAPS_SYSTEM, context, 'drives+caps', entity_name)
    goals = call_llm(client, GOALS_RELATIONS_SYSTEM, context, 'goals+relationships', entity_name)

    disp_section = DISPOSITION_SECTION if is_primary else ''
    resp_format = '{"reflections": [...], "mood_snapshots": [...], "behavioural_attractors": [...], "disposition_profile": {...}}' if is_primary else '{"reflections": [...], "mood_snapshots": [...], "behavioural_attractors": [...]}'
    reflect_system = REFLECTIONS_MOOD_SYSTEM.replace('{disposition_section}', disp_section).replace('{response_format}', resp_format)
    reflect = call_llm(client, reflect_system, context, 'reflections+mood', entity_name)

    merged = {}
    merged.update(drives)
    merged.update(goals)
    merged.update(reflect)
    return merged if merged else None


def identify_central_entities(entities: list, edges: list, memories: list, top_pct: float = 0.15) -> list[str]:
    """Identify Central-tier entities by composite prominence score."""
    scores = {}
    for ent in entities:
        name = ent['name']
        name_lower = name.lower()
        edge_count = sum(1 for e in edges if e.get('source', '').lower() == name_lower or e.get('target', '').lower() == name_lower)
        mem_count = sum(1 for m in memories if name_lower in [r.lower() for r in m.get('related_entities', [])] or m.get('subject', '').lower() == name_lower)
        mention_count = len(ent.get('source_passages', []))
        scores[name] = mention_count * 1.0 + edge_count * 2.0 + mem_count * 1.5

    sorted_entities = sorted(scores.items(), key=lambda x: x[1], reverse=True)
    cutoff = max(1, int(len(sorted_entities) * top_pct))
    return [name for name, _ in sorted_entities[:cutoff]]


def has_new_data(entity_name: str, memories: list, last_synthesis_pass: int) -> bool:
    """Check if entity has new memories since its last synthesis pass."""
    name_lower = entity_name.lower()
    for mem in memories:
        pass_str = mem.get('extraction_pass', 'phase-1-structural')
        if pass_str.startswith('convergence-pass-'):
            mem_pass = int(pass_str.split('-')[-1])
            if mem_pass > last_synthesis_pass:
                conf = mem.get('confidence', {}).get('value', 1.0)
                if conf > 0.4:
                    related = [r.lower() for r in mem.get('related_entities', [])]
                    if name_lower in related or mem.get('subject', '').lower() == name_lower:
                        return True
    return False


def main():
    parser = argparse.ArgumentParser(description='Phase 2b entity-level enrichment')
    parser.add_argument('enriched', help='Path to enriched.json (updated in-place)')
    parser.add_argument('structure', help='Path to structure.json')
    parser.add_argument('--subject', default=None, help='Primary subject name (auto-detected if omitted)')
    parser.add_argument('--incremental', action='store_true', help='Only re-synthesize entities with new data')
    parser.add_argument('--pass-num', type=int, default=1, help='Convergence pass number')
    parser.add_argument('--mode', choices=['fixed-point', 'selective'], default='fixed-point', help='Convergence mode')
    args = parser.parse_args()

    client = create_client()

    with open(args.enriched, 'r') as f:
        enriched = json.load(f)
    with open(args.structure, 'r') as f:
        structure = json.load(f)

    entities = enriched.get('entities', structure.get('entities', []))
    edges = enriched.get('edges', structure.get('edges', []))
    memories = enriched.get('memories', [])

    central = identify_central_entities(entities, edges, memories)

    if args.incremental:
        existing = enriched.get('entity_enrichments', {})
        filtered = []
        for name in central:
            prev = existing.get(name, {})
            last_pass = prev.get('synthesis_pass', 0)
            if has_new_data(name, memories, last_pass):
                filtered.append(name)
        print(f"  Incremental: {len(filtered)}/{len(central)} entities have new data")
        central = filtered

    primary_subject = args.subject or (central[0] if central else None)

    print(f"Phase 2b: {len(central)} Central-tier entities")
    print(f"Primary subject: {primary_subject}")

    entity_enrichments = {}
    reflections = []
    mood_snapshots = []
    disposition_profile = None

    for i, entity_name in enumerate(central):
        entity = next((e for e in entities if e['name'] == entity_name), {})
        is_primary = entity_name == primary_subject

        print(f"  [{i+1}/{len(central)}] {entity_name} {'(PRIMARY)' if is_primary else ''}")
        result = enrich_entity(client, entity_name, entity, edges, memories, is_primary)

        if result:
            entity_enrichments[entity_name] = {
                'drive_baselines': result.get('drive_baselines'),
                'caps_patterns': result.get('caps_patterns'),
                'goal_coherence': result.get('goal_coherence'),
                'behavioural_attractors': result.get('behavioural_attractors'),
                'relationship_stages': result.get('relationship_stages'),
                'synthesis_pass': args.pass_num,
            }

            for ref in result.get('reflections', []):
                if not isinstance(ref, dict):
                    continue
                ref['domain'] = 'reflection'
                ref['subject'] = entity_name
                ref['enrichment_version'] = 'v1.0-phase2b'
                ref['convergence_pass'] = args.pass_num
                if args.pass_num > 1:
                    ref['extraction_pass'] = f'convergence-pass-{args.pass_num}'
                else:
                    ref['extraction_pass'] = 'phase-2b-entity'
                if 'confidence' in ref:
                    ref['confidence'] = clamp_confidence(ref['confidence'], args.pass_num)
                reflections.append(ref)

            for mood in result.get('mood_snapshots', []):
                if not isinstance(mood, dict):
                    continue
                if 'text' not in mood:
                    period = mood.get('period', '?')
                    context = mood.get('context', '')
                    mood['text'] = f"Mood during {period}: {context}" if context else f"Mood during {period}"
                mood['domain'] = 'mood'
                mood['subject'] = entity_name
                mood['enrichment_version'] = 'v1.0-phase2b'
                mood['convergence_pass'] = args.pass_num
                if args.pass_num > 1:
                    mood['extraction_pass'] = f'convergence-pass-{args.pass_num}'
                else:
                    mood['extraction_pass'] = 'phase-2b-entity'
                if 'confidence' in mood:
                    mood['confidence'] = clamp_confidence(mood['confidence'], args.pass_num)
                mood_snapshots.append(mood)

            if is_primary and result.get('disposition_profile'):
                disposition_profile = result['disposition_profile']

        time.sleep(0.3)

        enriched['entity_enrichments'] = {**enriched.get('entity_enrichments', {}), **entity_enrichments}
        enriched['phase2b_reflections'] = enriched.get('phase2b_reflections', []) + reflections
        enriched['phase2b_mood_snapshots'] = enriched.get('phase2b_mood_snapshots', []) + mood_snapshots
        if disposition_profile:
            enriched['disposition_profile'] = disposition_profile
        atomic_write(args.enriched, enriched)
        reflections = []
        mood_snapshots = []

    if args.mode == 'selective':
        new_count = sum(1 for r in enriched.get('phase2b_reflections', []) if r.get('convergence_pass') == args.pass_num)
    else:
        new_count = sum(1 for r in enriched.get('phase2b_reflections', []) if r.get('convergence_pass') == args.pass_num) + sum(1 for m in enriched.get('phase2b_mood_snapshots', []) if m.get('convergence_pass') == args.pass_num)

    total_reflections = len(enriched.get('phase2b_reflections', []))
    total_moods = len(enriched.get('phase2b_mood_snapshots', []))

    print(f"\nPhase 2b complete:")
    print(f"  Entities enriched: {len(entity_enrichments)}/{len(central)}")
    print(f"  Reflections generated (total): {total_reflections}")
    print(f"  Mood snapshots (total): {total_moods}")
    print(f"  Disposition profile: {'derived' if disposition_profile else 'NOT derived'}")
    print(f"  NEW_MEMORIES={new_count}")
    print(f"Output: {args.enriched} (updated in-place)")


if __name__ == '__main__':
    main()
