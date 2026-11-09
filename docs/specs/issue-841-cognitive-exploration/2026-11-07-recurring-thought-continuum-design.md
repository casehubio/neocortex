# Recurring Thought Continuum — Design Spec

**Issue:** casehubio/neocortex#826
**Epic:** casehubio/neocortex#841 (Cognitive exploration)
**Depends on:** #825 (Search budget), #596 (Information-gap curiosity — requires existing implemented components: InformationGapDetector, CuriosityChannelComputer, CuriosityDrive, CuriosityPromptSection, CuriosityDescriptionGenerationPhase)
**Date:** 2026-11-07

## Overview

Curiosity, fixation, obsessive thought, and depressive rumination are the same computational mechanism — recurring thought about a specific topic — differentiated by valence, regulation capacity, and whether the loop resolves. This spec implements a continuum classifier that derives the agent's position from existing data, wires the pathological threshold in the search budget, and modulates prompt rendering to reflect the continuum state.

**Core principle:** No new state is stored. The continuum position is a pure function of existing data — experience memory frequency, affect trajectory, CAPS attractor strength, attention signal distribution, and confidence changes.

## ContinuumPosition Enum

Four discrete positions:

| Position | Drive | Resolves? | Can disengage? | CAPS signature |
|----------|-------|-----------|----------------|----------------|
| HEALTHY | Approach | Yes | Yes | Shallow attractor, easy escape |
| FIXATION | Approach + anxiety | Partially | Difficult | Deeper attractor, high escape threshold |
| OBSESSION | Anxiety | No | Can't | Sticky attractor, resists settling |
| RUMINATION | Despair | No | Self-reinforcing | Loop attractor, no exit without perturbation |

**Module:** `mindmap-api` — value type consumed by prompt sections and consolidation, same tier as `SignalCategory`.

## Seven Symptoms — Data Source Mapping

Each symptom is scored [0,1] from an existing data source:

| Symptom | Data source | Derivation |
|---------|-------------|------------|
| Thought recurrence | CaseMemoryStore | Count of experience memories mentioning this entity/subject within `recurrenceWindow` (default 7 days), via ExperienceQuery. Normalized against a per-agent baseline. |
| Habituation failure | HabituationState + experience frequency | In-session: `HabituationState.noveltyScores().getOrDefault(hash, 1.0)` staying high despite `observationCounts().getOrDefault(hash, 0) > HabituationConfig.repetitionTolerance()`. Cross-session: derived from experience memory timestamps — if the topic appears in ≥N experience memories within `recurrenceWindow` with non-declining frequency (count in recent half ≥ count in older half), that's cross-session habituation failure. No persistent habituation state needed. |
| Gap closure resistance | MindMap node confidence | Confidence on the topic's node not increasing (or decreasing via ConfidenceDecayDecorator) despite new information arriving. Computed as 1.0 - (confidenceChange / expectedChange) clamped [0,1]. |
| Attentional capture | AttentionSignal distribution | Proportion of recent attention signals originating from this topic vs others. High concentration = capture. Normalized by total signal count to avoid false positives in low-signal environments. |
| Arousal trajectory | AffectTrajectoryAnalyzer | PAD arousal slope: sustained positive slope or high sustained arousal → higher score. Declining arousal with negative pleasure → rumination signal. |
| Valence | MindMap node PAD pleasure | Current pleasure dimension on the topic's node. Inverted: low/negative pleasure → higher symptom score. |
| Action tendency | CAPS BehavioralAttractor | Attractor nodes from BEHAVIORAL subgraph, queried by CapsGenerated trait. `BehavioralAttractor.category()` is a free-form String matching CAPS OUTPUT node names from the topology YAML. Mapping: output nodes containing "approach"/"engage"/"explore" keywords → 0.0 (healthy); mixed approach + avoidance attractors on the same topic (both present, compare by strength) → 0.5 (fixation); avoidance-dominant attractors ("avoid"/"withdraw"/"freeze" keywords, strongest attractor) → 0.75 (obsession); withdrawal-only ("withdraw"/"disengage"/"shutdown") → 1.0 (rumination). When multiple attractors exist for a topic, use the strongest (highest EMA strength) to determine the dominant tendency. |

