# Cognitive Dissonance: Constraint Satisfaction Resolution

**Issue:** casehubio/neocortex#593
**Branch:** issue-593-cognitive-dissonance
**Status:** Design approved
**Literature:** Festinger 1957, Aronson 1969, Shultz & Lepper 1996, Cooper & Fazio 1984, Marsella & Gratch 2009

---

## 1. Overview

A new consolidation phase (`DissonanceDetectionPhase`, `@Priority(25)`) in the `cognition` module detects contradictions between an agent's beliefs (Belieflike-trait nodes) and between beliefs and established behavioral patterns (CapsGenerated-trait nodes in the BEHAVIORAL subgraph). Detected contradictions produce a persistent `DissonanceTension` state in MindMap, adjust PAD (arousal up, pleasure down per Cooper & Fazio 1984), and render via a `DissonancePromptSection` so the character experiences and can articulate the internal conflict.

Resolution is provided via configurable mechanical decay as an interim pathway. The architecture is designed for integration with #595 (EMA coping), where dissonance tension becomes a coping trigger and resolution flows through EMA strategies (positive reinterpretation ≈ "change a cognition", denial ≈ "deny contradicting information", suppression ≈ "reduce importance").

### Theoretical Foundation

Festinger (1957) defined cognitive dissonance as psychological discomfort from holding inconsistent cognitions — broadly defined as any knowledge about oneself, one's behavior, or one's environment. Aronson (1969) refined this: the most potent dissonance occurs when behavior violates the self-concept. Cooper & Fazio (1984) added the affective dimension: dissonance produces measurable arousal.

Shultz & Lepper (1996) formalized dissonance as constraint satisfaction: cognitive elements are nodes in a connectionist network, consonance relations are excitatory connections, dissonance relations are inhibitory connections, and the network settles to minimize constraint violations. Their critical design choice — all types of cognitions (beliefs, behaviors, self-concepts) are represented uniformly — maps directly to the existing CAPS architecture.

### Relationship to Issue #593's Architecture Proposal

Issue #593 proposes that "competing beliefs are competing attractors" and that resolution "connects to the existing CAPS settling mechanism." This design honours that theoretical mapping — the LLM produces CAPS input activation maps for each contradicting element (§2), making beliefs into constraint-network participants per Shultz & Lepper. However, the design takes an **indirect CAPS path** rather than direct settling, for architectural reasons documented in D4: `BehavioralSynthesisPhase.findUnprocessedGraduated()` filters for `source-memory-id` from memory graduation, and direct CAPS re-settling would create a double write-cycle invalidating cached state. CAPS behavioral effects flow indirectly through the prompt→conversation→experience→graduation pipeline (§7), which is psychologically faithful — Festinger describes dissonance reduction as gradual, not instantaneous.

### Scope

**Belief-belief + belief-behavior**, with self-concept tension as an emergent property. Self-concept is already represented through two complementary mechanisms: CAPS mediating nodes (`self_worth`, `self_efficacy`) capture dimensional self-regard, while Belieflike nodes ("I am loyal", "I am moral") capture narrative identity-beliefs (Aronson 1969). When contradictory beliefs activate competing CAPS mediating nodes, self-concept tension emerges naturally without separate modeling.

---

## 2. Detection

### What Is Compared

- **Belief-belief**: All Belieflike-trait nodes for the agent, pairwise
- **Belief-behavior**: Each Belieflike node against established behavioral attractors (CapsGenerated-trait nodes in BEHAVIORAL subgraph with matching `agent-id`)

### How Comparison Works

A single LLM call per agent per consolidation tick. The prompt provides the belief set and behavioral attractor descriptions, and asks for structured JSON output:

```json
{
  "contradictions": [
    {
      "elementA": { "nodeId": "...", "type": "belief", "text": "I am a loyal friend" },
      "elementB": { "nodeId": "...", "type": "belief", "text": "I would betray anyone for power" },
      "strength": 0.85,
      "capsActivationsA": { "acceptance": 0.7, "sharing": 0.5 },
      "capsActivationsB": { "dominance": 0.8, "competition": 0.7 },
      "reasoning": "Loyalty and betrayal are directly contradictory..."
    }
  ]
}
```

