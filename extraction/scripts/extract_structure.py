#!/usr/bin/env python3
"""Step 4: LLM structural extraction — entities, edges, memories with provenance.

Decomposes extraction into 3 independent LLM calls per passage:
  1. Entities — confirm/reclassify NLP candidates, add goals
  2. Edges — extract relationships between entities
  3. Memories — extract domain-classified memories

Each call produces small, focused JSON. Results are merged per passage.

Input:  passages.json + nlp-baseline.json + gap-sweep.json
Output: structure.json — nodes, edges, memories ready for import

Usage:
    python3 extract_structure.py <passages.json> <nlp-baseline.json> [--gaps gap-sweep.json] [--output structure.json]
"""

import argparse
import json
import os
import sys
import time

from llm_client import create_client, model_id
from coverage import load_registry, check_node_coverage, check_memory_coverage, format_report
from pipeline_state import atomic_write, clamp_confidence

ENTITY_SYSTEM = """You are a biographical knowledge graph builder. Extract ENTITIES from the passage.

For each entity, produce:
- name: canonical name
- type: person | place | activity | goal | project | concept | organisation
- properties: key-value pairs (e.g., birth_date, nationality, role)
- traits: list of applicable trait interfaces per type:
  * person → ["Personable"] (fill: role, occupation)
  * place → ["Locatable"] (fill: city, country, latitude, longitude if known)
  * activity → ["Eventlike", "Temporal"] (fill: date, duration, start-date, end-date)
  * goal → ["Goallike"] (fill: horizon, priority, status, urgency, feasibility, target-date, need-tier)
  * project → ["Projectlike"] (fill: status, deadline, description)
  * organisation → ["Organisational"] (fill: industry, size, founded)
- trait_properties: key-value pairs for the trait fields listed above
- pad: emotional dimensions for this entity as experienced by the subject: {"pleasure": -1..1, "arousal": -1..1, "dominance": -1..1}. Captures the subject's felt affect toward/about this entity. REQUIRED for ALL entity types — persons, places, activities, projects, concepts, organisations. Every entity carries affective significance: a place can feel safe or threatening, an activity joyful or dreaded, a concept inspiring or oppressive.
- confidence: {"origin": "STATED|INFERRED|SPECULATED", "value": 0.0-1.0}
- valid_from: ISO date or partial date when this entity first appears or is relevant. REQUIRED for persons (birth), places (founding/first mention), organisations (founding). Use partial dates (YYYY or YYYY-MM) when exact date unknown.
- valid_until: ISO date or partial date when this entity ceases to be relevant (death, demolition, dissolution). Omit if entity is still active at end of passage.
- lifecycle: list of dated milestones in the entity's history. REQUIRED — every entity should have at least one lifecycle event. Each event has:
  * event: semantic label from the standard set below
  * date: ISO 8601 partial date (YYYY, YYYY-MM, or YYYY-MM-DD). Postfix ~ for approximate, ? for uncertain (e.g., "1939~" not "~1939").
  * context: optional additional context (location, agent, circumstance)

Standard lifecycle labels by entity type:
  * person: born, died
  * project: conceived, started, completed, first-exhibited, published, sold, destroyed, lost
  * place: founded, built, renamed, demolished, opened-as-museum
  * organisation: founded, merged, dissolved
  * activity/event: occurred, started, ended
  * concept: coined, popularised

Use these standard labels when they apply. Use ad-hoc labels (e.g., "acquired", "restored") when no standard label fits.
Example: {"event": "completed", "date": "1939", "context": "Mexico City"}

Extract ALL entity types thoroughly — persons, places, organisations, projects, concepts, activities.
Do not skip any entity in favor of another type. Every named entity in the passage should appear.

Respond with JSON only: {"entities": [...]}"""

