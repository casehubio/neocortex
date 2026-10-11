#!/usr/bin/env python3
"""Step 5: Cognitive enrichment — apply the full prescriptive checklist.

Phase 2a: passage-level enrichment (PAD, OCC, SEC, action tendencies,
sub-thoughts, temporal, confidence, cognitive distortions).

Two modes:
  Normal:   enrich_cognitive.py <passages.json> <structure.json> -o enriched.json
  Backfill: enrich_cognitive.py --backfill <enriched.json>
            Enriches memories missing cognitive data (e.g., Phase 2b outputs).
"""

import argparse
import json
import os
import sys
import time

from llm_client import create_client, model_id
from coverage import load_registry, check_enrichment_coverage, format_report
from pipeline_state import atomic_write

CHECKLIST_PROMPT = """You are a cognitive psychologist analysing biographical text through the lens of the neocortex cognitive model. You will be given a passage and the memories/entities already extracted from it.

For EACH memory, apply the COMPLETE type-discriminated checklist. Every field must be evaluated. If a field genuinely does not apply, provide a justification — do not silently skip it.

## Checklist items (evaluate ALL that apply to the memory type):

### 1. PAD Affect [-1.0 to 1.0 each]
- pleasure: positive/negative valence
- arousal: activation/deactivation level
- dominance: sense of control/submission
- pad_justification: one sentence per dimension explaining why these values

### 2. OCC Emotions [intensity 0.0-1.0, pick all that apply]
22 types: joy, distress, hope, fear, pride, shame, admiration, reproach,
love, hate, happy-for, sorry-for, resentment, gloating, gratitude, anger,
gratification, remorse, interest, disgust, surprise, relief
Format: {"type": "joy", "intensity": 0.8, "source": "INTRINSIC"}
Source: INTRINSIC (directly felt), EMPATHIC (felt for another), ATTRIBUTED (inferred)

### 3. SEC Appraisal [0.0-1.0 each, conduciveness is -1.0 to 1.0]
- relevance, novelty, urgency (Relevance check)
- conduciveness (Implications check)
- controllability, adjustability (Coping check)
- internal_standards, external_standards (Normative check)

### 4. Action Tendencies [intensity 0.0-1.0]
9 types: approach, avoidance, attendance, rejection, nonattendance,
antagonism, interruption, dominance, submission
Identify the DOMINANT tendency with intensity, plus any secondary tendencies.
Format: {"dominant": {"type": "approach", "intensity": 0.85}, "secondary": [{"type": "attendance", "intensity": 0.4}]}

### 5. Sub-thought Decomposition
Decompose the memory into cognitive sub-thoughts:
- affect-observation: what emotion is felt
- causal-inference: why it happened
- evaluative: judgment about it
- intention: what action it motivates
- self-reflection: what it means about the self
- association: what it connects to
- concern: what worry it raises
Format: [{"type": "affect-observation", "text": "...", "entity": "entity-name"}]

### 6. Cognitive Distortions [strength 0.0-1.0, list any present]
Types: all_or_nothing, catastrophizing, overgeneralization, mental_filter,
disqualifying_positive, mind_reading, fortune_telling, magnification,
emotional_reasoning, should_statements, labelling, personalization
Format: [{"type": "all_or_nothing", "strength": 0.80}]
If none present, explain why: "no_distortions_justification": "..."

### 7. Drive Impact [delta -1.0 to 1.0 for each affected axis]
- curiosity, competence, affiliation, autonomy
- drive_justification: one sentence per axis explaining the delta

### 8. Confidence
- origin: STATED | INFERRED | SPECULATED
- value: 0.0-1.0
- confidence_justification: why this confidence level

### 9. Habituation Assessment
- novelty: "novel" | "familiar" | "habituated"
- habituation_score: 0.0-1.0 (1.0 = completely novel)
- echoes: what prior experiences this echoes (comma-separated descriptions)

### 10. CAPS Input Activation [0.0-1.0 per activated node]
Which CAPS input nodes does this experience activate?
Categories: Relationship (trust_offered, trust_betrayed, intimacy_available, rejection, relationship_loss, social_support), Threat (criticism_received, social_threat, physical_threat, psychological_threat, abandonment_threat), Achievement (achievement_opportunity, failure_experienced, skill_challenge, recognition_received), Social (group_inclusion, group_exclusion, social_comparison, authority_present, norm_violation_observed), Resource (resource_scarcity, resource_abundance, loss_experienced, gain_experienced), Autonomy (choice_available, choice_restricted, agency_granted, agency_denied, novelty_encountered, routine_established)
Format: {"trust_betrayed": 0.9, "social_threat": 0.5}

### 11. Goal Impact
- goal_impacts: [{"goal": "goal name", "relationship": "blocks|enables|contributes-to|requires", "urgency_delta": 0.0-1.0, "state_change": "active|blocked|completed|dormant|abandoned" or null}]

### 12. Relationship Assessment (when 2+ people involved)
- relationship: {"other_agent": "person-name", "quality_signal": "POSITIVE|NEGATIVE|NEUTRAL", "trust_trajectory": "increasing|stable|decreasing"}

### 13. Gut Feeling (when instinctive/somatic reactions described)
- gut_feeling: {"valence": "APPROACH|AVOID|CAUTIOUS", "intensity": 0.0-1.0, "resonance": "what the feeling reminds the subject of"}
If no gut feeling present, omit this field.

### 14. Temporal Anchoring
- temporal_period: year or date if identifiable from context

Respond with JSON: {"enrichments": [{"memory_index": 0, "pad": {...}, "pad_justification": "...", "occ_emotions": [...], "sec": {...}, "action_tendencies": {...}, "sub_thoughts": [...], "cognitive_distortions": [...], "drive_impact": {...}, "drive_justification": "...", "confidence": {...}, "confidence_justification": "...", "habituation": {...}, "caps_inputs": {...}, "goal_impacts": [...], "relationship": {...}, "gut_feeling": {...}, "temporal_period": "..."}]}"""


