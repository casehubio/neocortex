# Cognitive Search Budget — Design Spec

**Issue:** casehubio/neocortex#825
**Branch:** issue-765-cognitive-richness
**Date:** 2026-11-05

## Problem

Multiple cognitive subsystems produce motivation intensity (curiosity, goal pursuit, threat response, competence drive, relational obligation). There is no general model that converts motivation intensity into a search envelope the LLM operates within. Without this, the agent has no personality-shaped cognitive exploration style — a cautious agent and a bold agent approach the same information gap identically.

## Core Abstraction

The search budget is a 4-dimensional envelope that shapes **how** the LLM explores, independent of **what** it explores. Consumers provide the motivation intensity; this model provides the envelope.

### Four Parameters

Each parameter is orthogonal — Guilford (1967) measures fluency and flexibility as independent cognitive operations; depth and convergence are independent because fixation (depth=∞, convergence=never) requires them to vary independently.

| Parameter | What it controls | Literature |
|---|---|---|
| **Breadth** | How many parallel angles to pursue | Guilford (1967) fluency; DeYoung (2015) Openness/Intellect as cognitive exploration |
| **Laterality** | How far from the original topic to range | De Bono (1970) lateral thinking; DeYoung (2015) "leaky cognitive filtering" |
| **Depth** | How far to follow each thread before surfacing | Guilford (1967) elaboration; Conscientiousness → systematic search |
| **Convergence pressure** | When to stop exploring and synthesize | Charnov (1976) MVT patch-leaving; Pirolli & Card (1999) information foraging |

All parameters are in [0.0, 1.0].

## Architecture

### Two-Layer Pattern

Follows the established consolidation + tick pattern used by drive descriptions, CAPS attractors, and community summaries.

**Layer 1 — Consolidation time (background):**
A `SearchBudgetDescriptionGenerationPhase` at `@Priority(28)` computes the personality-derived search style baseline from PID-5 facets, DispositionAxes, and CAPS attractor state. An LLM call generates contextual cognitive-state prose describing what this agent's default search disposition *feels like*. Crystallizes to MindMap.

**Layer 2 — Tick time (per-interaction):**
A `SearchBudgetTickParticipant` in DERIVED phase computes numeric `SearchBudget` values from the CDE-derived `SearchBudgetConfig` defaults (personality baseline) + transient state (drive intensity, mood valence, goal urgency, appraisal action tendencies). Exposes the result via `currentBudget(agentId, tenantId)`. The crystallized prose from Layer 1 is used **only** by the prompt section for rendering — the tick participant computes numeric values independently (same separation as DriveOrchestrator computing DriveProfile independently from DriveDescriptionGenerationPhase prose). A `SearchBudgetPromptSection` with `BlockTag.SEARCH_BUDGET(415)` renders from the modulated numeric result + crystallized prose.

**Registration:** `CognitionCore.configureSearchBudget(DriveOrchestrator, MoodOrchestrator, CognitiveDefaultsRegistry, MindMapStore)` adds the participant to DERIVED phase. Must be called **after** `configureAppraisal()` and `configureGutFeeling()` to ensure intra-DERIVED ordering — `CognitionCore.runCustomParticipants()` executes DERIVED participants in registration order.

### Module Placement

| Component | Module | Rationale |
|---|---|---|
| `SearchBudget` record, `SearchMotivation` enum | cognition-api | Zero-dep type, alongside DriveProfile, ActionTendency |
| `SearchBudgetConfig` record | cognitive-api | Per-agent tuning, alongside HabituationConfig. cognitive-index already depends on cognitive-api; placing in cognition-api would create new dependency cognitive-index→cognition-api |
| `deriveSearchBudgetDefaults()` | cognitive-index (CDE) | Personality→budget baseline, alongside 12 existing derivation pathways |
| `SearchBudgetDescriptionGenerationPhase` | mindmap-intelligence | Consolidation phase, alongside DriveDescriptionGenerationPhase |
| `SearchBudgetTickParticipant` | cognition | DERIVED-phase participant, alongside AppraisalTickParticipant |
| `SearchBudgetPromptSection` | cognition | Prompt renderer, alongside BehavioralPromptSection |