EDGE_SYSTEM = """You are a biographical knowledge graph builder. Extract RELATIONSHIPS (edges) between entities in the passage.

You will be given a list of extracted entities — use ONLY these entity names as source/target. Connect every pair of entities that have a relationship in the passage. Be thorough: if two entities appear in the same context, there is likely a relationship.

For each edge:
- source: entity name (must match an extracted entity)
- target: entity name (must match an extracted entity)
- type: relationship type from vocabulary:
  Kinship: child-of, sibling-of, parent-of
  Romantic: married-to, affair-with, divorced-from
  Professional: treated-by, taught-by, championed, collaborated-with
  Goal: pursues, enables, blocks, requires, contributes-to, decomposes-into
  Spatial: lives-at, located-in, at
  Creative: inspired-by, created-by, depicted-in
  Conceptual: associated-with, influenced-by
  Activity: participated, occasion
  Temporal: preceded-by, concurrent-with, succeeded-by
  Causal: caused-by, resulted-in, motivated-by
- confidence_origin: STATED | INFERRED
- confidence_value: 0.0-1.0 (STATED edges typically 0.85-1.0, INFERRED 0.5-0.8)
- validation_tier: REGISTERED (from vocabulary above) | UNVALIDATED (ad-hoc type)

Extract edges aggressively — the graph needs rich connectivity. For every entity pair that co-occurs in the passage, consider whether a relationship exists. Include spatial relationships (person lives-at place), creative relationships (person created-by project), and goal relationships (person pursues goal).

Respond with JSON only: {"edges": [...]}"""

GOAL_SYSTEM = """You are a biographical knowledge graph builder. Extract GOALS from the passage.

Goals are aspirations, objectives, and driving motivations — explicit or implicit. Extract at three tiers:
- thematic: life-level recurring themes (artistic independence, cultural identity, physical resilience)
- strategic: multi-year directional goals implicit in sustained actions
- tactical: specific achievable objectives with clear targets

For each goal, produce:
- name: concise goal description
- type: "goal"
- goal_tier: thematic | strategic | tactical
- goal_horizon: immediate | short | medium | long | aspirational
- goal_status: active | blocked | completed | dormant | abandoned
- goal_urgency: 0.0-1.0
- goal_need_tier: SAFETY | TASKS | SOCIAL | SELF_EXPRESSION | UNDERSTANDING
- goal_priority: 0.0-1.0
- goal_feasibility: 0.0-1.0
- content_domain: one of: morality-and-virtue | religion-and-spirituality | self-actualisation | avoidance | social-relating | health | family | mastery-and-competence | financial-and-occupational
- content_subdomain: (optional, more specific) one of: social-values | personal-morals | help-others | highly-regarded | religion-and-spirituality | wisdom-and-serenity | self-knowledge-and-happiness | appreciating-beauty | exploration-and-passions | enjoy-life | avoid-stress-harm-rejection-conflict | avoid-hassles | relate-and-belong | intimacy | stability | power | leadership | health | family | achievement | self-efficacy | practical-competence | intellectual-competence | finances | occupational-success
- motivation_origin: intrinsic | identified | introjected | external
  * intrinsic: pursued for inherent satisfaction
  * identified: valued and personally endorsed
  * introjected: pursued to avoid guilt or gain approval
  * external: driven by external reward or punishment
- clarity: 0.0-1.0 (how well-defined the goal is — vague yearning=0.2, concrete plan=0.9)
- evidence_basis: quote or paraphrase the textual evidence supporting this goal (required unless confidence origin is SPECULATED)
- confidence: {"origin": "STATED|INFERRED|SPECULATED", "value": 0.0-1.0}
  * STATED 0.85-1.0: subject explicitly declares the goal
  * INFERRED 0.5-0.8: goal implied by sustained actions or choices
  * SPECULATED 0.3-0.5: analyst interpretation from indirect evidence

Extract aggressively — look for:
- Explicit aspirations ("she wanted to...", "her dream was...")
- Implicit goals from actions and choices (choosing art over medicine = goal of artistic career)
- Goals inferred from trade-offs and recurring patterns
- Goals from relationships (maintaining independence within marriage)

Respond with JSON only: {"entities": [...]}"""

