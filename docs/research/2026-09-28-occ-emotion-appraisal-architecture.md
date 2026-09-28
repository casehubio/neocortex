# OCC Emotion Appraisal Architecture in casehub-neocortex

**Date:** 2026-09-28
**Status:** Implemented (prospect-based + agent-based branches); object-based branch not yet implemented
**Covers:** #345 (goal cognition), #381 (attention model), #382 (personality calibration), #383 (agent-based emotions), #384 (compound emotions)

## 1. Introduction

The Ortony, Clore & Collins (1988) model organises emotions into three appraisal branches based on what is being evaluated:

| Branch | Stimulus | Appraisal Variable | Emotions |
|--------|----------|-------------------|----------|
| **Events** | Consequences for goals | Desirability, likelihood | Hope, Fear, Satisfaction, Disappointment, Relief, Fears-Confirmed |
| **Actions** | Agent behaviour against standards | Praiseworthiness | Pride, Shame, Admiration, Reproach |
| **Objects** | Entities against attitudes | Appealingness | Love, Hate |

Additionally, OCC defines **well-being emotions** (Joy, Distress) as undifferentiated positive/negative reactions, **fortunes-of-others emotions** (Happy-For, Pity, Resentment, Gloating) mediated by relationship valence, and **compound emotions** that arise when event appraisal and action attribution co-occur (Gratification, Remorse, Gratitude, Anger).

casehub-neocortex implements the first two branches and all compound types. This document describes the architecture, the scoring heuristics, the personality modulation mechanism, and the integration points with the broader cognitive system.

## 2. Dimensional Affect Model

All emotions map to the PAD (Pleasure-Arousal-Dominance) dimensional space via the ALMA model (Gebhard 2005). `AlmaPadTable` provides the static lookup table:

| Emotion | Pleasure | Arousal | Dominance | OCC Branch |
|---------|----------|---------|-----------|------------|
| Hope | 0.2 | 0.2 | -0.1 | Prospect |
| Fear | -0.64 | 0.60 | -0.43 | Prospect |
| Satisfaction | 0.3 | -0.2 | 0.4 | Prospect |
| Disappointment | -0.3 | 0.1 | -0.4 | Prospect |
| Relief | 0.2 | -0.3 | -0.4 | Prospect |
| Fears-Confirmed | -0.5 | -0.3 | -0.7 | Prospect |
| Joy | 0.4 | 0.2 | 0.1 | Well-being |
| Distress | -0.4 | -0.2 | -0.5 | Well-being |
| Pride | 0.4 | 0.3 | 0.3 | Agent (self) |
| Shame | -0.3 | 0.1 | -0.6 | Agent (self) |
| Admiration | 0.5 | 0.3 | -0.2 | Agent (other) |
| Reproach | -0.3 | -0.1 | 0.4 | Agent (other) |
| Gratification | 0.6 | 0.5 | 0.4 | Compound |
| Remorse | -0.3 | 0.1 | -0.6 | Compound |
| Gratitude | 0.4 | 0.2 | -0.3 | Compound |
| Anger | -0.51 | 0.59 | 0.25 | Compound |
| Happy-For | 0.4 | 0.2 | 0.2 | Fortunes (liked) |
| Pity | -0.4 | -0.2 | -0.5 | Fortunes (liked) |
| Resentment | -0.2 | -0.3 | -0.2 | Fortunes (disliked) |
| Gloating | 0.3 | -0.3 | -0.1 | Fortunes (disliked) |
| Love | 0.4 | 0.16 | -0.24 | Object |
| Hate | -0.6 | 0.6 | 0.3 | Object |

`AlmaPadTable.project(type, intensity)` scales the base PAD vector by emotion intensity, producing a continuous mapping from discrete emotion categories to dimensional affect.

Each emotion instance is a `CognitiveEmotion` record:

```
CognitiveEmotion(type, intensity ∈ [0,1], subjectId, onset, source, pad)
```