The LLM maps each contradicting element to CAPS input node activations — the same semantic mapping that `LlmSituationClassifier` performs for experiences. This is what makes the beliefs "cognitive elements in the constraint network" per Shultz & Lepper (1996). The CAPS activation maps are retained on the tension node for observability and future coping integration (§6), though they are not directly consumed by CAPS settling in this iteration (see §7 for the indirect pathway).

### Behavioral Attractor Text Representation

Category + dominant-input + personality-label fields from the BEHAVIORAL subgraph node. Example: "approach (driven by mastery)" or "deliberate_provocation (driven by dominance, personality: calculating)".

### Scaling

Pairwise belief comparison is O(n²). With typical agent belief counts (10-30 Belieflike nodes), this produces 45-435 pairs — manageable in a single LLM context window. If belief count grows, the phase pre-filters by grouping beliefs by subject/subgraph proximity and only comparing within plausibly contradictory groups.

### Contract with BeliefRevisionPhase

Clear separation: evidence→belief contradictions are handled by BeliefRevisionPhase (`@Priority(14)`) which revises beliefs based on new evidence. Belief→belief and belief→behavior contradictions are handled by DissonanceDetectionPhase (`@Priority(25)`) which creates tension state. The two phases share no data dependencies and operate on different contradiction types.

---

## 3. Tension State

Detected contradictions are persisted as MindMap nodes in a `COGNITIVE` subgraph with a `DissonanceTension` trait. Each tension node represents one active contradiction pair.

### Node Properties