MEMORY_SYSTEM = """You are a biographical knowledge graph builder. Extract MEMORIES from the passage — structured records of experiences, relationships, reflections, and moods.

For each memory:
- text: summarised memory (1-2 sentences capturing the key experience)
- domain: classify as one of:
  * "experience" — something the subject experienced, observed, did, or the outcome of an action
  * "relationship" — a change or event in a relationship between two people
  * "reflection" — a synthesised insight that goes beyond factual content
  * "mood" — a sustained emotional state during a period
- event_type: observation | action | outcome | formative-experience
- subject: entity this memory is about (use entity name)
- period: approximate date or year
- related_entities: other entities involved (list of names)
- extraction_method: "llm-extract"
- extraction_pass: "phase-1-structural"

FORMATIVE EXPERIENCE EXTRACTION — extract these aggressively.

A formative experience is any event that shaped the subject's personality, coping style,
creative direction, or worldview. These are NOT limited to childhood — they span all life
stages. Classify as event_type "formative-experience" when the passage describes:
- Illness, injury, or physical trauma (polio, accidents, surgeries, chronic pain)
- Loss or bereavement (death of loved ones, miscarriage, divorce)
- Mentorship or creative awakening (discovering a vocation, a teacher's influence)
- Trauma or abuse (physical, emotional, sexual)
- Cultural immersion or displacement (immigration, exile, cultural identity formation)
- Family dynamics that shaped personality (parental conflict, birth order effects, neglect)
- Social exclusion or inclusion (bullying, acceptance into a group, first recognition)
- Pivotal choices (choosing art over medicine, leaving a relationship, political commitment)
- Repeated patterns (recurring hospital stays, cyclical relationship dynamics)

For EVERY passage covering ages 0-25, at least one formative-experience memory is expected
unless the content is purely factual/administrative. For later life, classify events as
formative when they changed the subject's trajectory or self-concept.

For each formative-experience memory, ALSO include:
- developmental_period: childhood (0-12) | adolescence (13-17) | early_adulthood (18-30) | adulthood (30+)
- situation_types: comma-separated CAPS input node IDs from:
  trust_offered, trust_betrayed, intimacy_available, rejection, relationship_loss, social_support,
  criticism_received, social_threat, physical_threat, psychological_threat, abandonment_threat,
  achievement_opportunity, failure_experienced, skill_challenge, recognition_received,
  choice_available, choice_restricted, agency_granted, agency_denied, novelty_encountered
- salience_multiplier: 0.5-2.0 (traumatic=1.5-2.0, formative=1.0-1.5, routine=0.5-1.0)
- reinforcement_schedule: single | intermittent | continuous

When a passage describes a RELATIONSHIP change, create BOTH:
- An experience memory (domain: "experience") for what happened
- A relationship memory (domain: "relationship") with quality_signal (POSITIVE/NEGATIVE/NEUTRAL), other_agent, trust_trajectory (increasing/stable/decreasing)

Respond with JSON only: {"memories": [...]}"""


def build_entity_context(entities):
    """Format extracted entities as context text for the edge extraction prompt."""
    if not entities:
        return '  (none)'
    lines = []
    for ent in entities[:50]:
        name = ent.get('name', '?')
        etype = ent.get('type', '?')
        parts = [f'{name} ({etype}']
        if etype == 'goal':
            tier = ent.get('goal_tier')
            domain = ent.get('content_domain')
            if tier:
                parts.append(f', {tier}')
            if domain:
                parts.append(f', {domain}')
        parts.append(')')
        lines.append(f"  - {''.join(parts)}")
    return '\n'.join(lines)