def enrich_single_memory(client, passage_text, memory):
    """Apply cognitive checklist to one memory."""
    mem_type = memory.get('event_type', memory.get('domain', 'experience'))
    user_prompt = f"""Context:

{passage_text}

---

Memory to enrich ({mem_type}):
{memory['text']}

Apply the cognitive enrichment checklist to this memory.
Respond with a single JSON object (not wrapped in "enrichments")."""

    try:
        response = client.messages.create(
            model=model_id(),
            max_tokens=4096,
            system=CHECKLIST_PROMPT,
            messages=[{'role': 'user', 'content': user_prompt}],
        )
        text = response.content[0].text
        start = text.find('{')
        end = text.rfind('}') + 1
        if start >= 0 and end > start:
            return json.loads(text[start:end])
    except Exception as e:
        print(f"    Warning: enrichment failed: {e}", file=sys.stderr)
    return None


def validate_enrichment(enrichment):
    """Validate enrichment values are in expected ranges."""
    errors = []

    def check_range(val, lo, hi, label):
        if val is None or not isinstance(val, (int, float)):
            return
        if not (lo <= val <= hi):
            errors.append(f"{label} out of range: {val}")

    pad = enrichment.get('pad', {})
    if isinstance(pad, dict):
        for dim in ('pleasure', 'arousal', 'dominance'):
            check_range(pad.get(dim), -1.0, 1.0, f"PAD {dim}")

    for emo in enrichment.get('occ_emotions', []):
        if isinstance(emo, dict):
            check_range(emo.get('intensity'), 0.0, 1.0, "OCC intensity")

    sec = enrichment.get('sec', {})
    if isinstance(sec, dict):
        for dim in ('relevance', 'novelty', 'urgency', 'controllability', 'adjustability', 'internal_standards', 'external_standards'):
            check_range(sec.get(dim), 0.0, 1.0, f"SEC {dim}")
        check_range(sec.get('conduciveness'), -1.0, 1.0, "SEC conduciveness")

    return len(errors) == 0, errors


def apply_enrichment(memory, enrichment):
    """Stamp a memory with enrichment data."""
    memory['cognitive'] = enrichment
    memory['enrichment_version'] = 'v1.0'
    memory['enrichment_source'] = 'phase-2a-passage'
    memory['enrichment_timestamp'] = time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())
    for extra_key in ('pad', 'pad_justification', 'occ_emotions', 'sec', 'action_tendencies',
                      'cognitive_distortions', 'drive_impact', 'drive_justification',
                      'sub_thoughts', 'confidence', 'confidence_justification',
                      'habituation', 'caps_inputs', 'caps_expected_outputs',
                      'goal_impacts', 'relationship', 'gut_feeling', 'temporal_period'):
        if extra_key in enrichment:
            memory[extra_key] = enrichment[extra_key]
    pad = enrichment.get('pad', {})
    if isinstance(pad, dict) and 'pad_justification' in pad and 'pad_justification' not in memory:
        memory['pad_justification'] = pad['pad_justification']
    drive = enrichment.get('drive_impact', {})
    if isinstance(drive, dict) and 'drive_justification' in drive and 'drive_justification' not in memory:
        memory['drive_justification'] = drive['drive_justification']
    conf = enrichment.get('confidence', {})
    if isinstance(conf, dict) and 'confidence_justification' in conf and 'confidence_justification' not in memory:
        memory['confidence_justification'] = conf['confidence_justification']


