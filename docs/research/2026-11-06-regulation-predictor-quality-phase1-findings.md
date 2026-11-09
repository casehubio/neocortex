# Regulation Predictor Quality — Phase 1 Findings

**Issue:** #830 — fix: regulation predictor quality
**Date:** 2026-11-06
**Phase:** 1 of 2 (LLM guidance improvement)
**Predecessor:** #818 — automatic regulation (closed), which built RegulationStrategyPredictor

## Problem Statement

RegulationStrategyPredictor (#818) uses boolean classification — `isSuppressor()` and `isReappraiser()` — based on disposition axis signal counting. A personality is a "suppressor" if it has 3+ suppression signals (avoidant conflict mode, conservative risk appetite, competitive orientation) and fewer than 2 reappraisal-enabling signals. A "reappraiser" has the inverse.

This works for clear personality profiles: analytical+strict+cooperative → reappraiser → cognitive change. Avoidant+conservative → suppressor → response modulation. But it creates two categories of weakness:

### Weakness 1: Suppressor + shame falls through entirely

Shame is self-evaluative — the emotion IS about the self's failure against its own standards. The only healthy regulation engages with that self-evaluation (cognitive change). But suppressors can't execute cognitive change — they lack the analytical/reflective traits that power reappraisal.

This creates a fundamental mismatch: the personality's natural strategy (suppress) doesn't work for shame ("shame festers when suppressed" — judge score 2-3 across 7 runs), and the effective strategy (reappraise) doesn't work for this personality (can't execute — judge score 2-3).

The predictor correctly returns `Optional.empty()` — it recognises it can't confidently predict. But the LLM then gets no personality context and makes an uninformed choice.

### Weakness 2: Stable 4s that never reach 5

Two cases consistently score 4/5 across judge runs:
- **suppressor-anger → RM (4):** Judge notes CC "also viable at moderate intensity" — the boolean classification misses that some suppressors have partial reappraisal capacity
- **reappraiser-reproach → CC (4):** Judge notes strict rule-following resists reframing genuine standards violations — the predictor doesn't account for when reappraisal conflicts with the personality's values

## What Changed in Phase 1

Two conditional sections added to `EmotionRegulationGenerationPhase.buildUserMessage()` when the predictor falls through (returns `Optional.empty()`):

### 1. Disposition axes injection

When `pinnedStrategy == null` and axes are available, the LLM receives:

```
Personality disposition (predictor did not pin a strategy — use these to 
select a personality-congruent regulation):
  conflict-mode: avoidant
  social-orient: cooperative
  risk-appetite: conservative
  autonomy: low
  rule-following: moderate
```

The explicit "predictor did not pin a strategy" phrasing signals to the LLM that this is a case requiring careful personality-aware reasoning, not a default free choice.

### 2. Self-conscious emotion guidance

When the dominant emotion is SHAME or REMORSE and no strategy is pinned:

```
Self-conscious emotion guidance: Shame and remorse are self-evaluative — 
the emotion IS about the self's failure against its own standards. Pure 
suppression leaves the self-evaluation unaddressed (Webb et al. 2012). For 
avoidant personalities, temporal containment is adaptive: contain the 
expression now, with awareness that the self-evaluation will be processed 
when cognitive resources are available (Nathanson 1992 compass of shame; 
Yang et al. 2025). This is not suppression-as-avoidance — it is staged 
regulation where containment enables later reappraisal.
```

### Wiring

`EmotionRegulationTickParticipant.tick()` now:
1. Extracts the peak emotion earlier in the method (before tryPredictStrategy)
2. When the predictor falls through, retrieves `DispositionAxes` from `defaultsRegistry`
3. Passes both axes and dominant `EmotionType` through to the 9-parameter `generate()` overload
4. The generation phase passes them through `invokeLlm()` to the 10-parameter `buildUserMessage()`

## Literature Grounding

### Webb, Miles, & Sheeran (2012) — strategy effectiveness meta-analysis
*Psychological Bulletin*, 138(4), 775-808.