def infer_cooccurrence_edges(entities, memories):
    """Infer associated-with edges from entity co-occurrence in memories."""
    entity_names = {e['name'].lower(): e['name'] for e in entities}
    seen_pairs = set()
    edges = []
    for mem in memories:
        subject = mem.get('subject')
        related = mem.get('related_entities', [])
        if not subject or not related:
            continue
        subj_lower = subject.lower()
        if subj_lower not in entity_names:
            continue
        canonical_subj = entity_names[subj_lower]
        for rel in related:
            rel_lower = rel.lower()
            if rel_lower not in entity_names or rel_lower == subj_lower:
                continue
            canonical_rel = entity_names[rel_lower]
            pair = tuple(sorted([canonical_subj, canonical_rel]))
            if pair in seen_pairs:
                continue
            seen_pairs.add(pair)
            edges.append({
                'source': canonical_subj,
                'target': canonical_rel,
                'type': 'associated-with',
                'confidence_origin': 'INFERRED',
                'validation_tier': 'UNVALIDATED',
                'extraction_method': 'cooccurrence-inference',
            })
    return edges


def call_llm(client, system_prompt, passage_text, candidates_text, chunk_name,
             entity_context=None):
    """Make a single focused LLM call and parse JSON response."""
    sections = [f"Passage:\n\n{passage_text}\n\n---"]
    sections.append(f"NLP candidate entities:\n{candidates_text if candidates_text else '  (none)'}")
    if entity_context is not None:
        sections.append(f"Extracted entities (use these as source/target for edges):\n{entity_context}")
    sections.append(f"Extract {chunk_name} from this passage.")
    user_prompt = '\n\n'.join(sections)

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
        print(f"    Warning: {chunk_name} failed: {e}", file=sys.stderr)
    return {}


def extract_passage(client, passage, candidates):
    """Extract structure from one passage: entities+goals first, then edges with entity context."""
    candidate_text = '\n'.join(
        f"  - {c['canonical']} ({c['type']}, {c['mentions']} mentions, tier: {c.get('depth_tier', '?')})"
        for c in candidates[:30]
    )
    text = passage['text']

    ent_result = call_llm(client, ENTITY_SYSTEM, text, candidate_text, 'entities')
    goal_result = call_llm(client, GOAL_SYSTEM, text, candidate_text, 'goals')

    all_entities = ent_result.get('entities', []) + goal_result.get('entities', [])
    entity_ctx = build_entity_context(all_entities)

    edge_result = call_llm(client, EDGE_SYSTEM, text, candidate_text, 'edges',
                           entity_context=entity_ctx)
    mem_result = call_llm(client, MEMORY_SYSTEM, text, candidate_text, 'memories')

    llm_edges = edge_result.get('edges', [])
    memories = mem_result.get('memories', [])
    cooccurrence_edges = infer_cooccurrence_edges(all_entities, memories)

    existing_pairs = {(e.get('source', '').lower(), e.get('target', '').lower())
                      for e in llm_edges}
    for ce in cooccurrence_edges:
        pair = (ce['source'].lower(), ce['target'].lower())
        reverse = (pair[1], pair[0])
        if pair not in existing_pairs and reverse not in existing_pairs:
            llm_edges.append(ce)
            existing_pairs.add(pair)

    return {
        'entities': all_entities,
        'edges': llm_edges,
        'memories': memories,
    }


CONVERGENCE_SYSTEM = """You are a biographical knowledge graph builder. Given synthesized insights about an entity and the existing graph context, identify NEW content not already captured.

For each piece of new content, produce ONE of:
1. NEW ENTITY (not in the known entities list):
   - name, type, properties, traits, confidence
2. NEW EDGE (not in the known edges list):
   - source, target, type, confidence_origin, validation_tier
3. NEW MEMORY (a distinct insight not captured by existing memories):
   - text, domain, event_type, subject, related_entities

IMPORTANT:
- Do NOT re-extract entities or edges that are already known
- Only produce genuinely new content implied by the synthesized insights
- If the insights contain no new extractable content, return empty arrays

Respond with JSON only: {"entities": [], "edges": [], "memories": []}"""