`EmotionSource` distinguishes the appraisal process:
- `INTRINSIC` — self-directed (Hope, Fear, Pride, Shame, Joy, Distress)
- `EMPATHIC` — empathy for another's situation (Pity, Happy-For, Resentment, Gloating)
- `ATTRIBUTED` — judgment of another's action against standards (Admiration, Reproach)

## 3. Branch 1: Prospect-Based Emotions (Events → Goals)

### 3.1 SPI

```java
@FunctionalInterface
public interface GoalAppraisal {
    List<CognitiveEmotion> appraise(MindMapNode goal, AppraisalContext context);
}
```

The SPI receives a goal node from the MindMap knowledge graph and an `AppraisalContext` carrying agent identity, mood baseline, surfacing history, relationship scores, and personality-derived `AppraisalWeights`.

### 3.2 Heuristic Scoring (HeuristicGoalAppraisal)

The heuristic maps goal state to emotions using goal properties (priority, urgency, feasibility, status):

| Goal Status | Emotions Produced | Intensity Formula |
|-------------|-------------------|-------------------|
| Active | Hope + Fear | Hope: `priority × feasibility × (1 - urgency×0.5×uw)`. Fear: `priority × urgency × uw × surfacingGapFactor` when urgency exceeds threshold |
| Blocked | Distress | `priority × urgency × uw × (1 - feasibility)` |
| Completed | Satisfaction | `priority` |
| Abandoned | Disappointment | `priority × 0.7` |
| Dormant | Distress (low) | `priority × 0.15` |

Where `uw` = `AppraisalWeights.urgencyWeight` (personality-modulated) and `surfacingGapFactor` = `surfacingCount / (surfacingCount + 1)` captures how often the goal has been surfaced without progress.

### 3.3 Empathic Appraisal (Fortunes of Others)

When a goal has an `affected-entity` property and the appraising agent has a positive relationship score with that entity, `HeuristicGoalAppraisal` produces Pity:

```
pityIntensity = relationshipScore × rw × urgency × (1 - feasibility)
```

Where `rw` = `AppraisalWeights.relationshipWeight`. Pity is sourced as `EMPATHIC`.

### 3.4 Execution Context

`GoalAffectPhase` (consolidation priority 37) iterates all nodes in the GOAL subgraph per tenant. When a `GoalAppraisal` implementation is available via CDI, it delegates to OCC appraisal; otherwise falls back to legacy status-based PAD computation. The dominant emotion's PAD is written to the goal node via `store.updateNode()`, and `AFFECT_CHANGE` attention signals are emitted.

`AffectTrajectoryDecorator` (priority 65) intercepts PAD changes on any node update and persists them as `domain="affect"` memories, enabling trajectory analysis over time.

## 4. Branch 2: Agent-Based Emotions (Actions → Standards)

### 4.1 SPI

```java
@FunctionalInterface
public interface ActionAppraisal {
    List<CognitiveEmotion> appraise(ActionContext context);
}
```

`ActionContext` carries the appraisal inputs:

| Field | Type | Semantics |
|-------|------|-----------|
| actingAgentId | String | Who performed the action |
| apprasingAgentId | String | Who is feeling the emotion |
| tenantId | String | Tenant isolation |
| turnId | String | Conversation turn (compound detection key) |
| actionDescription | String | Human-readable description |
| capability | String (nullable) | What capability was exercised |
| outcome | ActionOutcome | SUCCESS, FAILURE, NEUTRAL |
| goalRelevance | double [-1, 1] | How much the action helped/harmed the appraising agent's goals |
| moodBaseline | PadProjection | Agent's resting emotional state |
| weights | AppraisalWeights | Personality-derived thresholds |
| timestamp | Instant | When the action occurred |

`isSelfAction()` returns true when `actingAgentId.equals(apprasingAgentId)` — this determines whether Pride/Shame (self) or Admiration/Reproach (other) is produced.

### 4.2 Standards

In OCC theory, agent-based emotions arise from judging actions against *standards* — norms, values, and rules the agent holds. In this implementation, standards are layered:

**Layer 1: Goal-derived (base signal).** An action that advances a goal is praiseworthy; one that harms a goal is blameworthy. `goalRelevance` is computed by matching the action's `capability` against active goal nodes in the GOAL subgraph. The sign follows outcome polarity: SUCCESS produces positive relevance, FAILURE produces negative.

**Layer 2: Personality modulation (threshold calibration).** `AppraisalWeights` carries two standards-related fields — `selfStandardsStrictness` and `otherStandardsStrictness` — each in [0.5, 2.0]. These modulate the thresholds asymmetrically (see Section 4.3).

This two-layer design avoids requiring explicit norm nodes or a moral reasoning subsystem while still producing personality-differentiated emotional responses. The SPI is the natural extension point for future norm-based standards.

### 4.3 Asymmetric Thresholds

The key insight from OCC theory: agents with high standards feel negative self-assessment emotions more easily but require greater achievement for positive ones. The implementation captures this via valence-dependent asymmetry:

```
praiseworthiness = outcomePolarity × |goalRelevance|

strictness = isSelfAction ? selfStandardsStrictness : otherStandardsStrictness

positiveThreshold = 0.2 × strictness     (strict → high bar → hard to impress)
negativeThreshold = 0.2 / strictness     (strict → low bar → easy to shame)

positiveIntensity = clamp(praiseworthiness / strictness)    (dampened)
negativeIntensity = clamp(|praiseworthiness| × strictness)  (amplified)
```

Example with `selfStandardsStrictness = 2.0`:
- Shame threshold: 0.1 — small failures trigger Shame easily
- Pride threshold: 0.4 — only significant achievements trigger Pride
- Shame intensity is amplified (×2.0); Pride intensity is dampened (÷2.0)

A flexible agent (`selfStandardsStrictness = 0.7`) is the inverse: hard to shame, easy to please.

### 4.4 Emotion Mapping

| Praiseworthiness | Self-action | Other-action |
|-----------------|-------------|--------------|
| Above positive threshold | PRIDE (INTRINSIC) | ADMIRATION (ATTRIBUTED) |
| Below negative threshold | SHAME (INTRINSIC) | REPROACH (ATTRIBUTED) |
| Between thresholds | No emotion | No emotion |
| Zero (no goal relevance) | No emotion | No emotion |

### 4.5 Execution Context

`ActionAppraisalObserver` is a CDI `@ApplicationScoped` observer that fires in real-time on `ExperienceRecorded` events, filtering for `Outcome` event types. Two paths within the same handler:

**Self-appraisal:** Every Outcome event triggers self-appraisal — the acting agent evaluates its own action's result.

**Other-appraisal:** When `metadata.get(TARGET_AGENT)` is present and differs from `agentId`, the observer also appraises the other agent's action from the perspective of the recording agent.

**ActionOutcome mapping** uses a two-stage strategy:
1. Explicit metadata (`outcome-status` key) → direct mapping
2. Confidence fallback → `≥ 0.7` SUCCESS, `≤ 0.3` FAILURE, else NEUTRAL

**Goal-relevance computation** iterates all active goals in the GOAL subgraph, matching `outcome.capability()` against goal names and `capability` properties (case-insensitive contains). The highest-magnitude match is used (max absolute value preserves the strongest signal).

## 5. Compound Emotions

OCC compound emotions arise when event appraisal and action attribution co-occur for the same event:

| Compound | Components | Meaning |
|----------|------------|---------|
| Gratification | Joy + Pride | "My action achieved my goal" |
| Remorse | Distress + Shame | "My action harmed my goal" |
| Gratitude | Joy + Admiration | "Your action helped my goal" |
| Anger | Distress + Reproach | "Your action harmed my goal" |

### 5.1 Inline Detection

Rather than coordinating between GoalAffectPhase (prospect emotions) and ActionAppraisalObserver (agent emotions), compound detection is performed inline within `HeuristicActionAppraisal`. The same `ActionContext` that determines praiseworthiness also carries `goalRelevance` and `outcome` — sufficient to infer whether a co-occurring prospect emotion would exist.

Compound condition: base agent emotion is produced AND `|goalRelevance| > 0.3`:

| Base Emotion | Compound Condition | Compound Produced |
|---|---|---|
| PRIDE | outcome = SUCCESS, goalRelevance > 0.3 | GRATIFICATION |
| SHAME | outcome = FAILURE, goalRelevance < -0.3 | REMORSE |
| ADMIRATION | goalRelevance > 0.3 | GRATITUDE |
| REPROACH | goalRelevance < -0.3 | ANGER |

Compound intensity = `max(baseIntensity, |goalRelevance|)`.

Both the base emotion and the compound are returned — in OCC theory, these are distinct emotional responses that genuinely co-occur. GoalAffectPhase independently produces prospect emotions (Joy/Distress) for the same goal state change. The combined mood impact from multiple concurrent emotions is the intended model.

## 6. Personality-Driven Calibration

`AppraisalWeights` encodes five personality-derived parameters:

| Weight | Derived From | Affects |
|--------|-------------|---------|
| urgencyWeight | JPAF function profile (Se, Ni contributions) | Hope/Fear intensity scaling |
| relationshipWeight | JPAF function profile (Fe, Fi contributions) | Pity intensity scaling |
| fearOnsetThreshold | JPAF function profile (Ni, Se contributions) | Fear activation point |
| selfStandardsStrictness | DispositionAxes (ruleFollowing) | Pride/Shame thresholds |
| otherStandardsStrictness | DispositionAxes (ruleFollowing, socialOrient) | Admiration/Reproach thresholds |

### 6.1 JPAF Derivation (Prospect Weights)

The first three weights are derived from the Jungian Personality Assessment Framework (JPAF) cognitive function profile via weighted average over function-specific contribution maps:

```
urgencyRaw = Σ(weight_i × URGENCY_CONTRIBUTION[function_i]) / totalWeight
urgencyWeight = max(1.0 + urgencyRaw × SCALE_FACTOR, 0.1)
```

Where `SCALE_FACTOR = 1.5` and contribution maps encode empirically-grounded Big Five → appraisal chain relationships. For example, Se (Extraverted Sensing) contributes +1.0 to urgency (high Se types react quickly to environmental pressure), while Te (Extraverted Thinking) contributes +1.0 to urgency (structured decision-making under time pressure).

### 6.2 DispositionAxes Derivation (Standards Weights)

Standards strictness is derived from framework-agnostic disposition axes rather than JPAF functions, because standards-related behaviour maps directly to higher-order dispositions:

| Axis Value | selfStandardsStrictness | otherStandardsStrictness |
|-----------|------------------------|-------------------------|
| ruleFollowing: strict | 1.6 | 1.4 |
| ruleFollowing: moderate | 1.0 | 1.0 |
| ruleFollowing: flexible | 0.7 | 0.6 |

`socialOrient` applies a modifier to other-strictness:
- cooperative → -0.2 (more forgiving of others)
- competitive → +0.2 (harsher judgment of others)

All values clamped to [0.5, 2.0].

This separation — JPAF for prospect weights, DispositionAxes for standards weights — follows the same derivation pattern as CBR strategy (from `ruleFollowing`) and social cognition (from `socialOrient`), keeping personality modulation consistent across cognitive subsystems.

## 7. Integration with the Cognitive Architecture

### 7.1 Attention Pipeline

Both GoalAffectPhase and ActionAppraisalObserver emit `AttentionSignal`s (category: `AFFECT_CHANGE`) for significant emotional events. The `CognitiveAttentionAccumulator` aggregates signals per principal, with adaptive thresholds — signals above the agent's urgency P75 lower the accumulation threshold, enabling faster attention firing for emotionally significant periods.

### 7.2 Mood System

`MoodState` represents the agent's current emotional state as a PAD vector with optional `activeContextIds` for domain-partitioned correlation. `MoodBaseline` provides a per-agent resting point. `MoodDecay` applies exponential decay toward the baseline. The mood system receives attention signals from both appraisal paths — the combined emotional impact from concurrent emotions produces richer mood dynamics than either path alone.

### 7.3 Affect Trajectory