306 experimental comparisons. Cognitive change overall (d+=0.36) outperforms response modulation (d+=0.16). But suppression of *expression* specifically (d+=0.32) narrows the gap considerably. The critical finding for shame: emotion type moderates strategy effectiveness. Self-evaluative emotions respond poorly to strategies that don't engage with the self-evaluation.

### Nathanson (1992) — compass of shame
*Shame and Pride: Affect, Sex, and the Birth of the Self*

Four shame responses: withdrawal, avoidance, attack-self, attack-other. Avoidance is explicitly described as a *temporary* strategy that "eventually allows the individual to reappraise the situation" — a documented two-stage temporal sequence. This is the literature basis for temporal containment: avoidance is not the endpoint, it's the bridge to later reappraisal.

### Yang et al. (2025) — neural temporal dynamics
*Electrocortical modulation of reappraisal and suppression in avoidant and secure attachment.*

ERP study showing avoidant individuals process suppression effectively in early phases (300-500ms, reduced LPP) but it decays in later phases (500-800ms, stronger LPP). Reappraisal has more sustained effects for secure attachment. This provides neural evidence that temporal sequencing (suppress early, reappraise later) matches the actual processing dynamics of avoidant personalities.

### Hughes, Furnham, & Batey (2020) — personality × regulation
*Emotion*, 20(1), 63-74.

Big Five personality traits relate to all three stages of Gross's (2015) extended process model: identification, selection, and implementation. Personality doesn't just affect which strategy is chosen but how effectively it's executed. This grounds the design decision to pass personality axes to the LLM — strategy selection and strategy implementation are both personality-dependent.

### Petter et al. (2025) — personality-strategy correlations
*Emotion*, 25(5), 1273-1292.

The predictor's foundation. Strategy selection depends on emotion intensity, personality, and controllability. High intensity → distraction. Low-moderate intensity → reappraisal viable. Personality shapes preference. The predictor mechanises the clear cases; Phase 1 gives the LLM the same personality information for the unclear cases.

## Predictor Judge Eval Results

### Baseline (#818, 2026-11-05)
| Case | Strategy | Score | Judge notes |
|------|----------|-------|-------------|
| suppressor-anger | RM | **4** | "CC also viable at moderate intensity" |
| reappraiser-anger | CC | **5** | Optimal match |
| reappraiser-reproach | CC | **4** | "strict rule-following resists reframing genuine violations" |
| anyone-fear | AD | **5** | Universal for uncontrollable |
| reappraiser-shame | CC | **5** | Analytical personality + shame = textbook reappraisal |
| suppressor-shame | FELL_THROUGH | -1 | Skipped |
| incoherent-anger | FELL_THROUGH | -1 | Skipped |
| mixed-anger | FELL_THROUGH | -1 | Skipped |

**Scored:** 5, **Skipped:** 3, **Mean:** 4.6

### Phase 1 (2026-11-06)
| Case | Strategy | Score | Change | Judge notes |
|------|----------|-------|--------|-------------|
| suppressor-anger | RM | **5** | +1 | Full personality-congruence acknowledged |
| reappraiser-anger | CC | **5** | = | Held |
| reappraiser-reproach | CC | **5** | +1 | No longer penalised for strict rule-following tension |
| anyone-fear | AD | **5** | = | Held |
| reappraiser-shame | CC | **5** | = | Held |
| suppressor-shame | FELL_THROUGH | -1 | = | Still skipped (predictor unchanged) |
| incoherent-anger | FELL_THROUGH | -1 | = | Still skipped |
| mixed-anger | FELL_THROUGH | -1 | = | Still skipped |

**Scored:** 5, **Skipped:** 3, **Mean:** 5.0

### Interpretation

The 4→5 improvement on suppressor-anger and reappraiser-reproach is **likely LLM variance** (judge non-determinism across runs), not caused by Phase 1 changes. The predictor logic is identical for these cases — Phase 1 only affects fall-through paths. The judge is a separate LLM call that may score differently on different runs even with identical inputs.

suppressor-shame still falls through because the predictor logic was intentionally left unchanged. Phase 1 improves what happens *after* the fall-through (the LLM gets personality context), but the predictor eval only measures the predictor's decision, not the LLM's response.

## Eval Gap: Predictor vs. End-to-End

