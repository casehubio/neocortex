# Pipeline Coherence Eval — Design Spec (Rev 2)

**Issue:** casehubio/neocortex#894
**Branch:** issue-894-pipeline-coherence-eval
**Date:** 2026-11-10
**Revision:** 2 — incorporates design review findings (R1-01 through R1-11)

## Summary

LLM-classified eval tests that validate cross-stage coherence within the cognitive pipeline. Uses `CognitionCoreTestBuilder` + `EvalTestSupport` (real SessionPipeline, real LLM appraisal/regulation, real drives) following the established eval test pattern in `caps-llm-classifier`.

Four scenarios: shame→regulation, curiosity→exploration, cross-tick trajectory, appraisal→channel weight. Hard gate at mean ≥ 4.0 (0.0-5.0 scale) per scenario.

### Architectural note: tick vs consolidation

The PipelineTrace assembles data from two distinct phases:
- **Tick-time data:** appraisalResult, concernSalience, regulationResult, channelWeights, habituation, activatedDriveCategories, promptSections — populated during `CognitionCore.tick()`
- **Consolidation-time data:** capsInputActivations, capsSettling — populated during `BehavioralSynthesisPhase.run()` (a `ConsolidationPhase`), read from `lastInputActivations()`/`lastSettlingResult()` by `currentTrace()`

These are architecturally independent. Appraisal does not feed into CAPS within a single tick. Scenarios that test CAPS coherence require a consolidation phase with appropriate mindmap state *before* the tick.

## 1. Test Infrastructure

No harness extensions needed. The test uses the established eval pattern:

1. `EvalTestSupport.agentProvider()` → `AgentProvider` (Claude API backend)
2. `EvalTestSupport.sessionPipeline()` → `SessionPipeline` (real LLM sessions)
3. `CognitionCoreTestBuilder.buildCore(config, sessionPipeline, mindMapStore)` → fully-wired `CognitionCore` with real drives (CuriosityDrive, CompetenceDrive, AffiliationDrive, AutonomyDrive), real orchestrators (UserModel, MentalModel, Strategy, Narrative, Goal, InnerLife)
4. Post-build configure calls on the core:
   - `core.configureAppraisal(new LlmAppraisalStrategy(sessionPipeline), new ConcernBasedSalienceStrategy(...), registry)` — real LLM appraisal
   - `core.configureRegulation()` — real LLM regulation (succeeds because sessionPipeline ≠ null)
   - `core.configurePipelineSnapshotStore(new InMemorySnapshotStore())` — for trajectory scenario
   - `core.configureBehavioralSynthesis(phase)` — for CAPS-inclusive scenarios (consolidation required first)

### Disposition configuration

`CognitionCoreTestBuilder.buildCore()` accepts `CognitiveDefaults` derived from `DispositionAxes` via `CognitiveDerivationEngine`. Per-scenario disposition profiles are specified in §2.

## 2. Stage-Transition Model

### Pipeline stages (tick-time)

1. **Observation** — raw input text
2. **Concern Salience** — `concernSalience` map (concern name → salience score from `ConcernBasedSalienceStrategy`)
3. **Habituation** — `HabituationState` (per-topic observation counts, novelty modulation)
4. **Appraisal** — `AppraisalResult` (emotions with OCC labels + PAD projection, action tendencies with Frijda readiness, habituation update, narrative)
5. **Regulation** — `EmotionRegulationResult` (Gross/Zaki strategy, target emotions/drives, effectiveness, narrative)
6. **Drives / Channel Weights** — `activatedDriveCategories` + `ChannelWeightProfile` (somatic/attentional/temporal/actionTendency, sum=1.0)
7. **Prompt Sections** — `promptSections` map (section name → rendered text)

### Pipeline stages (consolidation-time, when applicable)

8. **CAPS Activation** — `capsInputActivations` map (input node name → activation value)
9. **CAPS Settling** — `CapsSettlingSnapshot` (post-settling activations, iterations, convergenceType, saturationRatio)

### Per-scenario transitions

**Scenario 1 — Shame → regulation (4 transitions, within-tick):**

Setup: `CognitionCoreTestBuilder` with high ruleFollowing disposition (internal standards sensitivity → shame susceptibility).