**CognitiveDefaults integration:** Add `SearchBudgetConfig searchBudgetConfig` as field 18 on `CognitiveDefaults`. Add `withSearchBudgetConfig()` method following existing pattern. CDE's `derive()` stores the result; `CognitiveDefaultsRegistry` serves it to the tick participant. YAML parser extension supports per-agent SearchBudgetConfig overrides in cognitive-profiles/*.yaml.

### BlockTag

```java
SEARCH_BUDGET(415)  // Strategy tier, between STRATEGY(410) and ATTENTION(420)
```

Section calibration tier: **Core** — always present. `TierFilterCustomizer` suppresses CONTEXTUAL sections under high arousal, but the search budget is *most* informative under high arousal (threat → collapsed laterality, urgency → convergence acceleration). The section's content already reflects arousal-driven narrowing via tick-time modulation; suppressing it would remove guidance exactly when it matters most. Default tier for sections not in `TierFilterCustomizer.TIER_MAP` is CORE, so no map entry needed.

## Composition — No Custom Mechanism

The search budget does not compose multiple motivations itself. It reads already-composed outputs from upstream subsystems that each use principled internal composition:

| Subsystem | Output read | Literature basis for composition |
|---|---|---|
| `DriveOrchestrator` | `DriveProfile` (compositeMotivation, dominantDrive) | SDT (Deci & Ryan, 2000) |
| `AppraisalTickParticipant` | `AppraisalResult` (emotions, action tendencies) | Scherer SEC (2001) |
| `GoalPrioritizationPhase` | Goal node priority properties | Locke & Latham (2002) |
| `GutFeelingParticipant` | `GutFeeling` (valence, intensity) | Anthropic functional emotions (2026) |
| `MoodOrchestrator` | `MoodState` (PAD) | Russell (1980) circumplex |

**Source selection logic:** The `SearchBudget.source` is determined by highest-intensity signal across all subsystems: `max(DriveProfile.drives().values().intensity(), threatIntensity, goalUrgency)`. When the max comes from DriveProfile, source maps directly (CURIOSITY, COMPETENCE, AFFILIATION, AUTONOMY). When threat action tendency intensity (AVOIDANCE from `ActionReadiness`) exceeds all drive intensities, source=THREAT. When max goal priority exceeds all drives and threat, source=GOAL. The source determines the *character* of the search. The `compositeMotivation` + appraisal intensity + goal urgency determine the *size* of the budget. CAPS attractors provide the personality baseline.

**Grounding:** CAPS connectionist resolution (Mischel & Shoda, 1995) — recurrent network settles to satisfy multiple simultaneous constraints. Frijda's control precedence (1986, 2007) — highest urgency claims priority, others reinforce/attenuate. Schmidt & DeShon (2007) — resources shift toward largest discrepancy.

## Derivation Chain

### Trait Baseline (from CognitiveDerivationEngine)

Each parameter maps to specific PID-5 facets and DispositionAxes. Big Five trait names serve as theoretical grounding — the operational pathway works directly with PID-5 facets without constructing synthetic Big Five scores. Follows the pattern of `deriveExpressionStyles()`.

**Breadth** ← `GraphStructureDefaults.connectiveBias` (high → broad associative search) + PID-5: high Risk Taking → expansive, low Risk Taking + low Impulsivity → conservative.
- *Grounding:* Openness/Intellect (DeYoung 2015 CB5T; Grajzel et al. 2023 meta-analysis r=0.20 for divergent thinking)

**Laterality** ← PID-5: low Withdrawal + low Intimacy Avoidance → open to distant associations; high Suspiciousness → constrained.
- *Grounding:* Openness aesthetic/perceptual sensitivity, "leaky cognitive filtering" (DeYoung 2015). Same CDE pattern as `deriveExpressionStyles`.

**Depth** ← `DispositionAxes.ruleFollowing` (String→numeric via lookup: "strict"→0.8, "moderate"→0.5, "flexible"→0.2, following the pattern in `CognitiveDerivationEngine.deriveCbrStrategy()`) + PID-5: low Impulsivity + low Risk Taking → thorough; high Impulsivity → shallow-and-move-on.
- *Grounding:* Conscientiousness ↔ low Disinhibition (Krueger et al. 2012 PID-5 dimensional alignment)