def call_convergence_llm(client, system_prompt, user_prompt, entity_name):
    """Make a single convergence extraction LLM call — direct prompt, no template."""
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
        print(f"    Warning: convergence extraction failed for {entity_name}: {e}", file=sys.stderr)
    return {}


def group_memories_by_entity(memories: list) -> dict[str, list]:
    groups = {}
    for mem in memories:
        if mem.get('structurally_extracted'):
            continue
        subject = mem.get('subject')
        if not subject:
            continue
        groups.setdefault(subject, []).append(mem)
    return groups


def build_convergence_prompt(entity_name: str, memories: list,
                              known_entities: list, known_edges: list) -> str:
    mem_text = '\n'.join(f'  [{i+1}] {m["text"]}' for i, m in enumerate(memories))
    ent_text = ', '.join(known_entities[:50])
    edge_text = '\n'.join(f'  {e}' for e in known_edges[:50])
    return f"""Entity: {entity_name}

Synthesized insights ({len(memories)} total):
{mem_text}

Known entities (do NOT re-extract these):
{ent_text}

Known edges (do NOT re-extract these):
{edge_text if edge_text else '  (none)'}

Identify any NEW entities, edges, or memories implied by these insights."""


def merge_extraction_results(existing: dict, new_results: dict, pass_n: int) -> dict:
    ent_by_key = {}
    for ent in existing.get('entities', []):
        ent_by_key[ent['name'].lower()] = ent

    for ent in new_results.get('entities', []):
        key = ent['name'].lower()
        if key in ent_by_key:
            for k, v in ent.items():
                if k not in ent_by_key[key]:
                    ent_by_key[key][k] = v
        else:
            ent['extraction_pass'] = f'convergence-pass-{pass_n}'
            ent['extraction_method'] = 'llm-convergence-extract'
            if 'confidence' in ent:
                ent['confidence'] = clamp_confidence(ent['confidence'], pass_n)
            ent_by_key[key] = ent

    edge_keys = set()
    merged_edges = []
    for edge in existing.get('edges', []):
        if not edge.get('source') or not edge.get('target') or not edge.get('type'):
            continue
        key = f"{edge['source'].lower()}|{edge['target'].lower()}|{edge['type']}"
        if key not in edge_keys:
            edge_keys.add(key)
            merged_edges.append(edge)

    for edge in new_results.get('edges', []):
        if not edge.get('source') or not edge.get('target') or not edge.get('type'):
            continue
        key = f"{edge['source'].lower()}|{edge['target'].lower()}|{edge['type']}"
        if key not in edge_keys:
            edge_keys.add(key)
            edge['extraction_pass'] = f'convergence-pass-{pass_n}'
            merged_edges.append(edge)

    merged_memories = list(existing.get('memories', []))
    for mem in new_results.get('memories', []):
        mem['extraction_pass'] = f'convergence-pass-{pass_n}'
        mem['extraction_method'] = 'llm-convergence-extract'
        mem['convergence_pass'] = pass_n
        if 'confidence' in mem:
            mem['confidence'] = clamp_confidence(mem['confidence'], pass_n)
        merged_memories.append(mem)

    return {
        'entities': list(ent_by_key.values()),
        'edges': merged_edges,
        'memories': merged_memories,
    }