## RecurringThoughtScorer

**Module:** `mindmap-intelligence` — instance class with constructor-injected dependencies, same pattern as `InformationGapDetector`.

**API:**
```java
public class RecurringThoughtScorer {
    private final MindMapStore mindMapStore;
    private final CaseMemoryStore memoryStore;

    public RecurringThoughtScorer(MindMapStore mindMapStore, CaseMemoryStore memoryStore) {
        this.mindMapStore = mindMapStore;
        this.memoryStore = memoryStore;
    }

    public RecurringThoughtAssessment score(
        String topicNodeId,
        String tenantId,
        RecurringThoughtConfig config,
        @Nullable AttentionBriefing briefing,
        @Nullable HabituationState habituation
    )
}
```

**RecurringThoughtAssessment** — record in `mindmap-api`:
```java
public record RecurringThoughtAssessment(
    String topicNodeId,
    double thoughtRecurrence,
    double habituationFailure,
    double gapClosureResistance,
    double attentionalCapture,
    double arousalTrajectory,
    double valence,
    double actionTendency,
    double compositeScore,
    ContinuumPosition position
)
```

**Composite scoring:** Weighted sum of seven symptom scores, normalized to [0,1]. Default weights equal (1/7 each), overridable per-agent via `RecurringThoughtConfig.symptomWeights`. Thresholds map composite → enum: HEALTHY [0, 0.3), FIXATION [0.3, 0.6), OBSESSION [0.6, 0.8), RUMINATION [0.8, 1.0].

## RecurringThoughtPhase (ConsolidationPhase)

**Module:** `mindmap-intelligence` — alongside `BehavioralSynthesisPhase`.

**Priority:** `@Priority(25)` — after the @19-@24 range (BehavioralSynthesisPhase@19, RelationalSchemaEvolutionPhase@20, DriveProfileDerivationPhase@21, DriveDescriptionGenerationPhase@22, SelfStandardDerivationPhase@23, MergeDetectionPhase@24). CAPS attractors from @19 are fresh. No dependency on @20-@24 phases.

**Per-consolidation cycle:**

1. **Discover agents** — `ConsolidationPhase.run(tenantId, subgraphPriority)` does not receive `agentId`. Agent discovery follows the `BehavioralSynthesisPhase` pattern: query COGNITIVE subgraph nodes, group by `agent-id` property. Each agent is scored independently.

2. **Enumerate topics per agent** — for each agent, scan experience memories via `CaseMemoryStore.scan()` with domain="experience" within the `recurrenceWindow`, filtering by agent. Extract entity/subject references from memory text and metadata (`ExperienceAttributeKeys.SUBJECT`). Resolve referenced entities to MindMap nodes. Also include GOAL subgraph nodes that have received attention signals.

3. **Score each topic** — invoke `RecurringThoughtScorer.score()` per topic.

4. **Cache assessments** — store per-agent assessments in a `ConcurrentHashMap<String, List<RecurringThoughtAssessment>>` keyed by `agentId:tenantId`. This cache survives across consolidation cycles within the process. It is the source of truth for prompt sections (see Prompt Rendering Modulation).

5. **Produce ConsolidationArtifacts** — for each non-HEALTHY assessment, produce a `ConsolidationArtifact` carrying the `RecurringThoughtAssessment`. `ConsolidationMediator` drains these to `CognitionCore`, which passes them to prompt sections via `CognitionRenderContext` (see below).

6. **Emit signals** — if position ≥ FIXATION, emit `AttentionSignal` with `SignalCategory.THOUGHT_RECURRENCE`, `sourceNodeId` set to the topic node, and significance scaling with severity: FIXATION=0.4, OBSESSION=0.7, RUMINATION=1.0. The `reason` field carries a human-readable description (e.g., "FIXATION on topic X").

7. **Minimum data guard** — topics with fewer than `minObservations` (default 3) experience memories in the window always classify as HEALTHY. Prevents false positives on sparse data.

**Dependencies via `Instance<T>`:** CaseMemoryStore, MindMapStore, CognitiveDefaultsRegistry. If CaseMemoryStore is absent, the phase is a no-op (graceful degradation).