**Convergence** ← `DispositionAxes.ruleFollowing` (same String→numeric lookup as Depth) modulated by PID-5: high Anxiousness + high Emotional Lability → convergence failure risk.
- *Grounding:* Conscientiousness × inverse Neuroticism; Davis & Nolen-Hoeksema (perseverative errors on WCST)

### Dynamic Modulation (from tick-time state)

| Input | Breadth | Laterality | Depth | Convergence |
|---|---|---|---|---|
| `dominantDrive==CURIOSITY` | ↑ boost | ↑ boost | — | ↓ delay |
| `dominantDrive==COMPETENCE` | — | — | ↑ boost | ↑ accelerate |
| `dominantDrive==AFFILIATION` | ↑ moderate boost | — | ↓ shallow (social scanning) | ↑ accelerate (consensus-seeking) |
| `dominantDrive==AUTONOMY` | — | ↑ boost (independent paths) | ↑ boost (self-directed depth) | ↓ delay (resist premature closure) |
| Positive mood valence | ↑ boost | ↑ boost | — | — |
| High threat (ActionReadiness.AVOIDANCE) | — | ↓ collapse to 0 | — | — |
| GutValence.AVOID | — | ↓ reduce | — | ↑ accelerate |
| GutValence.APPROACH | ↑ boost | — | — | — |
| High goal urgency + proximity | — | — | ↑ focus | ↑ accelerate |
| HabituationState failure | — | — | — | ↓ pathological: → 0 |

**Mood → exploration-exploitation:** Positive valence and moderate arousal promote exploration; negative valence promotes exploitation/perseveration. Grounded in Cognitive, Affective & Behavioral Neuroscience (2021).

## Intensity → Budget Function

Per-parameter activation functions. `CapsSettler` establishes the precedent of different activation shapes for different node types (`Math.tanh()` for BIPOLAR, bare `logistic()` for UNIPOLAR). The search budget extends this with a parameterized logistic `1/(1+exp(-k*(x-x0)))` where k controls steepness and x0 the midpoint — a generalisation of CapsSettler's bare logistic (which is equivalent to k=1, x0=0).

| Parameter | Function | Shape rationale |
|---|---|---|
| **Breadth** | Logistic, midpoint 0.5, tunable k | Appetitive, smoothly graded |
| **Laterality** | Logistic with threat-collapse floor | Clamps to 0 under high threat (Frijda approach/avoidance) |
| **Depth** | Logistic, lower k (near-linear mid-range) | Systematic search is more linearly graded with effort |
| **Convergence** | Piecewise: logistic above pathological threshold, floor at 0 below | Models qualitative difference between normal convergence and fixation/rumination |

Steepness k, midpoint, and pathological threshold are tunable per-agent via `SearchBudgetConfig`. Defaults calibrated from existing `AppraisalThresholds.driveActivationThreshold()` (0.5).

## Pathological Boundary

Not a separate mode — the natural consequence of maximal intensity with no resolution. The same parameters at extreme values produce pathological states:

| State | Breadth | Laterality | Depth | Convergence | Mechanism |
|---|---|---|---|---|---|
| Healthy exploration | 0.5-0.7 | 0.3-0.6 | 0.3-0.5 | 0.4-0.6 | Normal intensity, gap closing |
| Intense pursuit | 0.3-0.5 | 0.2-0.4 | 0.6-0.8 | 0.2-0.4 | High intensity, gap partially closing |
| **Fixation** | 0.0-0.1 | 0.0 | 1.0 | 0.0 | Maximal intensity + HabituationState failure |
| **Rumination** | 0.0-0.1 | 0.0 | 1.0 | 0.0 | Maximal intensity + no gap — depth is high but HabituationState doesn't suppress, so the same ground is retraced |

**Mechanism:** High PID-5 Anxiousness + `HabituationState` failure + unresolved drive intensity → convergence function's pathological floor activates → breadth collapses, laterality collapses.