def run_convergence(args):
    """Convergence mode: extract from generated memories, merge into structure."""
    client = create_client()

    with open(args.convergence, 'r') as f:
        enriched = json.load(f)
    with open(args.structure_base, 'r') as f:
        structure = json.load(f)

    all_generated = []
    for mem in enriched.get('phase2b_reflections', []):
        if isinstance(mem, dict) and mem.get('text'):
            all_generated.append(mem)
    for mem in enriched.get('phase2b_mood_snapshots', []):
        if isinstance(mem, dict) and mem.get('text'):
            all_generated.append(mem)

    groups = group_memories_by_entity(all_generated)
    if not groups:
        print("  No unextracted generated memories — nothing to do")
        print("NEW_ENTITIES=0")
        print("NEW_EDGES=0")
        print("NEW_MEMORIES=0")
        return

    known_entities = [e['name'] for e in structure.get('entities', [])]
    known_edges = [f"{e['source']}|{e['target']}|{e['type']}" for e in structure.get('edges', []) if e.get('source') and e.get('target') and e.get('type')]

    total_new_entities = 0
    total_new_edges = 0
    total_new_memories = 0

    for i, (entity_name, mems) in enumerate(groups.items()):
        print(f"  [{i+1}/{len(groups)}] {entity_name}: {len(mems)} generated memories")
        prompt = build_convergence_prompt(entity_name, mems, known_entities, known_edges)

        result = call_convergence_llm(client, CONVERGENCE_SYSTEM, prompt, entity_name)
        if not result:
            result = {'entities': [], 'edges': [], 'memories': []}

        new_ent = len(result.get('entities', []))
        new_edg = len(result.get('edges', []))
        new_mem = len(result.get('memories', []))
        print(f"    -> {new_ent} entities, {new_edg} edges, {new_mem} memories")
        total_new_entities += new_ent
        total_new_edges += new_edg
        total_new_memories += new_mem

        structure = merge_extraction_results(structure, result, args.pass_num)

        for mem in mems:
            mem['structurally_extracted'] = True
            mem['extraction_pass'] = f'convergence-pass-{args.pass_num}'

        structure['stats'] = {
            'entities': len(structure['entities']),
            'edges': len(structure['edges']),
            'memories': len(structure['memories']),
        }
        atomic_write(args.output, structure)
        atomic_write(args.convergence, enriched)

        known_entities = [e['name'] for e in structure['entities']]
        known_edges = [f"{e['source']}|{e['target']}|{e['type']}" for e in structure['edges'] if e.get('source') and e.get('target') and e.get('type')]

        time.sleep(0.2)

    print(f"\nConvergence extraction complete:")
    print(f"  NEW_ENTITIES={total_new_entities}")
    print(f"  NEW_EDGES={total_new_edges}")
    print(f"  NEW_MEMORIES={total_new_memories}")


FIRST_EXISTENCE_LABELS = {
    'person': 'born', 'project': 'conceived', 'place': 'founded',
    'organisation': 'founded', 'activity': 'occurred', 'concept': 'coined',
}
LAST_EXISTENCE_LABELS = {
    'person': 'died', 'project': 'destroyed', 'place': 'demolished',
    'organisation': 'dissolved', 'activity': 'ended',
}


def derive_temporal_from_lifecycle(entities: dict):
    """Derive valid_from/valid_until from lifecycle events when not explicitly set."""
    for ent in entities.values():
        lifecycle = ent.get('lifecycle', [])
        if not lifecycle:
            continue
        etype = ent.get('type', 'concept')
        first_label = FIRST_EXISTENCE_LABELS.get(etype)
        last_label = LAST_EXISTENCE_LABELS.get(etype)
        if not ent.get('valid_from') and first_label:
            for lc in lifecycle:
                if lc.get('event') == first_label and lc.get('date'):
                    ent['valid_from'] = lc['date']
                    break
        if not ent.get('valid_from') and lifecycle:
            dates = sorted([lc['date'] for lc in lifecycle if lc.get('date')])
            if dates:
                ent['valid_from'] = dates[0]
        if not ent.get('valid_until') and last_label:
            for lc in lifecycle:
                if lc.get('event') == last_label and lc.get('date'):
                    ent['valid_until'] = lc['date']
                    break