def run_normal(args):
    """Normal mode: enrich passage-level memories using original passages as context."""
    client = create_client()

    with open(args.passages, 'r') as f:
        passages_data = json.load(f)
    with open(args.structure, 'r') as f:
        structure = json.load(f)

    passages_by_id = {p['id']: p for p in passages_data['passages']}
    memories_by_passage = {}
    for i, mem in enumerate(structure.get('memories', [])):
        pid = mem.get('source_passage')
        if pid:
            memories_by_passage.setdefault(pid, []).append((i, mem))

    enriched_memories = list(structure.get('memories', []))
    enrichment_count = 0

    total_memories = sum(len(pairs) for pairs in memories_by_passage.values())
    processed = 0

    for pid, mem_pairs in memories_by_passage.items():
        passage = passages_by_id.get(pid)
        if not passage:
            continue

        for global_idx, mem in mem_pairs:
            processed += 1
            print(f"  [{processed}/{total_memories}] {pid}: {mem['text'][:50]}...")
            enrichment = enrich_single_memory(client, passage['text'], mem)
            if enrichment:
                valid, errors = validate_enrichment(enrichment)
                if valid:
                    apply_enrichment(enriched_memories[global_idx], enrichment)
                    enrichment_count += 1
                    output = {
                        'stats': {
                            'total_memories': len(enriched_memories),
                            'enriched': enrichment_count,
                            'coverage_pct': round(enrichment_count / max(len(enriched_memories), 1) * 100, 1),
                        },
                        'entities': structure.get('entities', []),
                        'edges': structure.get('edges', []),
                        'memories': enriched_memories,
                    }
                    atomic_write(args.output, output)
            time.sleep(0.2)

    output = {
        'stats': {
            'total_memories': len(enriched_memories),
            'enriched': enrichment_count,
            'coverage_pct': round(enrichment_count / max(len(enriched_memories), 1) * 100, 1),
        },
        'entities': structure.get('entities', []),
        'edges': structure.get('edges', []),
        'memories': enriched_memories,
    }
    atomic_write(args.output, output)

    print_coverage(enriched_memories, enrichment_count, args.output)


def run_backfill(args):
    """Backfill mode: enrich memories that are missing cognitive data (e.g., Phase 2b outputs)."""
    client = create_client()

    with open(args.backfill, 'r') as f:
        enriched = json.load(f)

    memories = enriched.get('memories', [])

    phase2b_reflections = enriched.get('phase2b_reflections', [])
    phase2b_moods = enriched.get('phase2b_mood_snapshots', [])

    unenriched = []
    for i, mem in enumerate(memories):
        if 'cognitive' not in mem and mem.get('text'):
            unenriched.append((i, mem, 'memories'))

    for i, mem in enumerate(phase2b_reflections):
        if isinstance(mem, dict) and 'cognitive' not in mem and mem.get('text'):
            unenriched.append((i, mem, 'phase2b_reflections'))

    for i, mem in enumerate(phase2b_moods):
        if isinstance(mem, dict) and 'cognitive' not in mem and mem.get('text'):
            unenriched.append((i, mem, 'phase2b_mood_snapshots'))

    print(f"Backfill: {len(unenriched)} unenriched memories to process")
    enrichment_count = 0

    for idx, (i, mem, source) in enumerate(unenriched):
        print(f"  [{idx+1}/{len(unenriched)}] {source}[{i}]: {mem['text'][:50]}...")
        context = mem['text']
        if mem.get('subject'):
            context = f"About {mem['subject']}:\n{context}"
        if mem.get('period'):
            context = f"Period: {mem['period']}\n{context}"

        enrichment = enrich_single_memory(client, context, mem)
        if enrichment:
            valid, errors = validate_enrichment(enrichment)
            if valid:
                apply_enrichment(mem, enrichment)
                if args.pass_num > 1:
                    mem['enrichment_source'] = f'phase-2a-backfill-pass-{args.pass_num}'
                else:
                    mem['enrichment_source'] = 'phase-2a-backfill'
                enrichment_count += 1
                atomic_write(args.backfill, enriched)
        time.sleep(0.2)

    atomic_write(args.backfill, enriched)

    all_mems = memories + [m for m in phase2b_reflections if isinstance(m, dict)] + [m for m in phase2b_moods if isinstance(m, dict)]
    print_coverage(all_mems, enrichment_count, args.backfill)


def print_coverage(memories, enrichment_count, output_path):
    registry_path = os.path.join(os.path.dirname(__file__), '..', 'docs', 'field-registry.yaml')
    registry = load_registry(registry_path)
    enrich_report = check_enrichment_coverage(memories, registry)

    print(f"\nEnrichment complete:")
    print(f"  Memories enriched: {enrichment_count}/{len(memories)}")
    print(f"\n{format_report([enrich_report])}")
    print(f"\nOutput: {output_path}")


def main():
    parser = argparse.ArgumentParser(description='Cognitive enrichment — Phase 2a')
    parser.add_argument('passages', nargs='?', help='Path to passages.json (normal mode)')
    parser.add_argument('structure', nargs='?', help='Path to structure.json (normal mode)')
    parser.add_argument('--output', '-o', default='enriched.json', help='Output JSON file (normal mode)')
    parser.add_argument('--backfill', help='Path to enriched.json — enrich unenriched memories in-place')
    parser.add_argument('--pass-num', type=int, default=1, help='Convergence pass number (for provenance)')
    args = parser.parse_args()

    if args.backfill:
        run_backfill(args)
    elif args.passages and args.structure:
        run_normal(args)
    else:
        parser.error('Either provide passages + structure, or --backfill <enriched.json>')


if __name__ == '__main__':
    main()