**SignalCategory extension:** `THOUGHT_RECURRENCE` added as the 12th value in the existing enum in `mindmap-api`.

## PathologicalThreshold Wire-up

**Module:** `cognition` — in `SearchBudgetTickParticipant`.

**Current state:** `SearchBudgetConfig.pathologicalThreshold()` (default 1.4) is declared but not read in `computeConvergence()`.

**Current `computeConvergence()` signature:**
```java
double computeConvergence(double intensity, DriveAxis dominant, SearchBudgetConfig config, @Nullable GutValence gut)
```

**Changes required:**

1. **Add `HabituationState` access.** `SearchBudgetTickParticipant` already has access to `AppraisalTickParticipant` (for threat/arousal). `AppraisalTickParticipant` holds the `ConcurrentHashMap<String, HabituationState>` keyed by `agentId:tenantId`. Add a method parameter or access via the existing `AppraisalTickParticipant` reference.

2. **Add `HabituationConfig` access.** The threshold fields (`repetitionTolerance`, `noveltyThreshold`) are on `HabituationConfig` (cognitive-api), not `SearchBudgetConfig`. Access via `CognitiveDefaultsRegistry` which `SearchBudgetTickParticipant` already injects.

3. **Extend `computeConvergence()` signature:**
```java
double computeConvergence(double intensity, DriveAxis dominant, SearchBudgetConfig config,
                          @Nullable GutValence gut,
                          @Nullable HabituationState habituation, HabituationConfig habConfig,
                          double anxiousnessScore)
```

4. **Wire-up logic** — after the existing logistic computation:
```java
if (habituation != null) {
    boolean anyFailure = habituation.observationCounts().entrySet().stream()
        .anyMatch(e -> e.getValue() > habConfig.repetitionTolerance()
                    && habituation.noveltyScores().getOrDefault(e.getKey(), 1.0)
                       > habConfig.noveltyThreshold());
    double habFailure = anyFailure ? 1.0 : 0.0;
    if (anxiousnessScore + habFailure > config.pathologicalThreshold()) {
        convergence = 0.0;
    }
}
```

**Note on topic-awareness:** The tick-level mechanism is **global** (agent-wide), not per-topic. `HabituationState` is keyed by content hashes of individual stimuli, not by MindMap topic nodes. When `anxiousnessScore + habituationFailure > pathologicalThreshold`, ALL convergence collapses — the agent cannot converge on anything. This is intentional: an agent in a pathological state (high anxiety + failure to habituate to a stimulus) experiences system-wide convergence failure. Per-topic differentiation happens at the consolidation level via `RecurringThoughtPhase`.

**Anxiousness score source:** The `anxiousnessScore` is derived from the current tick's appraisal result — specifically the arousal component from `AppraisalTickParticipant`. `SearchBudgetTickParticipant` already reads from `AppraisalTickParticipant` for threat detection, so this is an existing dependency.

When convergence collapses to 0, the existing `SearchBudgetPromptSection` special path (convergence < 0.05 && breadth < 0.15) renders: "You keep coming back to this. Other thoughts won't stick." The continuum position then enriches this text (see Prompt Rendering Modulation below).

**Relationship between tick and consolidation:** The tick-level convergence collapse is immediate (single tick, in-session, agent-wide). The consolidation-level `RecurringThoughtPhase` is the cross-tick per-topic longitudinal pattern detector. They are complementary — tick responds fast to acute states, consolidation classifies sustained patterns per topic.

## Prompt Rendering Modulation

No new prompt sections. The `ContinuumPosition` modulates two existing sections.

### CuriosityPromptSection

Currently renders four-channel curiosity (Litman I/D, Silvia comprehension, Berlyne focus, Loewenstein proximity) with proportional baseline deviation.

| Position | Rendering change |
|----------|-----------------|
| HEALTHY | No change — four-channel rendering as-is |
| FIXATION | Channel rendering narrows to dominant channel only. Urgency framing added. D-type curiosity (deprivation) emphasised over I-type (interest). |
| OBSESSION | Channels suppressed entirely. Replaced with intrusive quality language. Arousal-dominant. |
| RUMINATION | Channels suppressed. Withdrawal framing. Flat affect, no approach motivation. |