def main():
    parser = argparse.ArgumentParser(description='LLM structural extraction')
    parser.add_argument('passages', nargs='?', help='Path to passages.json')
    parser.add_argument('baseline', nargs='?', help='Path to nlp-baseline.json')
    parser.add_argument('--gaps', help='Path to gap-sweep.json')
    parser.add_argument('--output', '-o', default='structure.json', help='Output JSON file')
    parser.add_argument('--convergence', help='Path to enriched.json for convergence extraction')
    parser.add_argument('--structure-base', help='Path to existing structure.json to merge into')
    parser.add_argument('--pass-num', type=int, default=1, help='Convergence pass number')
    parser.add_argument('--limit', type=int, default=0, help='Process only first N passages (0=all)')
    args = parser.parse_args()

    if args.convergence:
        if not args.structure_base:
            parser.error('--convergence requires --structure-base')
        run_convergence(args)
        return

    if not args.passages or not args.baseline:
        parser.error('Provide passages + baseline, or use --convergence mode')

    client = create_client()

    with open(args.passages, 'r') as f:
        passages_data = json.load(f)
    with open(args.baseline, 'r') as f:
        baseline = json.load(f)

    gap_entities = []
    if args.gaps and os.path.exists(args.gaps):
        with open(args.gaps, 'r') as f:
            gap_entities = json.load(f).get('new_entities', [])

    passages = passages_data['passages']
    entities_by_passage = {}
    for key, ent in baseline.get('entities', {}).items():
        for pid in ent.get('passages', []):
            entities_by_passage.setdefault(pid, []).append(ent)

    all_entities = {}
    all_edges = []
    all_memories = []
    collected_edge_types = set()
    edge_dedup = {}

    processed_passages = set()
    if os.path.exists(args.output):
        try:
            with open(args.output) as f:
                existing_output = json.load(f)
            for ent in existing_output.get('entities', []):
                key = ent['name'].lower()
                all_entities[key] = ent
                for pid in ent.get('source_passages', []):
                    processed_passages.add(pid)
            all_edges = existing_output.get('edges', [])
            all_memories = existing_output.get('memories', [])
            for e in all_edges:
                if not e.get('source') or not e.get('target') or not e.get('type'):
                    continue
                ekey = f"{e['source'].lower()}|{e['target'].lower()}|{e['type']}"
                edge_dedup[ekey] = e
                collected_edge_types.add(e['type'])
            if processed_passages:
                print(f"  Resuming: {len(processed_passages)} passages already processed")
        except (json.JSONDecodeError, IOError):
            pass

    if args.limit > 0:
        passages = passages[:args.limit]
        print(f"  --limit {args.limit}: processing first {len(passages)} passages only")

    for i, passage in enumerate(passages):
        if passage['id'] in processed_passages:
            print(f"  [{i+1}/{len(passages)}] {passage['id']}: SKIPPED (already processed)")
            continue

        candidates = entities_by_passage.get(passage['id'], [])
        passage_gaps = [g for g in gap_entities if g.get('source_passage') == passage['id']]
        for g in passage_gaps:
            candidates.append({
                'canonical': g['name'],
                'type': g['type'],
                'mentions': 1,
                'depth_tier': 'gap-sweep',
            })

        print(f"  [{i+1}/{len(passages)}] {passage['id']}: {passage['section'][:40]}... ({len(candidates)} candidates)")
        result = extract_passage(client, passage, candidates)

        ent_count = len(result.get('entities', []))
        edge_count = len(result.get('edges', []))
        mem_count = len(result.get('memories', []))
        print(f"    → {ent_count} entities, {edge_count} edges, {mem_count} memories")

        for ent in result.get('entities', []):
            key = ent['name'].lower()
            if key not in all_entities:
                all_entities[key] = ent
                all_entities[key]['source_passages'] = []
            all_entities[key]['source_passages'].append(passage['id'])
            if 'traits' not in all_entities[key] and 'traits' in ent:
                all_entities[key]['traits'] = ent['traits']
            if 'trait_properties' not in all_entities[key] and 'trait_properties' in ent:
                all_entities[key]['trait_properties'] = ent['trait_properties']
            if 'confidence' not in all_entities[key] and 'confidence' in ent:
                all_entities[key]['confidence'] = ent['confidence']
            if 'pad' not in all_entities[key] and 'pad' in ent:
                all_entities[key]['pad'] = ent['pad']
            if 'valid_from' not in all_entities[key] and 'valid_from' in ent:
                all_entities[key]['valid_from'] = ent['valid_from']
            if 'valid_until' not in all_entities[key] and 'valid_until' in ent:
                all_entities[key]['valid_until'] = ent['valid_until']
            if 'lifecycle' in ent:
                existing_lc = all_entities[key].get('lifecycle', [])
                existing_events = {e['event'] for e in existing_lc}
                for lc in ent['lifecycle']:
                    if lc.get('event') not in existing_events:
                        existing_lc.append(lc)
                        existing_events.add(lc['event'])
                all_entities[key]['lifecycle'] = existing_lc
            for gk in ('goal_tier', 'goal_horizon', 'goal_status', 'goal_urgency',
                        'goal_need_tier', 'goal_priority', 'goal_feasibility',
                        'content_domain', 'content_subdomain', 'motivation_origin',
                        'clarity', 'evidence_basis'):
                if gk in ent and gk not in all_entities[key]:
                    all_entities[key][gk] = ent[gk]

        for edge in result.get('edges', []):
            edge['source_passage'] = passage['id']
            if 'confidence_origin' not in edge:
                edge['confidence_origin'] = 'INFERRED'
            if 'validation_tier' not in edge:
                edge['validation_tier'] = 'UNVALIDATED'
            collected_edge_types.add(edge['type'])

        for mem in result.get('memories', []):
            mem['source_passage'] = passage['id']
            mem['source_start'] = passage['start']
            mem['source_end'] = passage['end']
            mem['enrichment_version'] = 'v1.0'
            if 'domain' not in mem:
                mem['domain'] = 'experience'
            if 'extraction_method' not in mem:
                mem['extraction_method'] = 'llm-extract'
            if 'extraction_pass' not in mem:
                mem['extraction_pass'] = 'phase-1-structural'
            all_memories.append(mem)

        for e in result.get('edges', []):
            if not e.get('source') or not e.get('target') or not e.get('type'):
                continue
            ekey = f"{e['source'].lower()}|{e['target'].lower()}|{e['type']}"
            if ekey not in edge_dedup:
                edge_dedup[ekey] = e

        interim = {
            'stats': {'entities': len(all_entities), 'edges': len(edge_dedup),
                      'memories': len(all_memories), 'passages_processed': i + 1},
            'entities': list(all_entities.values()),
            'edges': list(edge_dedup.values()),
            'memories': all_memories,
        }
        atomic_write(args.output, interim)

        time.sleep(0.2)

    derive_temporal_from_lifecycle(all_entities)

    registry_path = os.path.join(os.path.dirname(__file__), '..', 'docs', 'field-registry.yaml')
    entity_list = list(all_entities.values())
    registry = load_registry(registry_path)
    node_report = check_node_coverage(entity_list, registry, stage='extraction')
    mem_report = check_memory_coverage(all_memories, registry, stage='extraction')

    output = {
        'stats': {
            'entities': len(all_entities),
            'edges': len(edge_dedup),
            'memories': len(all_memories),
            'passages_processed': len(passages),
            'edge_types': sorted(collected_edge_types),
        },
        'entities': entity_list,
        'edges': list(edge_dedup.values()),
        'memories': all_memories,
        'coverage': {
            'node_fields': {'populated': node_report.populated, 'total': node_report.total,
                            'pct': node_report.pct, 'gate': node_report.gate},
            'memory_fields': {'populated': mem_report.populated, 'total': mem_report.total,
                              'pct': mem_report.pct, 'gate': mem_report.gate},
        },
    }

    atomic_write(args.output, output)

    print(f"\nExtraction complete:")
    print(f"  Entities: {len(all_entities)}")
    print(f"  Edges: {len(edge_dedup)}")
    print(f"  Memories: {len(all_memories)}")
    print(f"\n{format_report([node_report, mem_report])}")
    print(f"\nOutput: {args.output}")


if __name__ == '__main__':
    main()