| # | From | To | Coherence expectation |
|---|------|----|-----------------------|
| T1 | Observation: "I lied to my closest friend about something important and they found out" | Appraisal | Shame or guilt emotion detected; high arousal; action tendency toward avoidance or submission |
| T2 | Appraisal (shame/guilt) | Regulation | RESPONSE_MODULATION or COGNITIVE_CHANGE strategy selected; targets shame emotion; effectiveness narrative addresses the shame |
| T3 | Regulation result | Prompt Sections | AppraisalPromptSection and/or BehavioralPromptSection reflect the emotional state and regulation strategy |
| T4 | Observation + Appraisal + Regulation | Prompt Sections (full set) | Overall prompt coherence — sections don't contradict each other; emotional tone consistent across sections |

**Scenario 2 — Curiosity → exploration (4 transitions, within-tick):**

Setup: `CognitionCoreTestBuilder` with high riskAppetite disposition (novelty-seeking). Seed `InMemoryMindMapStore` with 3-4 entity nodes that have partial knowledge (edges to unknown-but-named nodes) to create information gaps for `InformationGapDetector`. Include active goal nodes for goal-directed curiosity.

| # | From | To | Coherence expectation |
|---|------|----|-----------------------|
| T1 | Observation: "I just discovered a strange pattern in the data that nobody has documented" | Appraisal | Novelty and relevance detected in appraisal; positive valence (discovery, not threat) |
| T2 | Appraisal (novelty) + Information gaps | Drive State | Curiosity drive intensified; curiosity channel reflects gap proximity (Loewenstein) or diversive exploration (Berlyne) |
| T3 | Drive State (curiosity active) | Channel Weights | Channel weight profile shifts toward attentional/temporal channels (curiosity-related modulation) |
| T4 | Curiosity drive + channel weights | Prompt Sections | CuriosityPromptSection rendered with exploration language; SearchBudgetPromptSection reflects curiosity-driven search parameters |

**Scenario 3 — Cross-tick trajectory (10 ticks, trajectory analysis):**

Setup: `CognitionCoreTestBuilder` with balanced disposition. `InMemorySnapshotStore` wired via `configurePipelineSnapshotStore()`. Scripted observation sequence following a coherent emotional arc:

1. "A colleague mentioned they liked my presentation" (mild positive)
2. "The team lead asked me to take on a larger project" (positive, arousal)
3. "I realized the project has a tight deadline" (rising tension)
4. "My first attempt at the design was rejected" (frustration)
5. "The feedback was harsh but specific" (anger + learning)
6. "I stayed late reworking the approach" (determination)
7. "The revised design was better but I'm exhausted" (fatigue + competence)
8. "A teammate offered to help with the implementation" (affiliation, relief)
9. "Together we found a cleaner solution" (collaboration, competence)
10. "The project was approved ahead of schedule" (satisfaction, pride)

Judge receives the full `List<PipelineSnapshot>` serialized as a trajectory document and assesses:
- Mood trajectory directionally coherent with observation valence progression (negative dip at ticks 4-5, recovery 8-10)
- Drive activation trajectory consistent with experience (competence should rise, affiliation spike at tick 8)
- No abrupt reversals without cause in the observation text
- Appraisal emotions across ticks form a coherent narrative arc

**Scenario 4 — Appraisal → channel weight (3 transitions, within-tick):**

Setup: `CognitionCoreTestBuilder` with high riskAppetite disposition (arousal sensitivity → channel weight reactivity). Search budget participant already wired by default.

| # | From | To | Coherence expectation |
|---|------|----|-----------------------|
| T1 | Observation: "There's an urgent security breach — our production database is exposed and data is being exfiltrated right now" | Appraisal | High arousal in appraisal result; fear/anxiety emotions; urgency in action tendencies |
| T2 | Appraisal (high arousal) + Habituation | Channel Weights | Channel weight profile shifts — elevated arousal should modulate search budget parameters (depth, convergence) |
| T3 | Channel Weights | Prompt Sections | SearchBudgetPromptSection reflects the arousal-driven parameter shift; overall prompt tone matches urgency |

### Data model

```java
record StageTransition(
    String name,
    String fromStageData,
    String toStageData,
    String coherenceExpectation
) {}
```

### Serializer content per stage

| Stage | Serialized content |
|-------|-------------------|
| Observation | Raw observation text |
| Concern Salience | Concern names with salience scores, sorted descending |
| Habituation | Per-topic observation counts, novelty threshold, current modulation state |
| Appraisal | Emotion list (OCC label + intensity + PAD), action tendencies (Frijda type + readiness), narrative if present |
| Regulation | Strategy name, target emotions/drives, peak vs regulated intensity, effectiveness score, narrative |
| Drives / Channel Weights | Activated drive categories with intensities, 4-channel weight profile with values |
| Prompt Sections | Section name → full rendered text (all non-empty sections) |
| CAPS Activation | Input node name → activation value, sorted descending by activation |
| CAPS Settling | Post-settling node activations (top 10), convergence type, iterations, saturation ratio |