| Property | Type | Description |
|---|---|---|
| `tension-type` | string | `belief-belief` or `belief-behavior` |
| `element-a-id` | string | Node ID of first contradicting element |
| `element-b-id` | string | Node ID of second contradicting element |
| `element-a-text` | string | Belief/attractor description (for prompt rendering) |
| `element-b-text` | string | Belief/attractor description (for prompt rendering) |
| `strength` | double | [0.0-1.0] contradiction strength from LLM |
| `caps-activations-a` | string | JSON-encoded CAPS input activation map (observability + future #595 integration) |
| `caps-activations-b` | string | JSON-encoded CAPS input activation map (observability + future #595 integration) |
| `reasoning` | string | LLM explanation of the contradiction |
| `agent-id` | string | The agent experiencing the dissonance |
| `detected-at` | string | ISO timestamp of first detection |
| `last-confirmed` | string | ISO timestamp of most recent re-detection |
| `decay-count` | int | Ticks since last confirmation |

### Lifecycle

1. **Created** when LLM detects a new contradiction pair not already represented
2. **Confirmed** when a subsequent tick re-detects the same pair — `last-confirmed` updated, `decay-count` reset to 0
3. **Decayed** when a tick runs and the pair is NOT re-detected — `strength` reduced additively: `newStrength = strength - decayRate` (default `decayRate` = 0.1). Additive decay gives predictable time-to-resolution: a strength-1.0 tension resolves in ~9 unconfirmed ticks at the default rate. `decay-count` incremented.
4. **Resolved** when strength drops below `dissonanceResolutionThreshold` (configurable, default 0.15) — tension node superseded via `store.supersede()`

### Deduplication

Contradiction pairs are matched by element IDs (order-independent). If element A's text changes (belief was revised by BeliefRevisionPhase), the tension node is superseded and re-evaluated fresh on the next tick.

---

## 4. PAD Adjustment

When active dissonance tensions exist for an agent, PAD is adjusted proportional to aggregate tension. Applied by `DissonanceTickParticipant` (CognitionTickParticipant, DERIVED phase).

| Dimension | Formula | Default weight | Cap | Rationale |
|---|---|---|---|---|
| Arousal | `+sum(tension.strength * arousalWeight)` | 0.15 | +0.3 | Cooper & Fazio 1984: dissonance produces physiological arousal |
| Pleasure | `-sum(tension.strength * pleasureWeight)` | 0.10 | -0.2 | Dissonance is aversive (Festinger 1957) |
| Dominance | `-max(tension.strength) * dominanceWeight` | 0.05 | -0.1 | Strongest tension undermines sense of control |

Caps prevent dissonance from overwhelming other mood signals even in extreme multi-tension scenarios. The computed delta is clamped to `[-cap, +cap]` before application.

### Data Source

The participant reads from `DissonanceDetectionPhase`'s in-memory cache (ConcurrentHashMap keyed by `agentId:tenantId`), populated during consolidation runs. It does not query MindMap per tick. This follows the `BehavioralSynthesisPhase` pattern — that phase exposes `lastSettlingResult()` and `lastInputActivations()` accessors for per-tick consumers.

### Registration

Registered via `CognitionCore.addParticipant(CognitionPhase.DERIVED, dissonanceTickParticipant)`, wired through `CognitionCoreFactory` using `Instance<DissonanceDetectionPhase>` injection. Gated by `DissonanceDetectionConfig.enabled()`.

---

## 5. Prompt Rendering

`DissonancePromptSection` renders active tensions into the cognitive prompt. Reads from the same in-memory cache on `DissonanceDetectionPhase` as the tick participant.

### Example Output

```
You feel an uncomfortable tension between parts of yourself that don't fit together:
- Your belief that you are a loyal friend pulls against your willingness to betray
  others for power. This contradiction is sharp and unresolved.
- Your conviction that honesty matters sits uneasily alongside your established
  pattern of calculated deception.
```

### Rendering Rules

- Only tensions above `renderingThreshold` (default 0.3) are rendered
- Sorted by strength descending — strongest tension first
- Maximum `maxRendered` (default 3) tensions per tick
- Uses `element-a-text` and `element-b-text` from the tension node — no re-querying
- Belief-behavior tensions use action-gap wording: "your belief that X sits uneasily alongside your pattern of Y"
- `BlockTag.DISSONANCE` with `sortOrdinal(212)` — Tier 2 (State), between DRIVES (210) and CURIOSITY (215). Requires adding the `DISSONANCE(212)` variant to the `BlockTag` enum in cognition-api.

### Registration

Constructed imperatively in `CognitionCore.promptSections()` when `DissonanceDetectionConfig.enabled()` is true and the phase's cache contains active tensions for the current agent. Follows the existing pattern — each prompt section is instantiated inline based on config flags.

---

## 6. Resolution

### Interim: Mechanical Decay

Each consolidation tick, the phase re-evaluates existing tensions:

1. Collects current Belieflike nodes and behavioral attractors
2. LLM re-assesses existing contradiction pairs alongside scanning for new ones (single call)
3. Pairs the LLM still identifies as contradictory: `last-confirmed` updated, `decay-count` reset
4. Pairs NOT re-detected: `strength` reduced by `dissonanceDecayRate` (default 0.1, additive), `decay-count` incremented
5. Pairs below `dissonanceResolutionThreshold` (0.15): superseded

This handles organic resolution — when BeliefRevisionPhase revises a belief, or when behavioral attractors shift through natural experience, the LLM will no longer detect the contradiction and tension decays.

### Future: Coping Integration (#595)

The persistent tension state is designed as a coping trigger:

- `DissonanceTension` nodes have a stable format consumable by EMA's micro-loop
- Coping strategy effects map to Festinger's dissonance reduction strategies:
  - Positive reinterpretation ≈ "change a cognition" (Gross: COGNITIVE_CHANGE)
  - Denial ≈ "deny contradicting information" (EMA: narrative transformation)
  - Suppression/acceptance ≈ "reduce importance" (Gross: RESPONSE_MODULATION)
- When a coping strategy resolves a tension, it adjusts belief confidence or attractor strength directly — the next consolidation tick detects reduced tension organically
- The CAPS activation maps stored on tension nodes (§3) provide the coping system with information about which psychological pathways are in conflict

**Note:** This integration contract is a **proposed design** documented here so #595 can adopt it when implemented. #595 does not currently have a consuming mechanism for DissonanceTension nodes. A follow-on issue should be filed to track the integration work.

### Proposed Integration Contract for #595

| Element | Specification |
|---|---|
| Tension nodes | `COGNITIVE` subgraph, `DissonanceTension` trait, properties per §3 |
| Coping trigger protocol | EMA micro-loop scans for `DissonanceTension` nodes above `copingTriggerThreshold` (default 0.5) |
| Resolution feedback | Coping effects modify source beliefs/attractors; dissonance phase detects the change on next tick |

---

## 7. CAPS Behavioral Effects

CAPS behavioral effects are **indirect**, mediated through the prompt section's influence on conversation behaviour:

1. `DissonancePromptSection` renders the felt tension during conversation
2. The character's behaviour is influenced by the tension description
3. Behaviour creates new experience events (recorded via `ExperienceRecorder`)
4. Experiences graduate via `ExperienceConsolidationPhase` (with `source-memory-id`)
5. `BehavioralSynthesisPhase` classifies and settles CAPS on a subsequent tick (2+ ticks)

This indirect pathway is psychologically faithful — Festinger's theory describes dissonance reduction as a gradual process influenced by ongoing behaviour and social interaction, not an instant behavioural reconfiguration. `BehavioralSynthesisPhase.findUnprocessedGraduated()` filters for `source-memory-id` (from memory graduation) and `mergeDriveIntensities()` filters for `cognitiveKind: "drive-intensity"` — tension nodes match neither filter, so direct CAPS entry is unavailable without modifying BehavioralSynthesisPhase (which D6 prohibits).

---

## 8. Configuration

`DissonanceDetectionConfig` (`@ConfigMapping`, prefix `casehub.cognition.dissonance`):

| Property | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | false | Feature gate |
| `decay-rate` | double | 0.1 | Additive strength reduction per unconfirmed tick |
| `resolution-threshold` | double | 0.15 | Below this, tension is superseded |
| `rendering-threshold` | double | 0.3 | Below this, tension is not rendered |
| `max-rendered` | int | 3 | Maximum tensions rendered per tick |
| `arousal-weight` | double | 0.15 | PAD arousal increase per unit tension |
| `arousal-cap` | double | 0.3 | Maximum arousal delta from dissonance |
| `pleasure-weight` | double | 0.10 | PAD pleasure decrease per unit tension |
| `pleasure-cap` | double | 0.2 | Maximum pleasure delta from dissonance |
| `dominance-weight` | double | 0.05 | PAD dominance decrease from max tension |
| `dominance-cap` | double | 0.1 | Maximum dominance delta from dissonance |
| `coping-trigger-threshold` | double | 0.5 | Tension above which coping triggers (#595) |

---

## 9. Component Summary

| Component | Module | Type | Role |
|---|---|---|---|
| `DissonanceDetectionPhase` | cognition | `@Priority(25) ConsolidationPhase` | LLM-based contradiction detection, tension state management, in-memory cache |
| `DissonanceDetectionConfig` | cognition-api | `@ConfigMapping` record | Tunable parameters |
| `DissonanceTickParticipant` | cognition | `CognitionTickParticipant` (DERIVED) | Per-tick PAD adjustment from phase cache |
| `DissonancePromptSection` | cognition | `CognitionPromptRenderer` | Renders active tensions into cognitive prompt |
| `BlockTag.DISSONANCE` | cognition-api | Enum variant, `sortOrdinal(212)` | Prompt ordering between DRIVES and CURIOSITY |
| `DissonanceTension` trait | — | MindMap convention | Tension nodes in COGNITIVE subgraph |

### Cross-Module Changes

| Module | Change |
|---|---|
| `cognition-api` | Add `DissonanceDetectionConfig` record, add `BlockTag.DISSONANCE(212)` |
| `cognition` | Add `DissonanceDetectionPhase`, `DissonanceTickParticipant`, `DissonancePromptSection`; wire in `CognitionCoreFactory` and `CognitionCore.promptSections()` |

No changes to `mindmap-api`, `caps-api`, `caps-engine`, or `mindmap-intelligence`. The `ConsolidationArtifact` sealed hierarchy in mindmap-api is not extended — the phase uses its own in-memory cache pattern instead.

---

## 10. Data Flow

```
Consolidation tick
  └─ DissonanceDetectionPhase (@Priority 25)
       ├─ Read: Belieflike nodes + BEHAVIORAL attractor nodes (from MindMapStore)
       ├─ Read: Existing DissonanceTension nodes (from MindMapStore)
       ├─ LLM call: detect contradictions + re-assess existing tensions
       ├─ Write: Create/confirm/decay tension nodes in MindMap
       └─ Update: In-memory cache (ConcurrentHashMap<agentId:tenantId, List<TensionSnapshot>>)

Each cognitive tick
  └─ DissonanceTickParticipant (DERIVED phase)
       ├─ Read: In-memory cache from DissonanceDetectionPhase
       ├─ Compute: Aggregate PAD deltas (capped per §4)
       └─ Apply: PAD adjustment to mood state

Prompt rendering (in CognitionCore.promptSections())
  └─ DissonancePromptSection (constructed when enabled + cache non-empty)
       ├─ Read: In-memory cache from DissonanceDetectionPhase
       └─ Render: Natural-language felt tension (BlockTag.DISSONANCE, ordinal 212)
```

---

## 11. Testing Strategy

| Test Type | What It Verifies |
|---|---|
| Unit: DissonanceDetectionPhase | Tension creation, confirmation, additive decay, supersession lifecycle with InMemoryMindMapStore |
| Unit: DissonanceTickParticipant | PAD delta computation from cached tensions, capping at configured caps, empty-cache handling |
| Unit: DissonancePromptSection | Rendering threshold, sort order, max-rendered cap, belief-behavior wording |
| LLM-stubbed | Mock SessionPipeline with canned JSON — verify parsing, deduplication, cache population |
| Integration | Full consolidation pipeline with Belieflike + BEHAVIORAL nodes — tension nodes appear, cache populated, PAD deltas applied, prompt renders |
| Contract | Tension node format matches proposed #595 integration contract |

---

## 12. Follow-On Issues

The following items are deferred from this iteration and should be tracked as separate GitHub issues:

| Item | Description |
|---|---|
| Coping integration | #595 follow-on: implement DissonanceTension consumption in EMA micro-loop per §6 contract |
| NLI fast-path | Future optimization: NLI classifier as pre-filter before LLM for high-volume belief sets |
| Direct CAPS pathway | Evaluate whether a direct CAPS settling pathway (via BehavioralSynthesisPhase extension) produces richer behavioral effects than the indirect prompt pathway |

---

## References

- Festinger, L. (1957). *A Theory of Cognitive Dissonance*. Stanford University Press.
- Aronson, E. (1969). The theory of cognitive dissonance: A current perspective. *Advances in Experimental Social Psychology*, 4, 1-34.
- Shultz, T.R. & Lepper, M.R. (1996). Cognitive dissonance reduction as constraint satisfaction. *Psychological Review*, 103(2), 219-240.
- Cooper, J. & Fazio, R.H. (1984). A new look at dissonance theory. *Advances in Experimental Social Psychology*, 17, 229-266.
- Marsella, S.C. & Gratch, J. (2009). EMA: A process model of appraisal dynamics. *Cognitive Systems Research*, 10(1), 70-90.
- `caps-engine/src/main/resources/caps-topology.yaml` — CAPS network topology with mediating self-concept nodes
- `caps-engine/src/main/java/.../CapsSettler.java` — constraint satisfaction settling mechanism
- `cognition/src/main/java/.../belief/BeliefRevisionPhase.java` — evidence→belief contradiction handling
- `cognition/src/main/java/.../core/CognitionCore.java` — tick execution, participant registration, prompt section construction
- `cognition/src/main/java/.../core/CognitionCoreFactory.java` — CDI wiring for tick participants
- `cognition-api/src/main/java/.../prompt/BlockTag.java` — prompt section ordering (DISSONANCE variant to be added)
- `mindmap-intelligence/src/main/java/.../consolidation/BehavioralSynthesisPhase.java` — CAPS settling integration pattern, in-memory cache pattern
- `mindmap-api/src/main/java/.../ConsolidationArtifact.java` — sealed hierarchy (not extended; own cache used instead)
- `cognition/src/main/java/.../CognitionDefaultBeans.java` — CDI producer pattern for config records
- casehubio/neocortex#595 — EMA coping strategies (future resolution integration)
- D1-D9 in `decisions.md` — design decisions with trade-offs and alternatives