The current eval (`RegulationPredictorBackTest`) judges the **predictor's** strategy selection. Phase 1's primary improvement is in the **LLM's** response quality when the predictor falls through. These are different things.

An **end-to-end regulation eval** is needed that:
1. Runs the full regulation pipeline (predictor → LLM) for each personality × emotion combination
2. Judges the LLM's chosen strategy (not the predictor's) against the personality profile
3. Includes fall-through cases where the LLM now receives personality context and self-conscious guidance
4. Measures whether temporal containment appears in suppressor+shame narratives

This eval should be built before Phase 2 — without it, we can't know whether Phase 1's guidance is sufficient or whether mechanical prediction (CDE continuous weights) is needed.

## Observations for Phase 2 Design

### 1. Binary classification works for clear cases
All 5 scored predictor cases are at 5 (across both runs). The architecture issue is the fall-through path, not the prediction path. Continuous weights should target turning some fall-throughs into confident predictions, not improving already-confident ones.

### 2. The shame problem is fundamentally different
Shame is the one personality × emotion combination where the personality's natural strategy and the emotion's needs are mismatched. Continuous weights won't solve this unless they can represent "partial CC capacity" — a suppressor personality with enough analytical traits to execute a limited form of reappraisal (temporal containment, not full cognitive restructuring).

### 3. The LLM needs personality context regardless
Even with continuous weights, some combinations will remain unpredictable. The disposition axes injection should stay as the permanent fallback path. Phase 2 should extend the mechanical prediction, not replace the LLM fallback.

### 4. End-to-end eval is prerequisite
Without measuring the LLM's response quality on fall-through cases, Phase 2's architecture decision (replace boolean vs. augment with continuous weights) would be based on guesswork, not evidence. Build the end-to-end eval first, measure Phase 1's actual impact, then design Phase 2.

### 5. Shame has dimensions beyond personality disposition (NOT implemented — Phase 2 design input)

**None of the following is implemented in Phase 1. These are design observations that emerged during the session and should inform Phase 2's approach to shame regulation.**

Three dimensions make shame qualitatively different from other emotions, and the current predictor accounts for none of them:

**Memory-triggered compounding:** Unlike anger or fear (responses to present events), shame compounds through memory retrieval. Previous shame episodes are surfaced during appraisal and amplify the current intensity. Repeated promise-violation creates accumulating shame where each cycle undermines the character's self-narrative — not just the act but the pattern the acts reveal. The existing salience-scored memory system already handles retrieval; the question is whether the regulation system should receive memory context (count/recency of similar episodes) alongside disposition axes.

**Relationship-modulated intensity:** Shame intensity scales with relational closeness and obligation structure. Failing a casual acquaintance produces mild discomfort. Failing someone you are responsible for produces shame that attacks self-concept. The quality changes — "I forgot" vs. "I failed them as a parent." The social cognition model already tracks trust rates and conflict interpretation per relationship; the question is whether the regulation pipeline should receive relational context for self-conscious emotions.

**Personality-gated depth:** High grandiosity + high callousness (Cluster-B traits) can produce surface-level shame that doesn't persist — grandiosity reframes failure externally, callousness doesn't register the other's hurt as relevant to self-evaluation. The PersonalityFacets system already models these dimensions, and the CDE expression style derivations already handle the grandiosity × callousness interaction for gloating/gallantry. The question is whether the predictor needs PersonalityFacets (not just DispositionAxes) to assess how deeply shame penetrates, and whether the LLM guidance should differentiate between identity-threatening shame and surface-level social discomfort.

**Existing infrastructure that connects:** salience-scored memory (compounding), social cognition model (relational closeness), CDE expression styles (grandiosity × callousness). The building blocks exist in the platform; the regulation system doesn't yet consume them for shame.

### 6. Literature review needed for CDE weight tables
If Phase 2 adds a `deriveRegulationDefaults()` pathway to CognitiveDerivationEngine, the weight tables (which disposition axis values map to which suppression/reappraisal/redirect capacities) must be grounded in published personality-regulation correlations. Hughes et al. (2020) and Petter et al. (2025) provide the starting point, but the specific axis-to-weight mappings need validation against the empirical literature — not intuitive assignment.