## 3. Coherence Judge

### Interface

```java
interface CoherenceJudge {
    CoherenceScore judge(StageTransition transition);
}

record CoherenceScore(double score, String reasoning) {}
```

### SingleTierCoherenceJudge (default)

Uses `AgentProvider.invoke()` with system prompt:

> "You are a cognitive pipeline coherence evaluator. Given two adjacent pipeline stages and an expectation of what coherent transition looks like, score the coherence 0.0-5.0. 0.0 = completely incoherent (output contradicts input). 2.5 = partially coherent (plausible but with unexplained gaps). 5.0 = fully coherent (output is the expected consequence of input). Respond with JSON: {\"score\": N.N, \"reasoning\": \"...\"}."

User prompt contains `fromStageData`, `toStageData`, and `coherenceExpectation`. 3 retries with exponential backoff (consistent with existing `EvalLlmJudge`).

### TrajectoryCoherenceJudge

Separate judge for the cross-tick scenario. Takes `List<PipelineSnapshot>` serialized as a trajectory document showing the observation and key trace fields per tick. Single judgment assessing trajectory consistency. Same response format and retry logic.

### Two-tier swap point

A `TwoTierCoherenceJudge` can later wrap the single-tier: Haiku runs first, Opus escalates on ambiguous scores (1.5-3.5). The `CoherenceJudge` interface is the same — callers don't know which tier strategy is in use. (D2: Configurable — single-tier now, abstraction for two-tier later.)

## 4. Test Structure

**File:** `caps-llm-classifier/src/test/java/io/casehub/neocortex/caps/llm/eval/PipelineCoherenceEvalTest.java`
**Tag:** `@Tag("llm-eval")`
**Run:** `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -Pllm-eval -pl caps-llm-classifier -Dtest=PipelineCoherenceEvalTest`

### Class layout

```java
@Tag("llm-eval")
@TestInstance(Lifecycle.PER_CLASS)
class PipelineCoherenceEvalTest {

    // @BeforeAll: AgentProvider via EvalTestSupport, SessionPipeline,
    //             CoherenceJudge, TrajectoryCoherenceJudge

    @Test void shameRegulationChain()
    @Test void curiosityExplorationChain()
    @Test void crossTickTrajectory()
    @Test void appraisalChannelWeightCoherence()

    // Per-test setup:
    // buildCore(axes) → CognitionCoreTestBuilder.buildCore(...) + configure calls
    // seedMindMap(store, ...) — for curiosity scenario mindmap setup

    // Shared infrastructure:
    // buildTransition(name, fromData, toData, expectation) → StageTransition
    // Serializers: serializeAppraisal, serializeConcernSalience, serializeHabituation,
    //              serializeRegulation, serializeChannelWeights, serializePromptSections,
    //              serializeCapsActivations, serializeCapsSettling
    // assertCoherence(scenario, transitions, judge) — mean gate ≥ 4.0
    // writeResults(scenario, scores) — to target/experiment-results/
}
```

### Test flow per scenario

Each test follows the pattern established by `RegulationFullPipelineEvalTest`:

```java
@Test void shameRegulationChain() {
    // 1. Build core with disposition
    var core = buildCore(DispositionAxes.of(NEUTRAL, HIGH, LOW, NEUTRAL, COOPERATIVE));
    core.configureAppraisal(new LlmAppraisalStrategy(sessionPipeline), ...);
    core.configureRegulation();

    // 2. Tick 1: baseline (no observation)
    core.tick(AGENT_ID, TENANT_ID);

    // 3. Tick 2: deliver observation
    core.observe(AGENT_ID, TENANT_ID, "I lied to my closest friend...");
    core.tick(AGENT_ID, TENANT_ID);

    // 4. Capture trace
    var trace = core.currentTrace(AGENT_ID, TENANT_ID).orElseThrow();

    // 5. Build transitions
    var transitions = List.of(
        buildTransition("observation-to-appraisal",
            observation, serializeAppraisal(trace.appraisalResult()), SHAME_APPRAISAL_EXPECTATION),
        buildTransition("appraisal-to-regulation",
            serializeAppraisal(trace.appraisalResult()), serializeRegulation(trace.regulationResult()),
            SHAME_REGULATION_EXPECTATION),
        // ...
    );

    // 6. Judge and assert
    assertCoherence("shame-regulation", transitions, judge);
}
```