**HabituationState failure defined operationally:** `HabituationState.observationCounts.get(topic) > N` (topic has been observed many times) AND `HabituationState.noveltyScores.get(topic) > M` (novelty hasn't decayed despite repeated observation). Default thresholds: N=5, M=0.6. The pathological threshold in `SearchBudgetConfig` (default 1.4) is the sum `Anxiousness.score + (noveltyScore - habituationRate * observationCount)` — when this sum exceeds the threshold, convergence collapses.

**Fixation vs rumination distinction:** Both produce identical numeric budgets (breadth≈0, laterality=0, depth=1.0, convergence=0). The distinction is observable via `HabituationState.observationCounts`: fixation has low counts (the topic is new but the agent can't stop), rumination has high counts (the topic has been reprocessed many times). The consolidation-time LLM prose captures this distinction — fixation prose emphasizes the pull of the unresolved question, rumination prose emphasizes the repetitive return.

## Prompt Rendering

The crystallized search style prose is generated by the consolidation phase LLM call, following the Anthropic "Functional Emotions" finding (April 2026): cognitive state descriptions causally influence LLM behavior through internal representations.

The rendering describes the **felt cognitive state**, not directives:
- Large budget, curiosity-dominant: *"This pulls at your attention from multiple angles — the obvious answer isn't enough, and connections to adjacent areas keep surfacing."*
- Large budget, competence-dominant: *"You want to trace this to its source. Surface answers aren't satisfying — there's a mechanism underneath."*
- Small budget: *"This registers, but doesn't hold your attention."*
- Pathological (fixation): *"You keep coming back to this. Other thoughts won't stick."*

The specific prose is generated by the LLM from the parameter combination at consolidation time — not from templates. The tick-time modulation adjusts which crystallized content to emphasize based on transient state.

## Attention Interaction

When an attention threshold crossing (progressive attention model, #381) interrupts a task with active convergence state:

1. Convergence pressure is **checkpointed** (not reset)
2. The attention push items are evaluated through the search budget's breadth/depth parameters
3. If the original task remains active after attention processing, convergence pressure resumes from checkpoint

**Storage:** Convergence checkpoint is part of the `SearchBudgetTickParticipant`'s per-agent `ConcurrentHashMap` state (same pattern as `AppraisalTickParticipant`). The checkpoint is the convergence value + dominant source at interruption time, keyed by agent+tenant.

**Grounding:** Frijda's control precedence (1986, 2007) — the most urgent signal claims priority, but prior commitments are not erased.

## Consumers

| Consumer | Motivation source | How it provides intensity |
|---|---|---|
| **#596 Information-gap curiosity** | Knowledge gap × relevance | `DriveProfile.drives().get(CURIOSITY).intensity()` |
| **Goal pursuit** | Goal urgency × feasibility | Goal node `priority` property from GoalPrioritizationPhase |
| **Threat response** | Threat appraisal × controllability | `AppraisalResult` action tendencies (`ActionReadiness.AVOIDANCE`) |
| **Competence drive** | Skill gap × opportunity | `DriveProfile.drives().get(COMPETENCE).intensity()` |
| **Relational obligation** | Relationship strength × other's need | `DriveProfile.drives().get(AFFILIATION).intensity()` |

Each consumer computes intensity through its own literature-grounded mechanism. This model converts that intensity into a search envelope.

## Types (cognition-api)

```java
public enum SearchMotivation {
    CURIOSITY,   // from DriveProfile (DriveAxis.CURIOSITY)
    COMPETENCE,  // from DriveProfile (DriveAxis.COMPETENCE)
    AFFILIATION, // from DriveProfile (DriveAxis.AFFILIATION)
    AUTONOMY,    // from DriveProfile (DriveAxis.AUTONOMY)
    THREAT,      // from AppraisalResult (ActionReadiness.AVOIDANCE intensity)
    GOAL         // from GoalPrioritizationPhase max priority
}

public record SearchBudget(
    double breadth,           // [0,1] — parallel angles
    double laterality,        // [0,1] — distance from topic
    double depth,             // [0,1] — thread follow-through
    double convergence,       // [0,1] — synthesis pressure (0 = fixation)
    SearchMotivation source,  // dominant motivation shaping search character
    Instant evaluatedAt
) { ... }

public record SearchBudgetConfig(
    double breadthSteepness,      // logistic k (default 6.0, from CapsSettler logistic range)
    double breadthMidpoint,       // logistic x0 (default 0.5, from driveActivationThreshold)
    double lateralitySteepness,   // (default 6.0)
    double lateralityMidpoint,    // (default 0.5)
    double lateralityThreatFloor, // threat intensity above which laterality clamps to 0 (default 0.7)
    double depthSteepness,        // (default 3.0, lower k for near-linear mid-range)
    double depthMidpoint,         // (default 0.5)
    double convergenceSteepness,  // (default 6.0)
    double convergenceMidpoint,   // (default 0.5)
    double pathologicalThreshold  // anxiousness + habituation failure sum above which convergence → 0 (default 1.4)
) {
    public static SearchBudgetConfig defaults() {
        return new SearchBudgetConfig(6.0, 0.5, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.5, 1.4);
    }
}
```

## Excluded — Insufficient Evidence

- **Extraversion → search parameters**: r=0.09 for divergent thinking (Grajzel et al. 2023). Too weak for reliable mapping.
- **Agreeableness → search parameters**: Context-dependent (Myszkowski et al. 2015 — managerial problem-solving, not general cognitive search). Negatively related to divergent thinking but positively to convergent-integrative; the sign depends on the task.

If future research strengthens these, they can be added to the derivation chain.

## References

- Guilford, J.P. (1967). *The Nature of Human Intelligence*. McGraw-Hill. — fluency/flexibility/elaboration independence
- De Bono, E. (1970). *Lateral Thinking*. Harper & Row. — laterality as distance from problem
- Charnov, E.L. (1976). Optimal foraging, the marginal value theorem. *Theoretical Population Biology*, 9(2), 129-136. — patch-leaving / convergence
- Mischel, W. & Shoda, Y. (1995). A cognitive-affective system theory of personality. *Psychological Review*, 102(4), 246-268. — CAPS logistic activation, constraint satisfaction
- Shoda, Y. & LeeTiernan, S. (2002). What remains invariant? *Personality and Social Psychology Review*, 6(4), 316-325. — CAPS recurrent network settling
- Frijda, N.H. (1986). *The Emotions*. Cambridge University Press. — control precedence, action readiness
- Frijda, N.H. (2007). *The Laws of Emotion*. Erlbaum. — control precedence claiming priority
- Pirolli, P. & Card, S.K. (1999). Information foraging. *Psychological Review*, 106(4), 643-675. — MVT for cognitive search
- DeYoung, C.G. (2015). Cybernetic Big Five Theory. *Journal of Research in Personality*, 56, 33-58. — Openness as cognitive exploration, dopamine
- Deci, E.L. & Ryan, R.M. (2000). The "what" and "why" of goal pursuits. *Psychological Inquiry*, 11(4), 227-268. — SDT drive composition
- Grajzel, K. et al. (2023). The Big Five and divergent thinking: A meta-analysis. *Personality and Individual Differences*. — Openness r=0.20, Extraversion r=0.09
- Davis, R.N. & Nolen-Hoeksema, S. (1998). Cognitive inflexibility among ruminators. *Cognitive Therapy and Research*, 22(4), 419-432. — perseverative errors, convergence failure
- Schmidt, A.M. & DeShon, R.P. (2007). What to do? *Journal of Applied Psychology*, 92(4), 928-941. — discrepancy-based resource allocation
- Louro, M.J., Pieters, R. & Zeelenberg, M. (2007). Dynamics of multiple-goal pursuit. *JPSP*, 93(2), 174-193. — emotion × proximity → effort allocation
- Krueger, R.F. et al. (2012). Initial construction of a maladaptive personality trait model. *Psychological Medicine*, 42(9), 1879-1890. — PID-5 to Big Five dimensional alignment
- Anthropic (2026). Emotion concepts and their function in a large language model. — cognitive state descriptions causally influence LLM behavior
- Kahneman, D. (2011). *Thinking, Fast and Slow*. Farrar, Straus and Giroux. — System 1/2 threshold
- Myszkowski, N. et al. (2015). Managerial creative problem solving and the Big Five. *Journal of Management Development*, 34(6), 674-684. — Agreeableness differential (excluded)
- Wilke, A. et al. (2009). Fishing for the right words. *Cognitive Science*, 33(7), 1266-1286. — foraging stopping rules in internal search
- Cognitive, Affective, & Behavioral Neuroscience (2021). Exploration-exploitation trade-off in a foraging task is affected by mood-related arousal and valence. — mood → exploration