`AffectTrajectoryDecorator` captures PAD changes as `domain="affect"` memories, enabling:
- `AffectTrajectoryAnalyzer`: slope, volatility, trend analysis over time
- `CuriositySignalGenerator`: affect-dampened curiosity (worsening affect boosts curiosity, improving dampens)
- `DomainActivation`: DTW correlation between subgraph affect trajectories and mood

### 7.4 Social Cognition Bridge

The compound emotions (Gratitude, Anger) are the emotional substrates of trust and conflict:
- **Gratitude** (Admiration + Joy) = "your action helped me achieve my goal" — strengthens trust
- **Anger** (Reproach + Distress) = "your action harmed my goal" — erodes trust

These connect to the `RelationshipEvent` pipeline and the `AgentTrustProvider` SPI, enabling emotion-driven trust dynamics. The `TrustWeightedCbrRecordStore` modulates CBR retrieval by source trust — emotions that produce trust changes propagate through the entire case-based reasoning system.

## 8. What Is Not Yet Implemented

| Component | Status | Depends On |
|-----------|--------|-----------|
| Object-based emotions (Love, Hate) | Not implemented | Attitude model — no infrastructure exists |
| Norm-based standards | Not implemented | Explicit norm nodes or moral reasoning SPI |
| ActionReconciliationPhase | Deferred | Cursor-based scan for missed events |
| Embedding-based goal matching | Deferred | Replace capability string matching with semantic similarity |
| Cross-phase compound detection | Not needed | Inline detection from same inputs is sufficient |
| LLM-based praiseworthiness | Out of scope | Blocks-layer CognitionCore concern |

## 9. Design Decisions

The full decision record (15 decisions, validated by standard review with 2 rounds) is at `docs/specs/issue-383-agent-occ-emotions/decisions.md`. Key decisions:

**D1: Additive SPI over unified framework.** ActionAppraisal is a separate SPI parallel to GoalAppraisal, not a generic `CognitiveAppraisal<C>`. The inputs differ fundamentally (goal node vs action context), and only two specialisations exist — generics would complicate CDI injection without payoff.

**D5: Single-trigger observer over dual-trigger.** Both self and other-appraisal fire from the same `ExperienceRecorded` event. The original design used `RelationshipRecorded` for other-appraisal, but `RelationshipProcessor` strips structured metadata (`Map.of()`), losing the capability and result fields needed for goal-relevance computation.

**D6: Valence-dependent asymmetric thresholds.** Strict agents have asymmetric sensitivity: low threshold for Shame/Reproach (easy trigger) but high threshold for Pride/Admiration (hard trigger). This matches OCC theory where high standards produce asymmetric emotional sensitivity.

**D9: EmotionSource.ATTRIBUTED over reusing EMPATHIC.** Empathy (Pity — "what happened to them?") and attribution (Reproach — "what did they do?") are fundamentally different appraisal processes. A consumer filtering by source should not conflate the two.

## References

- Ortony, A., Clore, G. L., & Collins, A. (1988). *The Cognitive Structure of Emotions*. Cambridge University Press.
- Gebhard, P. (2005). ALMA — A Layered Model of Affect. *Proceedings of AAMAS 2005*.
- Mehrabian, A. (1996). Pleasure-arousal-dominance: A general framework for describing and measuring individual differences in temperament. *Current Psychology*, 14(4), 261-292.
- `cognitive-api/src/main/java/io/casehub/neocortex/cognitive/AlmaPadTable.java` — PAD projection table
- `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/GoalAppraisal.java` — prospect appraisal SPI
- `mindmap-api/src/main/java/io/casehub/neocortex/mindmap/ActionAppraisal.java` — action appraisal SPI
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/HeuristicGoalAppraisal.java` — prospect scoring
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/HeuristicActionAppraisal.java` — action scoring
- `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/ActionAppraisalObserver.java` — real-time observer
- `cognitive-index/src/main/java/io/casehub/neocortex/cognitive/index/CognitiveDerivationEngine.java` — personality derivation
- `docs/specs/issue-383-agent-occ-emotions/decisions.md` — 15 validated design decisions