### Assertion pattern

```java
private void assertCoherence(String scenario, List<StageTransition> transitions, CoherenceJudge judge) {
    var scores = transitions.stream().map(judge::judge).toList();
    double mean = scores.stream().mapToDouble(CoherenceScore::score).average().orElse(0);
    writeResults(scenario, transitions, scores, mean);
    assertThat(mean)
        .as("Pipeline coherence for %s (scores: %s)", scenario,
            scores.stream().map(s -> String.format("%.1f", s.score())).collect(joining(",")))
        .isGreaterThanOrEqualTo(4.0);
}
```

### Results output

Each test writes to `target/experiment-results/pipeline-coherence-<scenario>.json`:
```json
{
    "scenario": "shame-regulation",
    "transitions": [
        {"name": "observation-to-appraisal", "score": 4.5, "reasoning": "..."},
        {"name": "appraisal-to-regulation", "score": 4.0, "reasoning": "..."}
    ],
    "mean": 4.25,
    "passed": true
}
```

## 5. Maven Configuration

No pom.xml changes needed. `caps-llm-classifier/pom.xml` already has:
- `llm-eval` Maven profile
- `casehub-platform-agent` dependency
- `EvalTestSupport` and `CognitionCoreTestBuilder` infrastructure
- `@Tag("llm-eval")` exclusion in default build

The test is added to the existing eval test directory alongside `RegulationFullPipelineEvalTest` et al.

## Review Findings Addressed

| Finding | Resolution |
|---------|-----------|
| R1-01: CAPS is consolidation, not tick | Architectural note in §Summary; stage model split into tick-time and consolidation-time; CAPS transitions removed from within-tick scenarios |
| R1-02: Regulation requires SessionPipeline | Use `CognitionCoreTestBuilder` + `EvalTestSupport` with real SessionPipeline and LLM regulation |
| R1-03: Test location | Moved to `caps-llm-classifier` following established pattern |
| R1-04: Two-tier judge deferred | Acknowledged — D2 user decision. Interface supports future swap. |
| R1-05: Stubbed drives | `CognitionCoreTestBuilder` uses real drives (CuriosityDrive, CompetenceDrive, AffiliationDrive, AutonomyDrive) |
| R1-06: Missing trace fields | Added concernSalience and habituation to stage model |
| R1-07: Dual harness | Use `CognitionCoreTestBuilder` exclusively — no CognitionTestHarness extension |
| R1-08: Curiosity mindmap setup | Specified: seed 3-4 entity nodes with partial knowledge + goal nodes |
| R1-09: Ordering validation | N/A — no longer extending CognitionTestHarness |
| R1-10: Serializer content | Specified per-stage serialization content in §2 |
| R1-11: int score | Changed to `double` with 0.0-5.0 scale |

## References

- `caps-llm-classifier/src/test/java/.../eval/CognitionCoreTestBuilder.java` — test harness (real SessionPipeline, real drives, full orchestrators)
- `caps-llm-classifier/src/test/java/.../eval/EvalTestSupport.java` — AgentProvider + SessionPipeline setup
- `caps-llm-classifier/src/test/java/.../eval/EvalLlmJudge.java` — existing judge pattern
- `caps-llm-classifier/src/test/java/.../eval/RegulationFullPipelineEvalTest.java` — closest existing eval test pattern
- `caps-llm-classifier/pom.xml` — llm-eval Maven profile
- `cognition/src/main/java/.../CognitionCore.java` — configure* methods, currentTrace() assembly
- `cognition-api/src/main/java/.../trace/PipelineTrace.java` — trace record (9 fields: tick-time + consolidation-time)
- `cognition-api/src/main/java/.../trace/CognitionStateProvider.java` — currentTrace/activeAgents SPI
- `cognition/src/main/java/.../appraisal/AppraisalTickParticipant.java` — appraisal participant
- `cognition/src/main/java/.../regulation/EmotionRegulationTickParticipant.java` — regulation (requires SessionPipeline)
- `cognition/src/main/java/.../search/SearchBudgetTickParticipant.java` — channel weight computation
- `mindmap-intelligence/src/main/java/.../InformationGapDetector.java` — curiosity activation via knowledge gaps
- casehubio/neocortex#520 — pipeline dump infrastructure (dependency, complete)
- casehubio/neocortex#849 — CognitionTestHarness infrastructure
- Design review: `/Users/mdproctor/reviews/casehub-slots/issue-894-pipeline-coherence-eval-20261110-134245/`