### SearchBudgetPromptSection

Currently has convergence < 0.05 special path.

| Position | Rendering change |
|----------|-----------------|
| HEALTHY | Normal per-source rendering |
| FIXATION | Convergence text shifts from neutral to pressured ("you keep circling back") |
| OBSESSION | Depth rendering adds compulsive quality |
| RUMINATION | All parameters rendered as collapsed — breadth 0, laterality 0, depth stagnant, convergence 0 |

### Pathway from consolidation to prompt sections

Prompt sections (`CuriosityPromptSection`, `SearchBudgetPromptSection`) do NOT have access to `AttentionBriefing` or `CaseMemoryStore`. The `CognitionRenderContext` currently carries only `agentId`, `tenantId`, `subjectId`, and `activatedDriveCategories`.

**Solution: extend CognitionRenderContext with continuum data.**

1. `RecurringThoughtPhase` caches per-agent assessments in-memory (ConcurrentHashMap) and produces `ConsolidationArtifact`s.
2. `ConsolidationMediator` (cognition module) drains artifacts during `CognitionCore` tick processing.
3. `CognitionCore` extracts `RecurringThoughtAssessment` records from the artifacts and stores them as the current continuum state.
4. **Extend `CognitionRenderContext`** with `Map<String, ContinuumPosition> continuum` — keyed by topic nodeId. `ContinuumPosition` is in `mindmap-api`, which is transitively visible from `cognition-api`.
5. Prompt sections read `context.continuum()` during rendering. No store queries, no re-derivation.

This adds one field to `CognitionRenderContext` and zero new dependencies to prompt sections. The data flows through the existing ConsolidationArtifact → ConsolidationMediator → CognitionCore pathway.

**Signal pathway (separate concern):** `THOUGHT_RECURRENCE` attention signals still flow through `CognitiveAttentionAccumulator` → `CognitiveAttentionMediator` for the attention threshold mechanism. This is the alerting pathway (fires `CognitiveAttentionRequired` CDI event). The render context pathway above is the prompt modulation pathway. They are independent.

### CuriosityDescriptionGenerationPhase

When generating crystallised LLM prose for curiosity (@Priority(29)), if the topic has a non-HEALTHY continuum position, the generation prompt includes the position as context so the LLM prose reflects the correct tone. Programmatic fallback uses the modulation tables above.

## Configuration and Personality Modulation

### RecurringThoughtConfig

New record in `cognitive-api`, added to `CognitiveDefaults` as field 19:

```java
public record RecurringThoughtConfig(
    Map<String, Double> symptomWeights,
    double fixationThreshold,
    double obsessionThreshold,
    double ruminationThreshold,
    Duration recurrenceWindow,
    int minObservations
) {
    public static RecurringThoughtConfig defaults() {
        return new RecurringThoughtConfig(
            Map.of(), // empty = equal weights (1/7 each)
            0.3, 0.6, 0.8,
            Duration.ofDays(7),
            3
        );
    }
}
```

### CognitiveDerivationEngine — 14th derivation pathway

Disposition profile → `RecurringThoughtConfig` adjustments:

| Disposition signal | Config adjustment | Rationale |
|--------------------|-------------------|-----------|
| High neuroticism / low emotional stability | Lower fixation threshold, higher weight on arousal + valence | Neuroticism amplifies negative affect sensitivity (Eysenck 1967) |
| High conscientiousness | Higher obsession threshold, lower weight on attentional capture | Self-regulation buffers against loss of control (Roberts et al. 2014) |
| High openness | Higher habituation rate modulation (broader recurrence window) | Open individuals shift attention more fluidly (DeYoung 2015) |
| Low agreeableness / high autonomy | Lower rumination threshold | Social isolation amplifies circular thinking (Nolen-Hoeksema 1991) |

Two agents experiencing identical stimuli can land at different continuum positions — personality determines susceptibility.

### YAML cognitive profile support

Per-agent overrides via existing `cognitive-profiles/*.yaml`. `CognitiveProfileWatcher` hot-reload works automatically since `RecurringThoughtConfig` is a field on `CognitiveDefaults`.

