# Cognitive Appraisal Architecture — Design Spec

**Issue:** casehubio/neocortex#428
**Branch:** issue-428-cognitive-appraisal-arch
**Date:** 2026-10-04
**Status:** Draft — SPI definitions in §4 are provisional pending Stage 1 research synthesis (§8). The research may revise context record shapes; the SPI method signatures are intentionally research-agnostic.

## 1. Problem Statement

LLM agent characters lack standardised emotional processing. Current drive descriptions try to carry the entire emotional process — what the character perceives, how they appraise it, and what they feel. This creates a ceiling where fixing one drive's expression regresses another (wacky-manor Principles 35-38). Characters also exhibit no boredom, impatience, or repetition fatigue — they loop happily forever because they have no novelty detection, habituation, or temporal expectation.

The root cause: emotions are prescribed as inputs rather than computed as outputs of a person × environment interaction.

## 2. Scientific Foundation

The architecture synthesises six established models. A standalone research synthesis document (multi-agent debate, building on #407's consolidation-time synthesis) determines what to adopt, adapt, or discard for real-time LLM agent appraisal.

| Model | Key contribution | Role in architecture |
|-------|-----------------|---------------------|
| **Lazarus** (1966, 1991) | Primary/secondary appraisal, core relational themes, reappraisal | Emotion vocabulary (15 CRTs), reappraisal as habituation mechanism |
| **Scherer** (2001) | 4 sequential SECs, computed emotion output | Appraisal pipeline structure (relevance → implications → coping → normative) |
| **Frijda** (1986, 2007) | Action readiness, habituation law, control precedence | Appraisal output type (action tendencies), habituation model |
| **OCC** (1988) | Event/agent/object taxonomy, 22 emotion types, computationally tractable | Emotion classification vocabulary, existing GoalAppraisal/ActionAppraisal SPIs from #383 |
| **Chain-of-Emotion** (2024) | Separate appraisal LLM call, emotion state across turns | Validates approach for LLM agents; informs pipeline execution model |
| **RAS / Salience Network** | Drive-biased attention, biased competition model | Salience filter design — drives shape perception before appraisal |

**Research debate questions** (Stage 1):
1. Which Scherer SECs add value for LLM agents? Do some collapse?
2. Where is the computational/LLM boundary per SEC?
3. Lazarus two-phase vs Scherer four-SEC — which maps better to LLM reasoning?
4. Action tendency granularity — full Frijda taxonomy or reduced set?

## 3. Architecture Overview

### 3.1 Three-System Complementary Architecture

Three systems operate at two temporal scales with defined interaction points:

```
CONSOLIDATION-TIME (sleep/idle)          REAL-TIME (per-tick)
┌─────────────────────────┐     ┌──────────────────────────────────┐
│ CAPS Engine             │     │ CognitionCore Tick               │
│  └─ BehavioralSynthesis │     │                                  │
│     └─ Attractor nodes  │────>│  FOUNDATION: Mood tick           │
│                         │     │  SOURCE: Narrative, Strategy     │
│ OCC (consolidation)     │     │  DERIVED:                        │
│  └─ GoalAffectPhase     │     │    DriveOrchestrator (dynamic)   │
│     └─ Prospect emotions│     │    SalienceStrategy              │
│                         │     │      └─ PerceivedSituation       │
│ DriveAdaptationPhase    │     │    AppraisalStrategy (Scherer)   │
│  (subsumed by CAPS)     │     │      └─ AppraisalResult          │
└─────────────────────────┘     │         ├─ CognitiveEmotion[]    │
                                │         └─ action tendencies     │
                                │  TERMINAL: Goals                 │
                                │                                  │
                                │  Prompt rendering:               │
                                │    AppraisalPromptSection         │
                                │    (replaces CharacterDrive)     │
                                └──────────────────────────────────┘
```

**System responsibilities:**

- **CAPS** (consolidation-time, caps-engine): Dispositional behavioral tendency encoding. Accumulates experience patterns into stable behavioral attractors during idle. Outputs to BEHAVIORAL MindMap subgraph. Settling is too expensive for real-time (~100 iterations).
- **Scherer appraisal pipeline** (real-time, cognition-api/cognition): Per-tick emotional context computation. Receives PerceivedSituation, produces AppraisalResult (emotions + action tendencies). Rendered as evocative prompt sections.
- **OCC** (both scales, mindmap-api): Emotion classification taxonomy. Real-time: ActionAppraisalObserver on ExperienceRecorded events. Consolidation: GoalAffectPhase appraises goal prospects. Both produce CognitiveEmotion instances.

**Interaction points:**

1. **CAPS → AppraisalWeights:** CAPS disposition parameters modulate Scherer appraisal thresholds via `AppraisalWeights` (derived by `CognitiveDerivationEngine` at agent creation from personality profile).

   Concrete mapping:
   - CAPS `threat_sensitivity` → `AppraisalWeights.fearOnsetThreshold` (higher threat sensitivity → lower threshold → more readily triggered fear/anxiety emotions)
   - CAPS `self_worth` attractors → `AppraisalWeights.selfStandardsStrictness` (higher self-worth → stricter self-standards → stronger pride/shame responses)
   - CAPS `other_reliability` attractors → `AppraisalWeights.otherStandardsStrictness` and `relationshipWeight` (low other-reliability → stricter other-standards → stronger reproach)

   **Static derivation (Stage 2):** `CognitiveDerivationEngine.deriveAppraisalWeights()` already derives AppraisalWeights from personality profile at agent creation. This uses static disposition axes — the initial pathway.

   **Dynamic modulation (future, post-#408):** Once CAPS attractors are persisted in the BEHAVIORAL subgraph and readable at runtime, `deriveAppraisalWeights` can incorporate CAPS attractor strengths alongside static personality weights. This is not a Stage 2 deliverable — it requires CAPS attractor persistence to be stable (#408).

2. OCC emotions feed CAPS as reinforcement signals during consolidation (#407 §2.4)
3. Situational action tendencies (appraisal) and dispositional attractors (CAPS) rendered independently — the LLM receives both at different temporal scales
4. CAPS subsumes DriveAdaptationPhase; DriveOrchestrator continues real-time drive computation

5. **Scherer pipeline ↔ OCC SPI boundary:** The Scherer appraisal pipeline and the existing OCC SPIs (#383) produce emotions at different temporal windows for different purposes. They do NOT overlap:
   - **Scherer pipeline** (this spec): Situational appraisal — runs per-tick in DERIVED phase BEFORE the LLM response. Appraises the current observation text to produce pre-response emotional context. Emotions bridge to mood via `MoodSignal.DirectShift`.
   - **ActionAppraisalObserver** (#383): Event appraisal — fires on `ExperienceRecorded` CDI events AFTER an interaction is recorded. Evaluates specific structured outcomes (success/failure) against goals and standards. Currently computes emotions via `HeuristicActionAppraisal` but does not bridge them to mood.
   - **GoalAffectPhase** (#383): Consolidation-time — runs during "sleep" to appraise goal prospects. Updates MindMap node PAD values. Does not feed `MoodOrchestrator` directly.
   - **GoalEmotionMoodBridge**: Real-time bridge — reads goal-orchestrator emotions and bridges them to mood via `MoodSignal.DirectShift`. This is the pattern the appraisal pipeline follows (§5.1).
   
   The temporal separation prevents double-counting: the Scherer pipeline appraises what's happening NOW (the current observation), ActionAppraisalObserver appraises what just HAPPENED (a recorded event). They cannot produce emotions for the same event at the same time.

### 3.2 Real-Time Pipeline (DERIVED Phase)

The appraisal pipeline runs as a `CognitionTickParticipant` in the DERIVED phase, after DriveOrchestrator:

```
DriveOrchestrator                    SalienceStrategy                 AppraisalStrategy
 ├─ Baseline drives (3 SDT +       ├─ Input: observation text       ├─ Input: PerceivedSituation
 │  curiosity, computed)            │  (from CognitionTickContext)   │  + drives + personality
 └─ Per-character drives            │  + DriveProfile                │  + AppraisalWeights
    (from cognitive profile)        │  + MoodState                   │  + habituation state
         │                          │  + active concerns/memories    │
         └──────────────────────────> Fusion: external + internal ──> Scherer SECs:
                                    │                                │  1. Relevance (novelty,
                                    └─ Output: PerceivedSituation    │     drive-relevance)
                                       (textual, subjective)        │  2. Implications (goal
                                                                     │     conduciveness, urgency)
                                                                     │  3. Coping potential
                                                                     │  4. Normative significance
                                                                     │
                                                                     └─ Output: AppraisalResult
                                                                        ├─ List<CognitiveEmotion>
                                                                        │  (OCC types + PAD)
                                                                        └─ ActionTendencies
                                                                           (Frijda taxonomy)
```

**Observation source:** The raw observation text (user message, game engine output, etc.) enters the pipeline via `CognitionTickContext`. This requires extending `CognitionTickContext` to carry an optional observation field — see §4.8 for the API change.

## 4. SPI Design

> **Provisional:** All SPI definitions in this section are illustrative designs based on the architecture decisions in `decisions.md`. Stage 1 research synthesis will validate or revise the context record shapes (e.g., whether `SalienceContext` needs additional memory retrieval parameters, whether `AppraisalContext` needs per-SEC configuration). The SPI method signatures (`perceive()`, `appraise()`) are intentionally research-agnostic — a single method per SPI allows implementations to structure internally around whatever model the research selects.

### 4.1 SalienceStrategy

Produces a perceived situation by fusing external observation with internal state. Perception is construction, not filtering — drives and mood actively shape what reaches conscious awareness.

```java
@FunctionalInterface
public interface SalienceStrategy {
    PerceivedSituation perceive(SalienceContext context);
}

public record SalienceContext(
    String observation,           // raw environment text (from CognitionTickContext)
    DriveProfile driveProfile,    // current drive state (baseline + character)
    MoodState moodState,          // current PAD emotional state
    List<Memory> activeConcerns,  // fears, hopes, anticipatory emotions
    List<Memory> recentExperiences // for novelty/repetition detection
) {}

public record PerceivedSituation(
    String narrative,             // textual subjective experience
    Map<String, Double> salience  // drive→salience scores for appraisal
) {}
```

### 4.2 AppraisalStrategy

Single SPI with one method. Implementations may internally structure around Scherer's 4 SECs but aren't forced to. Operates at a higher abstraction level than #383's GoalAppraisal/ActionAppraisal (which remain in mindmap-api for OCC-level classification).

```java
@FunctionalInterface
public interface AppraisalStrategy {
    AppraisalResult appraise(AppraisalContext context);
}

public record AppraisalContext(
    PerceivedSituation situation,
    DriveProfile driveProfile,
    AppraisalWeights weights,       // personality-derived thresholds
    HabituationConfig habituationConfig, // personality-derived habituation parameters
    HabituationState habituation,   // current repetition tracking state
    MoodState currentMood
) {}

public record AppraisalResult(
    List<CognitiveEmotion> emotions,      // OCC-typed emotions with PAD projections
    List<ActionTendency> actionTendencies, // Frijda taxonomy
    HabituationState updatedHabituation   // updated repetition state
) {}
```

### 4.3 Emotional Output — CognitiveEmotion Reuse

The appraisal pipeline produces `CognitiveEmotion` instances (from cognitive-api) — the same type that GoalAppraisal, ActionAppraisal, and GoalAffectPhase produce. This maintains a single, unified emotion representation across all temporal scales and appraisal levels.

**Existing type (cognitive-api):**

```java
public record CognitiveEmotion(
    EmotionType type,       // 22-value OCC enum: HOPE, FEAR, PRIDE, SHAME, ...
    double intensity,       // 0-1
    String subjectId,       // what/who triggered this emotion
    Instant onset,          // when the emotion arose
    EmotionSource source,   // INTRINSIC, EMPATHIC, ATTRIBUTED
    PadProjection pad       // per-emotion PAD contribution
) {}
```

**How appraisal uses it:**

- `EmotionType`: The 22-value enum provides compile-time exhaustiveness checking and typo detection. Each type maps to exactly one OCC branch (event-prospect, event-actual, agent-self, agent-other, object) — the branch classification is derivable from the type, not a separate field.
- `intensity`: 0-1, computed by the appraisal pipeline.
- `subjectId`: The entity the emotion is directed at (e.g., "Clara" for worry about Clara's absence, the agent's own ID for self-directed emotions like pride/shame).
- `onset`: The current tick's timestamp.
- `EmotionSource`: Mapped per emotion based on #383's established semantics:
  - `INTRINSIC` — agent evaluates its own situation or actions (Hope, Fear, Joy, Distress, Pride, Shame, Satisfaction, Disappointment, Relief, Fears-confirmed, Gratification, Remorse)
  - `ATTRIBUTED` — agent evaluates another agent's actions (Admiration, Reproach, Gratitude, Anger)
  - `EMPATHIC` — agent responds to another's fortunes (Happy-for, Pity, Resentment, Gloating)
  
  This preserves the three-way distinction that `HeuristicActionAppraisal` encodes (self-action → INTRINSIC, other-action → ATTRIBUTED) and that consumers may switch on. The appraisal strategy implementation determines EmotionSource from the appraisal target, not from a blanket assignment.
- `PadProjection`: Per-emotion PAD contribution. Mood integration aggregates these across all active emotions — no separate aggregate PAD record needed.

**Why not a new type:** The #383 spec chose `EmotionType` enum and `EmotionSource.ATTRIBUTED` specifically to force compile-time handling via exhaustive switches. Creating a parallel type with `String type` would lose this guarantee and fragment the emotion system. The OCC system (GoalAppraisal, ActionAppraisal, GoalAffectPhase) and the appraisal pipeline produce the same domain concept — they should use the same type.

**PAD and mood integration:** Each `CognitiveEmotion` carries its own `PadProjection`. The appraisal pipeline bridges emotions to `MoodOrchestrator` via `MoodSignal.DirectShift` (§5.1, step 5), following the `GoalEmotionMoodBridge` pattern: intensity-weighted PAD averaging across all appraisal emotions, recorded as a mood signal for the next tick. No third PAD representation is needed.

**Boundary with existing OCC SPIs:** The Scherer pipeline and the #383 OCC SPIs operate at different temporal windows and abstraction levels (see §3.1 interaction point 5). They both produce `CognitiveEmotion` instances using the same type system, but for different purposes.

**No separate EmotionalState record:** The original spec proposed `EmotionalState(double pleasure, double arousal, double dominance, List<DiscreteEmotion>)` with aggregate PAD. This is unnecessary — aggregate PAD is derivable from the constituent emotions' PadProjections, and the mood bridge handles aggregation. `AppraisalResult.emotions` (a `List<CognitiveEmotion>`) is the complete emotional output.

### 4.4 ActionTendency

Frijda's action readiness taxonomy. The appraisal computes the urge; the LLM decides whether to act on it.

```java
public record ActionTendency(
    ActionReadiness readiness,  // APPROACH, AVOIDANCE, ATTENDING,
                                // REJECTION, ANTAGONISM, INTERRUPTION,
                                // SUBMISSION, DOMINANCE, INDIFFERENCE
    double intensity,           // 0-1
    String target               // what the tendency is directed at
) {}
```

### 4.5 Habituation

Personality-parameterised boredom, impatience, and repetition fatigue. Derived from disposition profile via CognitiveDerivationEngine.

```java
public record HabituationConfig(
    double habituationRate,        // how fast novelty decays (personality-derived)
    double noveltyThreshold,       // below this, boredom signals emerge
    double repetitionTolerance,    // how many repeats before impatience
    Map<String, Double> domainModulation  // per-drive domain patience multipliers
) {}

public record HabituationState(
    Map<String, Integer> observationCounts,  // observation hash → count
    Map<String, Double> noveltyScores        // observation hash → current novelty
) {}
```

**Personality derivation** (new pathway in CognitiveDerivationEngine):

- High openness → fast habituationRate, low noveltyThreshold, low repetitionTolerance
- Low openness + high conscientiousness → slow habituationRate, high repetitionTolerance
- Per-character drives provide domainModulation: repetition within an active drive's domain habituates slower (Foxworth examining valuables vs listening to small talk)

**CognitiveDefaults integration:** A new `habituationConfig` field is added to `CognitiveDefaults`, following the established pattern (`personality`, `moodBaseline`, `curiosity`, `appraisalWeights`, etc.). Delivery path: `CognitiveDerivationEngine.deriveHabituationConfig()` → `CognitiveDefaults.habituationConfig` → `CognitiveDefaultsRegistry` → runtime lookup by `AppraisalTickParticipant`.

```java
// Addition to CognitiveDefaults record:
HabituationConfig habituationConfig

// New builder method:
public CognitiveDefaults withHabituationConfig(HabituationConfig habituationConfig) { ... }
```

### 4.6 Drive Model Evolution

DriveAxis evolves from a 4-value enum to dynamic per-character drives:

```java
public record Drive(
    String name,              // "protection", "greed", "curiosity" etc
    DriveCategory category,   // BASELINE (SDT+curiosity) or CHARACTER
    double intensity,         // 0-1, computed by DriveOrchestrator
    String trigger            // what's currently feeding this drive
) {}

public enum DriveCategory { BASELINE, CHARACTER }
```

The 4 existing DriveSource implementations (CuriosityDrive, CompetenceDrive, AffiliationDrive, AutonomyDrive) continue computing baseline drives.

**DriveProfile evolution:**

```java
public record DriveProfile(
    String agentId,
    String tenantId,
    List<Drive> drives,           // all drives: BASELINE + CHARACTER
    double compositeMotivation,   // 0-1, computed across all drives
    String dominantDrive,         // name of the highest-intensity drive
    Instant evaluatedAt
) {}
```

Key changes from current `Map<DriveAxis, DriveIntensity>`:
- `drives` is `List<Drive>` — holds both BASELINE and CHARACTER drives
- `dominantDrive` is `String` (not `DriveAxis`) — CHARACTER drives like "protection" aren't enum values
- `DriveIntensity` is subsumed by the `Drive` record (same fields: name/axis, intensity, trigger)

**DriveOrchestrator evolution:**

1. **BASELINE drives:** The 4 hardcoded `DriveSource` constructor parameters stay. These compute CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY via existing implementations.

2. **CHARACTER drives:** Discovered from `AgentDescriptor.disposition()` at tick time. The cognitive profile defines CHARACTER drive names and baseline intensities. Real-time modulation uses the same `DriveComposer` pipeline as BASELINE drives — MoodState modulates intensity (positive mood amplifies approach-oriented drives, negative mood amplifies avoidance-oriented drives), and NarrativeModulation adjusts based on active narrative arcs.

3. **compositeMotivation:** Computed as weighted average across all drives (BASELINE + CHARACTER), where CHARACTER drive weights come from their profile-defined baseline intensities.

4. **dominantDrive:** The drive (BASELINE or CHARACTER) with the highest post-composition intensity.

5. **DriveSource SPI:** Unchanged — continues to serve the 4 BASELINE drives. CHARACTER drives don't use `DriveSource` because their baseline comes from the cognitive profile, not from a computation.

Detailed design of the CHARACTER drive computation path (profile lookup, modulation parameters, DriveComposer adaptation) is a Stage 2 deliverable. The `DriveProfile` output shape above is settled — consumers (SalienceContext, AppraisalContext) depend on it.

### 4.7 Toggle Architecture

Every appraisal pipeline component is independently toggleable, following the existing `CognitionConfig` pattern. New fields added to `CognitionConfig`:

```java
// Additions to CognitionConfig:
boolean appraisalEnabled,         // master toggle for the appraisal pipeline
boolean salienceEnabled,          // salience filtering (can disable to pass raw observation)
boolean habituationEnabled        // habituation/novelty tracking
```

Per-SEC toggles (SEC 1 relevance, SEC 2 implications, SEC 3 coping, SEC 4 normative) are configuration of the `AppraisalStrategy` implementation, not CognitionConfig fields — they are internal to the strategy, not cross-cutting CognitionCore concerns. A `SchererAppraisalConfig` record provides these:

```java
public record SchererAppraisalConfig(
    boolean relevanceEnabled,      // SEC 1
    boolean implicationsEnabled,   // SEC 2
    boolean copingEnabled,         // SEC 3
    boolean normativeEnabled       // SEC 4
) {}
```

Salience-specific configuration is separate — it controls `SalienceStrategy`, not `AppraisalStrategy`:

```java
public record SalienceConfig(
    double salienceThreshold       // drive-weight threshold (0.0=all pass, 1.0=top drive only)
) {}
```

**Measurement:** Each toggle configuration produces a named experiment configuration reproducible via the existing measurement infrastructure (`classify_emotions.py`, per-drive 1-5 scoring, comparison against BASELINE0/L1). The test framework (§7) seeds specific toggle configurations and asserts on expected behavioral differences.

**Epic coverage:** The epic's toggle table maps to this architecture:

| Epic toggle | Architecture mapping |
|-------------|---------------------|
| Salience filter on/off | `CognitionConfig.salienceEnabled` |
| SEC 1-4 individual | `SchererAppraisalConfig` per-SEC flags |
| Emotion knowledge RAG | Not applicable — decision D2 rejects separate store (see §4.9) |
| Sub-LLM vs inline | SPI implementation choice — see §4.8 |
| Emotional echo (L1) | Existing feedback mechanism, orthogonal to appraisal |

### 4.8 Execution Model

**Synchronous SPIs, implementation-determined execution:**

Both SPIs are `@FunctionalInterface` with synchronous return types. This is intentional and consistent with the platform's execution model:

- `CognitionCore.tick()` is synchronous — all hardcoded orchestrators (mood, drive, narrative, strategy, goals) are called synchronously
- `CognitionTickParticipant.tick()` is `void` and called synchronously within `CognitionCore.runCustomParticipants()`
- The `safeRun()` wrapper in CognitionCore catches exceptions from any participant, preventing one failure from cascading

**LLM-backed implementations:** The SPIs do not prevent LLM calls. An implementation of `SalienceStrategy` or `AppraisalStrategy` can make blocking LLM calls internally (Haiku-class sub-LLM calls as the epic envisions). This follows the same pattern as other I/O-performing orchestrators in the tick lifecycle. The research phase (Stage 1) determines which stages benefit from LLM involvement vs pure computation.

**Latency:** If both salience and appraisal make sub-LLM calls, this adds ~400ms-1s to each cognition tick (at Haiku latency ~200-500ms per call). This is acceptable for the current use cases (pre-response context computation before the main LLM call, which takes 2-10s). If latency becomes problematic, implementations can:
- Use computational heuristics for some SECs and LLM only for others
- Cache salience results for repeated observations (habituation makes this natural)
- Run salience and appraisal in a single combined LLM call

**Error handling:** LLM call failures (timeouts, rate limits, malformed output) are caught by `CognitionCore.safeRun()`, which wraps every participant tick. A failed appraisal tick means no emotional context for this response — the prompt renders without the appraisal section. This graceful degradation follows the existing pattern (e.g., a failed drive tick means the drive prompt section renders from the previous tick's cached state).

**CognitionTickContext extension:** Observation text enters the tick lifecycle via an extended `CognitionTickContext`:

```java
public record CognitionTickContext(
    String agentId,
    String tenantId,
    @Nullable AgentDescriptor descriptor,
    SubjectResolver resolver,
    @Nullable String observation    // raw environment text (user message, game output, etc.)
) {}
```

`CognitionCore.tick()` gains a `@Nullable String observation` parameter. Callers (e.g., `CognitionAvatarAdapter`) pass the user message or game engine output. For proactive ticks with no external stimulus, observation is null — the appraisal pipeline is skipped entirely and the previous tick's `AppraisalResult` persists in the `ConcurrentHashMap` (the prompt section continues to render from the cached result). This is intentional: appraisal requires something to appraise. Mood changes and temporal expectations from proactive ticks influence the NEXT observation-bearing tick via the mood and habituation state that persists between ticks.

### 4.9 Emotion Knowledge — No Separate Store

The epic proposes "Emotion knowledge base (RAG)" as a full pipeline stage. This architecture explicitly rejects a separate emotion knowledge store (decision D2 in `decisions.md`).

**Rationale:** The emotion knowledge is small, well-structured, and enumerable. OCC defines 22 emotion types (`EmotionType` enum); Lazarus defines 15 core relational themes; Frijda lists ~16 action tendencies (`ActionReadiness` enum). These belong as enums and configuration in the appraisal strategy's logic — not as unstructured knowledge requiring semantic retrieval. Memories that inform appraisal (experiences, relationships, moods) are already in the existing memory stores (`CaseMemoryStore`, `CbrRecordStore`).

**Revisit trigger:** If Stage 1 research reveals that Chain-of-Emotion structured knowledge patterns or unstructured emotional knowledge would improve appraisal quality, this decision should be revisited. The SPI design does not preclude an implementation that queries a knowledge store internally.

## 5. Tick Lifecycle Integration

### 5.1 AppraisalTickParticipant

Registered as a `CognitionTickParticipant` in the DERIVED phase via `CognitionCore.addParticipant(CognitionPhase.DERIVED, participant)`. Phase assignment happens at registration time — `CognitionTickParticipant` is a `@FunctionalInterface` with a single `tick(CognitionTickContext)` method; there is no `phase()` method on the interface.

**Ordering within DERIVED phase:** DriveOrchestrator runs first as a hardcoded call in `CognitionCore.tick()`, then `runCustomParticipants(CognitionPhase.DERIVED, context)` iterates all registered participants in registration order. The appraisal participant runs after DriveOrchestrator because it depends on `DriveProfile` — this ordering is guaranteed by the hardcoded-then-custom execution model in CognitionCore, not by priority annotations.

```java
@ApplicationScoped
public class AppraisalTickParticipant implements CognitionTickParticipant {

    private final SalienceStrategy salienceStrategy;
    private final AppraisalStrategy appraisalStrategy;
    private final DriveOrchestrator driveOrchestrator;
    private final MoodOrchestrator moodOrchestrator;
    private final CognitiveDefaultsRegistry defaultsRegistry;
    private final CognitionConfig config;

    // Per-agent state — keyed by "agentId:tenantId"
    private final ConcurrentHashMap<String, AppraisalResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HabituationState> habituationStates = new ConcurrentHashMap<>();

    @Override
    public void tick(CognitionTickContext context) {
        if (!config.appraisalEnabled()) return;

        var agentKey = context.agentId() + ":" + context.tenantId();
        var observation = context.observation();

        // 1. Gather context
        var drives = driveOrchestrator.currentDrives(context.agentId(), context.tenantId())
                .orElse(null);
        if (drives == null) return;

        var mood = moodOrchestrator.currentMood(context.agentId(), context.tenantId())
                .orElse(null);

        var defaults = defaultsRegistry.get(context.agentId(), context.tenantId());
        var weights = defaults != null ? defaults.appraisalWeights() : AppraisalWeights.NEUTRAL;
        var habConfig = defaults != null ? defaults.habituationConfig() : null;
        var habituation = habituationStates.getOrDefault(agentKey, HabituationState.empty());

        // 2. Perceive (if salience enabled and observation available)
        PerceivedSituation situation;
        if (config.salienceEnabled() && observation != null) {
            var concerns = queryActiveConcerns(context.agentId(), context.tenantId());
            var recent = queryRecentExperiences(context.agentId(), context.tenantId());
            situation = salienceStrategy.perceive(
                new SalienceContext(observation, drives, mood, concerns, recent));
        } else if (observation != null) {
            situation = new PerceivedSituation(observation, Map.of());
        } else {
            return; // no observation and no salience — nothing to appraise
        }

        // 3. Appraise
        var result = appraisalStrategy.appraise(
            new AppraisalContext(situation, drives, weights, habConfig, habituation, mood));

        // 4. Store per-agent state
        results.put(agentKey, result);
        habituationStates.put(agentKey, result.updatedHabituation());

        // 5. Bridge emotions to mood (GoalEmotionMoodBridge pattern)
        if (!result.emotions().isEmpty()) {
            double totalIntensity = result.emotions().stream()
                    .mapToDouble(CognitiveEmotion::intensity).sum();
            if (totalIntensity > 0) {
                double p = result.emotions().stream()
                        .mapToDouble(e -> e.intensity() * e.pad().pleasure())
                        .sum() / totalIntensity;
                double a = result.emotions().stream()
                        .mapToDouble(e -> e.intensity() * e.pad().arousal())
                        .sum() / totalIntensity;
                double d = result.emotions().stream()
                        .mapToDouble(e -> e.intensity() * e.pad().dominance())
                        .sum() / totalIntensity;
                moodOrchestrator.record(
                        new MoodSignal.DirectShift(clamp(p), clamp(a), clamp(d),
                                "appraisal-emotions"),
                        context.agentId(), context.tenantId());
            }
        }
    }

    public Optional<AppraisalResult> currentResult(String agentId, String tenantId) {
        return Optional.ofNullable(results.get(agentId + ":" + tenantId));
    }

    private static double clamp(double value) {
        return Math.max(-2.0, Math.min(2.0, value));
    }
}
```

**Per-agent state management:** `AppraisalResult` and `HabituationState` are stored in `ConcurrentHashMap`s keyed by `agentId:tenantId`. This follows the same pattern as `DriveOrchestrator.profiles` (which uses `ConcurrentHashMap<String, DriveProfile>`). Thread safety is handled by ConcurrentHashMap's atomic operations — CognitionCore may tick multiple agents concurrently.

### 5.2 Prompt Rendering Changes

| Current | After #428 | Source |
|---------|-----------|--------|
| `MoodPromptSection` | Stays — PAD state | Mood tick |
| `DrivePromptSection` | Stays — baseline drive intensities | DriveOrchestrator |
| `CharacterDrivePromptSection` | **Removed** — monolithic descriptions | was MindMap |
| *(new)* `AppraisalPromptSection` | **Added** — perceived situation + emotions + action tendencies | AppraisalTickParticipant |

**Integration mechanism:** `AppraisalPromptSection` is wired into `CognitionCore.promptSections()` via the existing `chainSectionCustomizer()` mechanism. At initialization, the CDI layer registers a section customizer that adds `AppraisalPromptSection` and conditionally removes `CharacterDrivePromptSection`:

```java
// At initialization — register sections by type, not per-agent:
cognitionCore.chainSectionCustomizer(sections -> {
    if (config.appraisalEnabled()) {
        sections.add(new AppraisalPromptSection(appraisalParticipant));
        sections.removeIf(s -> s instanceof CharacterDrivePromptSection);
    }
    return sections;
});
```

`AppraisalPromptSection` resolves per-agent data at render time via `CognitionRenderContext`, following the same pattern as `DrivePromptSection` (which takes a `DriveOrchestrator` reference and calls `currentDrives(context.agentId(), context.tenantId())` in its `render()` method):

```java
class AppraisalPromptSection implements CognitionPromptRenderer {
    private final AppraisalTickParticipant participant;

    AppraisalPromptSection(AppraisalTickParticipant participant) {
        this.participant = participant;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        return participant.currentResult(context.agentId(), context.tenantId())
            .map(this::renderEvocative)
            .orElse(null);
    }

    private String renderEvocative(AppraisalResult result) { ... }
}
```

The customizer does NOT capture `agentId`/`tenantId` — the `UnaryOperator<List<CognitionPromptRenderer>>` signature has no agent context. Per-agent resolution happens inside `render(CognitionRenderContext)`.

Alternatively, `CognitionCore.promptSections()` can add `AppraisalPromptSection` directly with a new `appraisalEnabled` config check, following the same pattern as existing sections (e.g., `if (config.characterDrivesEnabled()) sections.add(new CharacterDrivePromptSection(...))`). Both mechanisms work; the `chainSectionCustomizer` approach avoids modifying CognitionCore's source.

**Removal sequencing:** `CharacterDrivePromptSection` removal (Stage 6) depends on the drive model evolution (Stage 2) being complete. Until CHARACTER drives are represented in `DriveProfile` and rendered via `AppraisalPromptSection`, `CharacterDrivePromptSection` continues to render CHARACTER drive information from MindMap nodes. The `chainSectionCustomizer` approach handles this naturally — it only removes `CharacterDrivePromptSection` when `appraisalEnabled` is true and appraisal results are available.

The AppraisalPromptSection renders evocatively, not analytically:

```
// NOT this (analytical):
"SEC 1: Relevance=0.8, SEC 2: Goal-conducive=0.3, SEC 3: Coping=low"

// THIS (evocative):
"You notice the empty chair where Clara usually sits — your stomach tightens.
Something is wrong. The protection instinct sharpens everything: the unlocked
door, the overturned glass, the draft from the corridor. You feel an urge to
search, to act, before it's too late."
```

**Habituation in rendering:**

```
// First encounter:
"The bookshelf catches your eye — old leather bindings, dust undisturbed."

// Third repetition (high-openness character):
"The same bookshelf. You've seen it. Your attention drifts — there must be
something more interesting in this house."

// Third repetition (low-openness character):
"The familiar bookshelf. Its orderly rows are reassuring."
```

## 6. Module Structure

All new types in existing modules — no new Maven modules:

**cognition-api** (SPIs and value types):
- `AppraisalStrategy` SPI
- `SalienceStrategy` SPI
- `AppraisalResult`, `AppraisalContext`
- `PerceivedSituation`, `SalienceContext`
- `ActionTendency`, `ActionReadiness` enum
- `HabituationConfig`, `HabituationState`
- `Drive`, `DriveCategory` enum
- `SchererAppraisalConfig`, `SalienceConfig`

**cognition** (implementations):
- `AppraisalTickParticipant`
- `DefaultSalienceStrategy`
- `SchererAppraisalStrategy`
- `AppraisalPromptSection`
- Updated `DriveOrchestrator` (dynamic drives)
- Removed `CharacterDrivePromptSection` (Stage 6, after Stage 2)

**cognitive-api** (unchanged — reuse existing types):
- `CognitiveEmotion`, `EmotionType`, `EmotionSource`, `PadProjection` — used as appraisal output types

**cognitive-index** (derivation):
- Updated `CognitiveDerivationEngine` — new `deriveHabituationConfig()` pathway
- Updated `CognitiveDefaults` — new `habituationConfig` field

## 7. Testing Strategy

New `CognitiveAppraisalTest` base class (not extending CognitiveEmergenceTest — different domain primitives). Scenario builder pattern:

**Determinism:** Tests use deterministic `SalienceStrategy` and `AppraisalStrategy` implementations (stubs with fixed computation rules, not LLM-backed). The test class names and assertion style reflect pipeline behavior verification with controlled inputs, not statistical emergence testing. This is explicitly NOT the measurement methodology — see §4.7 for empirical measurement via `classify_emotions.py`.

```java
class AppraisalBehaviorTest extends CognitiveAppraisalTest {

    @Test
    void highOpennessHabituatesFaster() {
        givenDisposition(highOpenness())
            .withDrive("curiosity", 0.8)
            .withObservation("examining the bookshelf")
            .repeatedTimes(5);

        thenEmotions()
            .showsDecreasingEngagement()
            .showsBoredomAfter(3);
        thenActionTendency()
            .shifts(ATTENDING, INTERRUPTION);
    }

    @Test
    void lowOpennessToleratesRepetition() {
        givenDisposition(lowOpenness())
            .withDrive("order", 0.7)
            .withObservation("examining the bookshelf")
            .repeatedTimes(5);

        thenEmotions()
            .showsStableEngagement();
        thenActionTendency()
            .remains(ATTENDING);
    }

    @Test
    void driveRelevantObservationHabituatesSlower() {
        givenDisposition(highOpenness())
            .withDrive("botany", 0.9)
            .withObservation("examining the garden plants")
            .repeatedTimes(5);

        thenEmotions()
            .showsSlowerDecay()  // domain modulation
            .noBoredomBefore(4);
    }

    @Test
    void protectionDriveNoticesDangerFirst() {
        givenDisposition(anyDisposition())
            .withDrive("protection", 0.8)
            .withObservation("The room is quiet. Clara's chair is empty. "
                + "Beautiful flowers on the table.");

        thenPerceivedSituation()
            .mentionsBefore("Clara", "flowers")  // protection biases salience
            .hasSalience("protection", greaterThan(0.7));
    }
}
```

**Test coverage requirements:**
- Personality × habituation: at least 4 personality archetypes (high-openness, low-openness, high-sensation-seeking, high-conscientiousness) × repeated observations
- Drive domain modulation: on-domain vs off-domain repetition tolerance
- Salience bias: different drives notice different things in the same observation
- Action tendency transitions: engagement → boredom → impatience → disengagement
- Cross-system: appraisal action tendencies don't contradict CAPS behavioral attractors
- Toggle isolation: each CognitionConfig toggle produces measurably different behavior
- Mood bridge feedback loop: appraisal emotions produce a `MoodSignal.DirectShift` that updates the next tick's `MoodState` — verifies end-to-end: disposition → appraisal → emotions → mood shift → next tick's `AppraisalContext.currentMood` reflects the shift

## 8. Implementation Staging

### Stage 1: Research synthesis (multi-agent debate)
Analyse 6 models for real-time LLM applicability. Builds on #407's consolidation-time synthesis. Output: `specs/issue-428-cognitive-appraisal-arch/2026-10-04-research-synthesis.md`

Research findings may revise the provisional SPI context records in §4 (e.g., which fields SalienceContext needs, whether AppraisalContext should carry per-SEC configuration). The SPI method signatures (`perceive()`, `appraise()`) are designed to survive research revision.

### Stage 2: SPI definition + drive model evolution
Define SPIs in cognition-api. Evolve DriveAxis to dynamic drives (§4.6). Update DriveOrchestrator. Add HabituationConfig to CognitiveDefaults (§4.5). Build CognitiveAppraisalTest base class (TDD). API-breaking stage — done early.

### Stage 3: Salience implementation
`DefaultSalienceStrategy` — perception fusion. Research determines whether LLM involvement needed for narrative generation.

### Stage 4: Appraisal implementation
`SchererAppraisalStrategy` — SEC pipeline informed by research synthesis. Delegates to GoalAppraisal/ActionAppraisal where applicable. Produces `CognitiveEmotion` instances using the existing EmotionType vocabulary.

### Stage 5: Habituation and personality parameterisation
HabituationConfig derivation in CognitiveDerivationEngine. Novelty tracking, expectation discrepancy. Heavy testing stage for personality × habituation.

### Stage 6: Prompt rendering + integration
AppraisalPromptSection, remove CharacterDrivePromptSection (depends on Stage 2 drive model being complete — §5.2), wire into CognitionCore via `chainSectionCustomizer` or native prompt section registration.

### Epic requirement coverage

| Epic requirement | Spec coverage |
|-----------------|---------------|
| Composable toggle architecture | §4.7 — CognitionConfig extensions + SchererAppraisalConfig |
| Sub-LLM pre-processing | §4.8 — SPIs support LLM-backed implementations; Stage 1 determines boundary |
| Emotion knowledge RAG | §4.9 — explicitly rejected per D2; revisit trigger documented |
| Measurement method | §4.7 — toggle configurations map to named experiments; existing classify_emotions.py infrastructure |

## 9. Wacky-Manor Constraints

All design decisions respect the empirical evidence from character emergence experiments:

| Constraint | How the architecture satisfies it |
|-----------|----------------------------------|
| Evocative, not analytical (P11) | Computational pipeline produces structured data; prompt renderer translates to character-subjective prose |
| Identity activation (P13) | LLM receives pre-processed emotional context, responds AS character |
| Less instruction beats more (P14) | AppraisalPromptSection replaces verbose drive descriptions with focused emotional state |
| Emotional echo (P19) | Appraisal emotions bridge to MoodOrchestrator via MoodSignal.DirectShift (§5.1 step 5); habituation state persists in ConcurrentHashMap across ticks |
| No structured fields (P9) | Appraisal output is narrative text, not fields for the LLM to fill |
| Monolithic drives regress (P37) | Drives simplified to type + intensity; appraisal computes emotions from them |

## 10. Dependencies and Blockers

- **#398** (memory seeding) — CLOSED, satisfied
- **#406** (emergent behavioral synthesis) — OPEN, CAPS engine landed (#408). #428 is complementary, not blocked.
- **#407** (psychology cause-effect models) — CLOSED, research synthesis to build on
- **#383** (OCC appraisal SPIs) — GoalAppraisal/ActionAppraisal remain as-is in mindmap-api. Appraisal pipeline reuses `CognitiveEmotion` and `EmotionType` from cognitive-api.
- **#408** (CAPS engine) — Dynamic CAPS → AppraisalWeights modulation deferred until CAPS attractor persistence is stable.

## References

- Lazarus, R.S. (1966). *Psychological Stress and Coping Process*
- Lazarus, R.S. (1991). Progress on a cognitive-motivational-relational theory of emotion. *American Psychologist*
- Scherer, K.R. (2001). *Appraisal Processes in Emotion*
- Scherer, K.R. (2019). The Emotion Process. *Annual Review of Psychology*
- Frijda, N.H. (1986). *The Emotions*
- Frijda, N.H. (2007). *The Laws of Emotion*
- Ortony, A., Clore, G.L., & Collins, A. (1988). *The Cognitive Structure of Emotions*
- Chain-of-Emotion (2024). PMC11086867
- casehubio/neocortex#383 — OCC GoalAppraisal/ActionAppraisal SPIs
- casehubio/neocortex#407 — Psychology cause-effect models synthesis (2026-10-02-psychology-cause-effect-models-design.md)
- casehubio/neocortex#408 — CAPS engine implementation
- casehubio/examples#97 — Wacky-manor character emergence experiments
- CognitionCore tick lifecycle (cognition-api/cognition modules)
- CognitiveDerivationEngine (cognitive-index module)
- CognitiveEmergenceTest (caps-testing module)
- DriveOrchestrator, DriveSource, DriveAxis (cognition-api/cognition modules)
- GoalAppraisal, ActionAppraisal, AppraisalWeights (mindmap-api/mindmap-intelligence modules)
- CognitiveEmotion, EmotionType, EmotionSource, PadProjection (cognitive-api module)