## Module Placement

| Component | Module | Rationale |
|-----------|--------|-----------|
| `ContinuumPosition` enum | mindmap-api | Value type, same tier as SignalCategory |
| `RecurringThoughtAssessment` record | mindmap-api | Result record alongside AttentionSignal |
| `SignalCategory.THOUGHT_RECURRENCE` | mindmap-api | Enum extension |
| `RecurringThoughtConfig` record | cognitive-api | Config on CognitiveDefaults, alongside SearchBudgetConfig |
| `RecurringThoughtScorer` class | mindmap-intelligence | Instance with constructor-injected stores — same pattern as InformationGapDetector |
| `RecurringThoughtPhase` | mindmap-intelligence | ConsolidationPhase alongside BehavioralSynthesisPhase |
| PathologicalThreshold wire-up | cognition | In SearchBudgetTickParticipant |
| `CognitionRenderContext` extension | cognition-api | Add `Map<String, ContinuumPosition> continuum` field |
| Prompt rendering modulation | cognition | In CuriosityPromptSection + SearchBudgetPromptSection |
| CDE derivation pathway | cognitive-index | In CognitiveDerivationEngine (14th pathway) |

No new modules created. All components fit existing module boundaries.

## Non-Goals

- Modelling clinical pathology. This is character behaviour, not diagnosis.
- Separate code paths for each continuum position. The symptoms are emergent from existing mechanisms at different parameter values.
- Persisting continuum state. The classification is re-derived each consolidation cycle.
- LLM-based classification. The classifier is threshold-based, mechanical.

## Literature

- Nolen-Hoeksema, S. (1991). Responses to depression — rumination as repetitive passive thinking about distress.
- Ehring, T. & Watkins, E.R. (2008). Repetitive negative thinking as a transdiagnostic process.
- Litman, J.A. (2008). I/D model — D-type curiosity (deprivation) at the fixation boundary.
- Silvia, P. (2008). Appraisal theory — high novelty + low coping → confusion/anxiety.
- Loewenstein, G. (1994). The psychology of curiosity — information-gap theory.
- Eysenck, H.J. (1967). The biological basis of personality.
- DeYoung, C.G. (2015). Cybernetic Big Five Theory — openness and attention fluidity.

## References

- `cognition-api/.../appraisal/HabituationState.java` — immutable in-memory habituation tracking
- `cognitive-api/.../HabituationConfig.java` — personality-derived habituation parameters
- `cognition/.../search/SearchBudgetTickParticipant.java` — pathologicalThreshold declared but unwired
- `cognition/.../search/SearchBudgetConfig.java` — pathologicalThreshold field (default 1.4)
- `cognition/.../search/SearchBudgetPromptSection.java` — convergence < 0.05 special path
- `cognition/.../curiosity/CuriosityPromptSection.java` — four-channel rendering
- `mindmap-intelligence/.../consolidation/BehavioralSynthesisPhase.java` — CAPS → BEHAVIORAL subgraph
- `mindmap-intelligence/.../InformationGapDetector.java` — entropy-based gap scoring
- `mindmap-intelligence/.../CuriosityChannelComputer.java` — four-channel derivation
- `mindmap-intelligence/.../CuriosityDescriptionGenerationPhase.java` — LLM prose crystallisation
- `mindmap-api/.../AttentionSignal.java` — change event for threshold accumulation
- `mindmap-api/.../SignalCategory.java` — 11-value enum (THOUGHT_RECURRENCE will be 12th)
- `cognitive-index/.../CognitiveDerivationEngine.java` — disposition → config derivation
- `cognitive-index/.../AffectTrajectoryAnalyzer.java` — PAD slope/volatility/trend
- `caps-engine/.../CapsSettler.java` — settling mechanics (CONVERGED/OSCILLATION/MAX_ITERATIONS)
- `mindmap-intelligence/.../consolidation/CognitiveAttentionAccumulator.java` — signal aggregation
- #826 issue body — seven symptoms table, architectural mechanisms, literature
- #825 — cognitive search budget (parameter model)
- #596 — information-gap curiosity (gap detection, four-channel rendering)
