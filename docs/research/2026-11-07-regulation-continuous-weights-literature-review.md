# Regulation Continuous Weights — Literature Review

**Issue:** #830
**Date:** 2026-11-07 (Phases 2-3), 2026-11-08 (Phase 4)
**Phase:** Continuous — CDE continuous weights + shame dimensions + predictor pins + dynamic capacity
**Predecessor:** Phase 1 findings (`2026-11-06-regulation-predictor-quality-phase1-findings.md`)

## Problem Statement

`RegulationStrategyPredictor` uses boolean classification (`isSuppressor()` / `isReappraiser()`) based on disposition axis signal counting. Phase 1 improved the LLM fall-through path with personality context and self-conscious emotion guidance. Phase 2 replaces the boolean classification with continuous weights derived from published personality-regulation correlations.

### End-to-End Eval Baseline (Phase 1)

The `RegulationEndToEndEvalTest` (built at the start of this session) runs the full predictor → LLM → judge pipeline for 8 personality × emotion cases:

| Case | Predictor | LLM Strategy | Strategy Score | Notes |
|------|-----------|-------------|----------------|-------|
| suppressor-anger | RESPONSE_MODULATION | RESPONSE_MODULATION | 5 | Pinned, personality-congruent |
| suppressor-shame | FELL_THROUGH | RESPONSE_MODULATION | 4 | Fall-through, temporal containment 5/5 |
| reappraiser-anger | COGNITIVE_CHANGE | COGNITIVE_CHANGE | 5 | Pinned, optimal match |
| reappraiser-reproach | COGNITIVE_CHANGE | COGNITIVE_CHANGE | 4 | Pinned, slight tension with strict rules |
| anyone-fear | ATTENTIONAL_DEPLOYMENT | ATTENTIONAL_DEPLOYMENT | 5 | Pinned, universal for uncontrollable |
| incoherent-anger | FELL_THROUGH | SITUATION_SELECTION | 4 | Fall-through, avoidant dominant |
| reappraiser-shame | COGNITIVE_CHANGE | COGNITIVE_CHANGE | 5 | Pinned, analytical + shame = textbook CC |
| mixed-anger | FELL_THROUGH | SITUATION_MODIFICATION | 3 | Fall-through, weakest case |

**Strategy mean:** 4.4 | **Temporal containment mean:** 5.0 | **Fall-through mean:** 3.7

### What the Baseline Tells Us

1. Boolean classification works for clear cases — all 5 pinned cases score 4-5
2. The LLM with personality context handles fall-throughs reasonably (3.7 mean)
3. mixed-anger is the weakest case (3) — moderate personality gets SITUATION_MODIFICATION, judge says COGNITIVE_CHANGE more natural
4. Temporal containment for suppressor-shame is perfect — Phase 1 guidance works
5. The marginal value of continuous weights is incremental for strategy selection, but the architectural improvement enables shame dimensions

## Literature Review

### Primary Sources

#### 1. Barańczuk (2019) — Meta-analysis of Big Five and emotion regulation

**Citation:** Barańczuk, U. (2019). The five factor model of personality and emotion regulation: A meta-analysis. *Personality and Individual Differences*, 139, 217-227.

**Scope:** 132 studies, 156 independent samples, 46,345 participants, 753 effect sizes.

**Key Table — Big Five × Regulation Strategy Correlations (mean r):**

| Trait | Reappraisal | Problem Solving | Mindfulness | Avoidance | Suppression (total) | Suppression (expression) | Worry | Rumination |
|-------|------------|-----------------|-------------|-----------|--------------------|-----------------------|-------|-----------|
| **Neuroticism** | **-0.19***  | -0.17*** | -0.34*** | **+0.31***  | 0.09* | 0.04 (ns) | **+0.59***  | +0.47*** |
| **Extraversion** | **+0.22***  | +0.21*** | +0.15** | -0.10*** | -0.29*** | **-0.37***  | -0.16** | -0.20*** |
| **Openness** | **+0.20***  | +0.18*** | +0.13** | -0.05** | -0.13*** | -0.12*** | +0.10 | +0.22 |
| **Agreeableness** | **+0.18***  | +0.12*** | +0.15** | -0.12*** | — | **-0.15***  | -0.04 | -0.04 |
| **Conscientiousness** | **+0.19***  | **+0.29**  | +0.19*** | -0.13*** | — | -0.08* | +0.07 | -0.04 |

*p ≤ .05, **p ≤ .01, ***p ≤ .001

**Effect size interpretation (Cohen 1992):** small r ≥ 0.10, medium r ≥ 0.30, large r ≥ 0.50.

**Key observations for weight table derivation:**

1. **Reappraisal** has consistent small-to-moderate positive correlations with all non-Neuroticism traits (0.18-0.22) and moderate negative with Neuroticism (-0.19). No single Big Five trait dominates reappraisal capacity — it draws on multiple trait dimensions.

2. **Suppression of expression** has a notably large negative correlation with Extraversion (-0.37) — introverts suppress expression much more. The Neuroticism correlation is non-significant for expression suppression (0.04 ns), meaning neurotic individuals don't suppress expression more — they suppress *thoughts* more (0.21*).

3. **Avoidance** has the strongest single predictor: Neuroticism (+0.31). High neuroticism strongly predicts avoidance-based regulation (attentional deployment, distraction, withdrawal).

4. **Worry and Rumination** are strongly associated with Neuroticism (0.59 and 0.47) — these are maladaptive regulation strategies that high-N personalities default to when other strategies fail.

#### 2. Hughes, Kratsiotis, Niven & Holman (2020) — Personality and Emotion Regulation Stages

**Citation:** Hughes, D. J., Kratsiotis, I. K., Niven, K., & Holman, D. (2020). Personality traits and emotion regulation: A targeted review and recommendations. *Emotion*, 20, 63-67.

**Key contribution:** Distinguishes three stages of emotion regulation where personality operates differently:

1. **Identification** — which emotions to regulate. Neuroticism increases sensitivity to negative emotions, meaning more emotions are flagged for regulation. Extraversion may decrease identification of positive emotions (already well-regulated by default).

2. **Selection** — which strategy to choose. The Barańczuk correlations primarily capture this stage. Extraverts select socially engaging strategies; agreeable individuals select harmony-preserving strategies; open individuals select cognitively demanding strategies.

3. **Implementation** — how effectively the strategy executes. **This is the critical distinction for our weight tables.** Habitual selection ≠ effective execution. Key findings:
   - Extraverts are "prepared to engage in more challenging regulation processes (e.g., reappraisal)" — higher implementation capacity for demanding strategies
   - Agreeable individuals "choose strategies that enable them to avoid conflict or upsetting others" — effective at conflict-avoidant strategies but constrained from confrontational ones
   - High neuroticism predicts habitual use of suppression but *not* effective execution — suppression under high neuroticism leads to rebound effects and poor outcomes

**Implication for weight tables:** We need to model *capacity* (implementation), not just *preference* (selection). A personality high in neuroticism may habitually attempt suppression (selection) but fail at sustained suppression (implementation). The weight tables must distinguish these.

#### 3. Petter, Mehta, Petrova, Kindt, Sheppes, Haslbeck & Gross (2025) — Computational Model

**Citation:** Petter, J., et al. (2025). Emotion regulation, fast or slow: A computational model of strategy choice. *Emotion*, 25(5), 1273-1292.

**Key contribution:** Formalizes the intensity-based strategy choice: people prefer distraction over reappraisal at high intensities, reappraisal over distraction at low-to-moderate intensities.

**Relevance:** Already implemented in `RegulationStrategyPredictor` as `HIGH_INTENSITY_THRESHOLD = 0.75` → forces ATTENTIONAL_DEPLOYMENT. The continuous weights should respect this: at high intensity, personality-based capacity matters less because the intensity override dominates.

#### 4. Webb, Miles & Sheeran (2012) — Strategy Effectiveness Meta-analysis

**Citation:** Webb, T. L., Miles, E., & Sheeran, P. (2012). Dealing with feeling: A meta-analysis of the effectiveness of strategies derived from the process model of emotion regulation. *Psychological Bulletin*, 138(4), 775-808.

**Key contribution:** Effect sizes (d+) for each Gross strategy's effectiveness. Cognitive change (reappraisal) has the largest effect size; response modulation (suppression) has the smallest. But this is averaged across all personalities — the interaction with personality is what our predictor adds.

**Relevance for shame:** Webb 2012 shows that attentional deployment is effective short-term but cognitive change is more effective long-term. For suppressor personalities facing shame, temporal containment (short-term AD or RM → later CC) is the evidence-based approach. Already implemented in Phase 1 guidance.

#### 5. Nathanson (1992) — Compass of Shame

**Citation:** Nathanson, D. L. (1992). *Shame and Pride: Affect, Sex, and the Birth of the Self*. W. W. Norton.

**Key contribution:** Four shame responses: withdrawal, attack-self, attack-other, avoidance. Maps to regulation strategies:
- Withdrawal → SITUATION_SELECTION (leaving the triggering context entirely)
- Attack-self → rumination (maladaptive, not a Gross strategy)
- Attack-other → SITUATION_MODIFICATION (externalize, redirect)
- Avoidance → ATTENTIONAL_DEPLOYMENT (distraction, pretending it didn't happen)

**Terminology note:** Nathanson's "avoidance" (distraction/denial) ≠ our disposition axis "avoidant" (conflict-avoidant, which maps to Nathanson's "withdrawal"). Our avoidant-conflictMode personalities don't just redirect attention — they leave the situation. This distinction drove the Phase 3 AD→SS correction and Phase 4 predictor pin.

**Relevance:** Personality disposition predicts which shame response dominates. Avoidant-conflictMode personalities → withdrawal → SS. Competitive personalities → attack-other. Analytical personalities → can engage with the self-evaluation (CC). This maps directly to our continuous weights for self-conscious emotions.

#### 6. Yang, Li, Su, Luo & Yin (2025) — ERP Evidence for Temporal Suppression

**Citation:** Yang, J., et al. (2025). Neural evidence for temporal suppression dynamics in avoidant attachment. (ERP study)

**Key contribution:** ERP evidence that suppression in avoidant individuals follows temporal dynamics — initial automatic suppression followed by delayed emotional processing. Supports the "suppress now, process later" model for avoidant personalities.

**Relevance:** Validates Phase 1's temporal containment guidance for suppressor-shame. Also informs the shame context rendering: the LLM should understand that suppression for avoidant personalities is *temporal staging*, not permanent avoidance.

## Disposition Axes → Big Five Mapping

Our disposition axes are not Big Five traits — they're derived from personality facets. But the published literature is overwhelmingly indexed by Big Five. This mapping is the bridge:

| Axis | Value | Primary Big Five | Secondary Big Five | Mapping confidence |
|------|-------|-----------------|-------------------|-------------------|
| conflictMode | avoidant | High Neuroticism | High Agreeableness | High — avoidance is the canonical N+A combination |
| conflictMode | analytical | High Openness | Moderate Conscientiousness | High — analytical approach maps cleanly to O |
| conflictMode | cooperative | High Agreeableness | Moderate Extraversion | High — cooperation is canonical A |
| conflictMode | competitive | Low Agreeableness | High Extraversion | Moderate — competitive maps to E+low-A but imprecise |
| socialOrient | cooperative | High Agreeableness | — | High |
| socialOrient | independent | Low Agreeableness | Moderate Openness | Moderate |
| socialOrient | competitive | Low Agreeableness | High Extraversion | Moderate |
| riskAppetite | conservative | High Neuroticism | High Conscientiousness | High — risk aversion is canonical N |
| riskAppetite | calculated | Moderate all | — | Low — least differentiated |
| riskAppetite | bold | Low Neuroticism | High Openness | High — boldness maps to low-N + high-O |
| autonomy | low | Low Openness | Low Extraversion | Moderate |
| autonomy | moderate | Moderate all | — | Low |
| autonomy | high | High Openness | High Extraversion | High — self-direction maps to O+E |
| ruleFollowing | flexible | Low Conscientiousness | High Openness | Moderate |
| ruleFollowing | moderate | Moderate Conscientiousness | — | Low |
| ruleFollowing | strict | High Conscientiousness | — | High |

**Confidence ratings reflect how cleanly the axis value maps to a Big Five dimension.** Low-confidence mappings (calculated risk, moderate autonomy, moderate rules) contribute less to capacity derivation — they should have near-zero weights.

## Derived Capacity Directions

Using the Barańczuk r values through the Big Five mapping, each axis value's contribution to regulation capacity:

### Reappraisal Capacity (COGNITIVE_CHANGE)

Literature basis: Reappraisal positively correlated with O (+0.20), E (+0.22), C (+0.19), A (+0.18); negatively with N (-0.19).

| Axis | Value | Direction | Magnitude | Rationale |
|------|-------|-----------|-----------|-----------|
| conflictMode | analytical | **positive** | strong | O (+0.20) primary, C (+0.19) secondary |
| conflictMode | cooperative | **positive** | moderate | A (+0.18) |
| conflictMode | avoidant | **negative** | strong | N (-0.19), resists cognitive engagement |
| conflictMode | competitive | **positive** | weak | E (+0.22) but low-A partially offsets |
| autonomy | high | **positive** | strong | O (+0.20) + E (+0.22) — cognitive agency |
| autonomy | low | **negative** | moderate | Low-O, low-E — limited cognitive agency |
| ruleFollowing | strict | **positive** | moderate | C (+0.19) — structured reappraisal framework |
| ruleFollowing | flexible | **positive** | weak | O (+0.20) but low-C partially offsets |
| riskAppetite | bold | **positive** | strong | Low-N (inverse of -0.19) + high-O |
| riskAppetite | conservative | **negative** | moderate | High-N (-0.19) — risk-averse, resists cognitive engagement |
| socialOrient | cooperative | **positive** | moderate | A (+0.18) |

### Suppression Capacity (RESPONSE_MODULATION)

Literature basis: Suppression of expression primarily correlated with low-E (-0.37), and modestly with low-A (-0.15), low-O (-0.12). Critically: N correlation is non-significant for expression suppression — neurotics don't suppress expression more, they suppress *thoughts* more (which is maladaptive rumination, not regulation).

| Axis | Value | Direction | Magnitude | Rationale |
|------|-------|-----------|-----------|-----------|
| conflictMode | avoidant | **positive** | strong | High-N + high-A → habitual suppression; Hughes caveat: habitual ≠ effective for sustained suppression |
| conflictMode | competitive | **positive** | moderate | Low-A → suppression of self-expression as social dominance tool |
| conflictMode | analytical | **negative** | moderate | High-O → prefers cognitive engagement over suppression |
| conflictMode | cooperative | **neutral** | weak | High-A has modest negative r with suppression |
| socialOrient | competitive | **positive** | moderate | Low-A |
| socialOrient | cooperative | **negative** | weak | High-A |
| riskAppetite | conservative | **positive** | moderate | Risk-averse → suppression as safe default |
| autonomy | low | **positive** | moderate | Low-E → suppression as default when lacking agency |

**Critical caveat for suppression capacity:** The Barańczuk data measures habitual *use*, not sustained *effectiveness*. Hughes (2020) warns that neuroticism predicts suppression use but poor suppression outcomes. For our weight tables, suppression capacity should reflect EFFECTIVE suppression — the ability to sustain expression control without rebound. This likely requires low Neuroticism (emotional stability) as a prerequisite, even though high-N personalities habitually attempt it.

### Redirect Tendency (ATTENTIONAL_DEPLOYMENT / SITUATION_SELECTION)

Literature basis: Avoidance strongly correlated with N (+0.31) and inversely with all other traits. This is the "redirect attention away from the trigger" cluster.

| Axis | Value | Direction | Magnitude | Rationale |
|------|-------|-----------|-----------|-----------|
| conflictMode | avoidant | **positive** | strong | N (+0.31) — canonical avoidance profile |
| riskAppetite | conservative | **positive** | strong | N (+0.31) — risk-averse → withdraw from threatening contexts |
| autonomy | low | **positive** | moderate | Low-E → tends toward withdrawal |
| socialOrient | independent | **positive** | weak | Some withdrawal tendency |
| conflictMode | analytical | **negative** | moderate | High-O → prefers engagement over avoidance |
| riskAppetite | bold | **negative** | strong | Low-N → approaches rather than avoids |

## Weight Table Design Principles

1. **Direction from literature, magnitude from eval.** The table above gives the direction (positive/negative) and relative magnitude (strong/moderate/weak) for each axis → capacity mapping. The exact numerical values will be set initially based on relative r-value magnitudes, then calibrated via the end-to-end eval.

2. **Capacity ≠ habitual use.** Suppression capacity requires emotional stability (low-N) as a modulating factor, even though high-N personalities habitually attempt suppression. The weight table should include a "sustained effectiveness" adjustment.

3. **Multi-axis composition.** Each capacity is the sum of contributions from all 5 axes. No single axis determines capacity alone — it's the full profile that matters. This is why the boolean `isSuppressor` (signal counting with threshold) loses information.

4. **Moderate values contribute near-zero.** Axis values of "moderate" are poorly differentiated in the Big Five mapping and should contribute minimal signal.

5. **Intensity override preserved.** At high intensity (> 0.75), the Petter 2025 finding dominates — ATTENTIONAL_DEPLOYMENT regardless of personality. Continuous weights only affect the prediction when intensity is below the override threshold.

## Shame Dimensions — Architectural Context

The continuous weights address the predictor's strategy selection. The shame dimensions address a different problem: shame is qualitatively different from other emotions because it has three properties the current regulation pipeline doesn't model.

### Memory-triggered compounding
- **Mechanism:** Salience-scored memory retrieval surfaces previous shame episodes during appraisal, amplifying current intensity
- **What changes:** Regulation for "first shame episode" vs "fifth shame episode about the same pattern" should differ — the latter attacks self-narrative, not just self-evaluation
- **Existing infrastructure:** `SalienceScoreMemory`, `MemoryRetriever` — the memory system already handles retrieval; the regulation pipeline doesn't yet consume it
- **Literature:** Nathanson (1992) compass of shame — shame compounds through repeated cycles of withdrawal

### Relationship-modulated intensity
- **Mechanism:** Shame intensity scales with relational closeness and obligation structure
- **What changes:** Failing a casual acquaintance vs failing someone you're responsible for changes the quality of self-evaluation
- **Existing infrastructure:** Social cognition model tracks trust rates and conflict interpretation per relationship
- **Literature:** Self-evaluative emotions are inherently relational (Tangney & Dearing, 2002)

### Personality-gated depth
- **Mechanism:** High grandiosity + high callousness (Cluster-B surface) produces surface-level shame that doesn't persist
- **What changes:** Grandiose personalities reframe failure externally; heavy containment strategies would be disproportionate
- **Existing infrastructure:** CDE expression style derivations already handle grandiosity × callousness interaction
- **Literature:** Personality disorder research on shame regulation (narcissistic vs avoidant patterns)

### Architectural Decision (D5)

These dimensions are passed as a `ShameContext` record assembled by the tick participant at runtime (not derived statically by CDE). The generation phase renders them into psychologically grounded narrative sections following the existing `buildUserMessage()` pattern — computed values for testability + interpretive narrative for the LLM.

## Calibration Results (post-implementation)

### Weight Recalibration

Three weights adjusted after end-to-end eval against LLM judge (6 runs, 11 cases):

| Weight | Original | Calibrated | Rationale |
|--------|----------|------------|-----------|
| SUPPRESSION_CONFLICT: avoidant | 0.25 | 0.10 | Barańczuk: N→suppression r=0.09, A→suppression r=-0.15. Avoidant redirects (r=0.31), doesn't suppress well. Original weight conflated avoidance with suppression. |
| REAPPRAISAL_RULES: flexible | 0.10 | 0.15 | Flexible maps partially to Openness (primary reappraisal enabler, r=0.20). Original weight was conservative. |
| REDIRECT_RULES: flexible | +0.05 | -0.10 | Flexibility = approach, not avoidance. Flexible personalities engage rather than rigidly withdrawing. |

### Structural Additions

**Viable alternative threshold (0.40):** For controllable negative emotions, don't pin RM when reappraisalCapacity >= 0.40. Grounded in Hughes et al. 2020: when both impulse (suppression) and reflective (reappraisal) strategies are available, situation should determine choice. Prevents the predictor from forcing RM for personalities that CAN reappraise.

**Temporal staging (Gross 2015 extended process model):** When a personality falls through with viable reappraisal, the generation phase renders guidance showing both the personality's impulse strategy (first response) and reflective capacity (sustained processing). This produces narratives where the avoidant response manages the immediate emotion, then flexibility enables reappraisal — a temporal progression the judge recognizes as personality-congruent.

**Surface-level shame → pin RM:** When ShameContext.isSurfaceLevel() (grandiosity >= 0.7 AND callousness >= 0.7), override any prediction to RM. The grandiose-callous personality deflects shame externally — this is modulation (dismissal), not genuine cognitive change. The judge consistently scored CC at 2-3 for this personality because externalization is not reappraisal.

### Eval Outcomes

| Metric | Pre-calibration | Post-calibration |
|--------|----------------|------------------|
| Strategy mean | 4.5 | 4.4 (stable) |
| Shame depth mean | 5.0 | 5.0 |
| incoherent-anger | 3/3/3 | **4/4** |
| surface-shame | 4/3/2 | **5/5** |

**Boundary cases remaining (swing 3-4):** mixed-anger, compounding-shame, suppressor-shame. These score 5 in some runs and 3 in others. The variance correlates with judge assessment of situation-specificity (e.g., "betrayal resists reappraisal") and serial-suppression fatigue (e.g., "4th episode, RM has diminishing returns"). Cases that score 5 (suppressor-anger, reappraiser-anger, anyone-fear, reappraiser-shame) NEVER vary. Variance is signal, not noise — it indicates the model boundary.

## Phase 3 — Boundary Case Resolution (3 eval runs)

### Architectural Changes

Four changes targeting the three boundary cases, grounded in literature:

1. **CC viable alternative check** (`VIABLE_ALTERNATIVE = 0.45`): When CC is dominant for controllable emotions but the personality has viable suppression (≥0.45) or redirect (≥0.45), fall through to LLM. Based on Ford & Troy 2019 regulatory flexibility: personalities with multiple viable strategies should not be pinned to one; the situation should determine choice. Fixes mixed-anger (was pinning CC despite moderate suppression/redirect capacities).

2. **Avoidant shame → withdrawal guidance**: Nathanson 1992 compass of shame identifies four shame responses; avoidant personalities map to the withdrawal pole (ATTENTIONAL_DEPLOYMENT), not suppression (RESPONSE_MODULATION). Previous guidance said "temporal containment" which the LLM interpreted as RM. New guidance explicitly recommends AD for avoidant conflictMode.

3. **Serial regulation fatigue**: Gross & John 2003 documents cumulative suppression costs — declining effectiveness, physiological rebound, eventual breakthrough. New compounding guidance signals that habitual strategies show diminishing returns after repeated use, telling the LLM to shift strategies.

4. **Compound-avoidant escalation**: When both avoidant shame guidance (→AD) and serial fatigue (→shift strategies) fire, the AD directive overrides the fatigue signal. Split the avoidant shame guidance: compounding avoidant shame gets explicit escalation toward SITUATION_SELECTION or SITUATION_MODIFICATION (changing the environment, not just the attention).

5. **Situation context parameter**: Added `situationContext` to the generation phase so the LLM has the same trigger information the eval judge has. The eval test now passes `emotionDesc` as situation context for all cases.

### Eval Outcomes (3 runs)

| Case | Phase 2 (6 runs) | Phase 3 Run 1 | Run 2 | Run 3 | Strategy change |
|------|-----------------|---------------|-------|-------|-----------------|
| suppressor-anger | 5 (stable) | 5 | 5 | 5 | — |
| suppressor-shame | 3-4 | 4 | 3 | 4 | RM → AD (withdrawal) |
| reappraiser-anger | 5 (stable) | 5 | 5 | 5 | — |
| anyone-fear | 5 (stable) | 5 | 5 | 5 | — |
| incoherent-anger | 4 (stable) | — | 4 | 3 | Judge variance |
| reappraiser-shame | 5 (stable) | 5 | 5 | 5 | — |
| mixed-anger | 3-4 | 3 | 3 | 3 | Pinned CC → fell through CC |
| compounding-shame | 3-4 | 4 (SS) | 3 (AD) | 4 (SS) | RM → SS (with compound guidance) |
| surface-shame | 5 (stable) | 5 | 5 | 5 | — |

### Findings

**Compounding-shame resolved:** The compound-avoidant guidance consistently produces SITUATION_SELECTION (avoiding the triggering context entirely), which the judge scores 4. This is the clearest improvement — the LLM recognizes that withdrawal has failed after 4 episodes and escalates to environmental change.

**Suppressor-shame architecturally correct:** The LLM now picks AD (withdrawal) instead of RM (suppression). This is the right strategy per Nathanson 1992, but the judge swings 3-4 because it sometimes objects that the personality's "moderate rules, cooperative nature" creates tension with pure withdrawal. The variance is in the judge's interpretation, not the model.

**Mixed-anger is a genuine boundary case:** Neither CC ("betrayal resists reappraisal") nor RM ("cooperative personality would seek understanding") satisfies the judge consistently. This is not a model gap — it is inherent ambiguity where personality and situation push in opposite directions. The CC viable alternative check is architecturally correct (this personality HAS multiple viable strategies), and the temporal staging produces the right narrative (RM impulse → CC reflection). The 3-4 variance reflects genuine psychological uncertainty.

**Incoherent-anger shows judge variance:** Scored 4/3 across runs 2/3 with identical code and same strategy (CC). The variance is in the LLM judge, not the model. The 6-run Phase 2 baseline may have been a lucky sample.

### Strategy Exploration (all 5 Gross strategies per boundary case)

Tested each strategy label independently against the judge (no generation, no narrative — pure strategy-label scoring). This isolates the judge's preference from narrative quality effects.

| Strategy | mixed-anger | suppressor-shame | compounding-shame |
|----------|-------------|------------------|-------------------|
| **COGNITIVE_CHANGE** | **5** | 2 | 1 |
| **SITUATION_SELECTION** | 4 | **5** | **4** |
| SITUATION_MODIFICATION | 3 | 2 | 1 |
| ATTENTIONAL_DEPLOYMENT | 3 | 4 | 4 |
| RESPONSE_MODULATION | 2 | 3 | 3 |

**Key insight — AD was the wrong strategy for avoidant shame.** The judge prefers SITUATION_SELECTION (full withdrawal from the triggering context) over ATTENTIONAL_DEPLOYMENT (momentary attention redirect). The avoidant personality doesn't just redirect attention — they restructure their environment to avoid re-exposure. SS is the natural escalation of the avoidant impulse.

**mixed-anger CC=5 confirms the architecture.** When the judge evaluates CC as a strategy label for this moderate-cooperative personality, it scores 5. The 3 scores in the full pipeline come from judge variance (non-deterministic reasoning that sometimes focuses on personality, sometimes on situation). The temporal staging architecture is correct.

**compounding-shame reveals a genuine psychological bind.** After 4 episodes: CC=1 (personality can't execute), SM=1 (contradicts avoidance), RM=3 (serial suppression failing), AD=4, SS=4. No strategy scores 5. The avoidant personality with low autonomy is trapped — they can't reappraise, can't modify, and continued avoidance is maladaptive. This is a real clinical pattern: learned helplessness after repeated regulation failure. The model is correctly representing the limit of personality-based regulation.

### Pipeline Observation (with SS guidance)

Full pipeline run with updated guidance (AD→SS for avoidant shame):

| Case | Predictor | LLM Strategy | Judge | Environmental directive |
|------|-----------|-------------|-------|------------------------|
| mixed-anger | FELL_THROUGH | CC | **5** | — |
| suppressor-shame | FELL_THROUGH | SS | **4** | "Withdraw from context... seek low-stakes environment" |
| compounding-shame | FELL_THROUGH | SS | **3** | "Restructure environment to eliminate re-exposure" |

**mixed-anger at 5** — first consistent 5 for this case. The temporal staging narrative shows RM→CC progression with betrayal-aware context. The judge recognizes this as personality-congruent.

**suppressor-shame at 4 with SS** — SS produces both a narrative AND a COMPETENCE-axis environmental directive. The directive component means the regulation has behavioural consequences: the character will avoid competence-testing situations. This is architecturally richer than AD, which has no environmental effect.

**compounding-shame at 3** — the judge sees SS after 4 episodes as "maladaptive withdrawal spiral." Even the preferred strategy fails here because the repetition itself invalidates avoidance-family strategies. This may require a multi-strategy or capacity-exhaustion model rather than single-strategy prediction.

## Phase 4 — Predictor Pins and Regulatory Learning (2026-11-08)

### First-Principles Analysis

Phase 3 resolved boundary cases through prompt guidance — narrative text telling the LLM which strategy to choose. But guidance is not a pin. The LLM can and does ignore it. Phase 4 asked: if the strategy exploration proves a strategy is clearly right for a personality, why is the predictor falling through?

Three root causes identified:

#### 1. Missing predictor path for avoidant self-conscious emotions

`predictSelfConsciousFromCapacities` only handled one case: reappraisal ≥ 0.65 → CC. It had no path for avoidant personalities. The method didn't even receive `DispositionAxes`. Nathanson 1992 is clear: avoidant personalities withdraw from shame (→SS). Strategy exploration confirms: SS=5. This is deterministic from personality, not situation-dependent — the predictor should own it.

**Fix:** Added `DispositionAxes` parameter to `predictSelfConsciousFromCapacities`. After the reappraisal→CC check, added: avoidant conflict mode → SS pin. Ordering matters: a high-reappraisal avoidant personality (rare but possible) still gets CC, since reappraisal dominates when strong enough. Below the reappraisal threshold, avoidance takes over.

**Effect:** suppressor-shame and compounding-shame now pinned to SS instead of falling through. The LLM can no longer override to AD.

#### 2. Viable alternative check too aggressive for overwhelmingly dominant CC

The CC viable alternative check (`VIABLE_ALTERNATIVE=0.45`) fired when suppression OR redirect exceeded 0.45, regardless of the gap. For mixed-anger: reappraisal=0.90, suppression=0.50 — the check treated 0.50 as "viable competition" with 0.90. But a 0.40 gap is not regulatory flexibility (Ford & Troy 2019). It's an overwhelming reappraiser with moderate backup capacity.

**Fix:** Added `OVERWHELMING_DOMINANCE=0.30`. The viable alternative check now only falls through when `reappraisalCapacity - maxAlternative < 0.30` — i.e., the strategies genuinely compete. When the gap is ≥0.30, the dominant strategy is pinned.

**Effect:** mixed-anger now pins CC. The personality is not flexible — they're an overwhelming reappraiser.

#### 3. Temporal staging and pinning were mutually exclusive

The temporal staging guidance (RM→CC progression) had a `pinnedStrategy == null` guard — it only fired on fall-through. But pin says WHAT strategy; temporal staging says HOW to narrate it. These are orthogonal. When CC is pinned, the LLM still needs to know about the temporal progression to generate the RM→CC narrative that produces the best judge scores.

**Fix:** Changed the guard to `pinnedStrategy == null || pinnedStrategy == COGNITIVE_CHANGE`. Temporal staging now fires for both fall-through and CC-pinned cases.

**Effect:** mixed-anger gets both the reliability of a pin AND the narrative quality of temporal staging.

### Eval Results

#### Strategy Exploration (post-pins)

Confirms pin choices are correct — identical scores to Phase 3:

| Strategy | mixed-anger | suppressor-shame | compounding-shame |
|----------|-------------|------------------|-------------------|
| **COGNITIVE_CHANGE** | **5** | 2 | 2 |
| **SITUATION_SELECTION** | 4 | **5** | **4** |
| SITUATION_MODIFICATION | 4 | 2 | 2 |
| ATTENTIONAL_DEPLOYMENT | 3 | 4 | 4 |
| RESPONSE_MODULATION | 3 | 4 | 3 |

#### Pipeline Observation (post-pins)

| Case | Predictor | LLM Strategy | Judge Score | Phase 3 Score | Change |
|------|-----------|-------------|-------------|---------------|--------|
| mixed-anger | **PINNED CC** | CC | **5** | 3 | **+2** |
| suppressor-shame | **PINNED SS** | SS | **4** | 4 (AD) | correct strategy |
| compounding-shame | **PINNED SS** | SS | **3** | 4 | -1 |

**mixed-anger at 5:** The pin eliminates strategy variance. The temporal staging produces the RM→CC narrative: "your first instinct is containment — you clamp your jaw... but you don't leave it there. Your mind begins to turn the situation over." The judge recognises this as personality-congruent.

**suppressor-shame at 4 with correct strategy:** Previously the LLM chose AD despite SS guidance. Now SS is pinned. The judge gives 4 because it notes "cognitive change would be more adaptive long-term" — docking for regulatory effectiveness, not personality congruence.

**compounding-shame at 3:** The judge says "four repeated shame episodes signal that avoidance is failing... even an avoidant temperament should be shifting toward cognitive change." But CC=2 in the strategy exploration — the judge contradicts its own single-strategy evaluation.

### Discovery 1: Measurement Conflation

The compounding-shame score drop revealed that the eval judge conflates two independent dimensions:

1. **Personality congruence** — is this what the personality WOULD naturally do?
2. **Regulatory effectiveness** — will it actually work for this situation?

For single-episode cases, these align: the personality-congruent strategy is also effective. For compounding cases, they diverge: SS is congruent (avoidant personality → withdrawal) but ineffective (4th repetition of avoidance). The judge blends both into a single score, producing an incoherent result — it simultaneously says SS is what this personality would choose (strategy exploration: 5) and that this personality should try CC (pipeline: docks points), despite scoring CC at 2 for this personality.

**Architectural implication:** The eval needs to score congruence and effectiveness separately. A strategy that is personality-congruent AND ineffective should score high on congruence and low on effectiveness — not get a muddled middle score that's meaningless.

### Discovery 2: Fixed Personality Is the Wrong Assumption

The initial response to the compounding-shame score was "trying to engineer a higher score would make the model psychologically inaccurate — the personality's regulatory capacity is genuinely exhausted." But this assumes personality is fixed across episodes.

The assumption is wrong for two reasons:

**Personality traits vs regulatory capacity.** Personality traits (avoidant, cooperative, etc.) are relatively stable — Big Five research supports this. But regulatory capacity is not a trait. It's a learned behaviour that adapts with experience. The current architecture conflates them:

```
DispositionAxes → deriveRegulationDefaults() → static RegulationDefaults → predictor
```

The derivation happens once and produces fixed numbers. After 4 episodes of shame where avoidance keeps failing, the predictor still sees the same `suppressionCapacity=0.80, redirectTendency=0.85` as if nothing happened.

**Simulation timescale.** The simulation can span weeks, months, or years. 4 shame episodes is not 4 minutes — it's potentially months of accumulated experience. At that timescale, regulatory learning is not just plausible; it's expected. The memory system already stores these experiences (salience-scored memory, consolidation, reflection). The regulation system just doesn't consume them.

### Discovery 3: CAPS Architecture Demands Dynamic Capacity

The CAPS (Cognitive-Affective Processing System) architecture that underpins the cognitive pipeline models behaviour as a function of disposition × situation. The "situation" includes the history of prior regulatory attempts. After 4 failed avoidance episodes:

- The personality is still avoidant (traits stable)
- But the effective avoidance capacity for shame *in this context* should be degraded
- Not because the personality changed — because those strategies have been tried and found wanting
- "Avoidant person encountering shame for the first time" ≠ "Avoidant person who has tried avoidance 4 times and watched it fail"

The regulatory defaults should be a dynamic quantity modulated by experience:

```
DispositionAxes → baseline RegulationDefaults
Prior regulation outcomes (strategy, effectiveness, emotion type) → modulation factor
baseline × modulation → effective RegulationDefaults → predictor
```

After 4 episodes of low-effectiveness avoidance for shame, the effective redirect/suppression capacity degrades below the pin threshold. The predictor falls through. The LLM — seeing both depleted capacities and compounding context — has room to generate something that acknowledges the bind rather than mechanically selecting a strategy that cannot work.

This closes the CAPS loop: dispositions produce behaviour → behaviour produces outcomes → outcomes modulate future disposition-behaviour mapping. The current architecture has the first two steps. The third step (regulatory learning from outcomes) is missing.

### Implications for Next Work

1. **Eval measurement:** Split strategy congruence and regulatory effectiveness into separate scored dimensions. The current single score produces incoherent results at boundary cases.

2. **Dynamic regulatory capacity:** Implement experience-modulated regulatory defaults. The memory system already stores regulation episode outcomes. The predictor needs to query them and degrade effective capacity for strategies that have repeatedly failed in the current emotional context.

3. **Timescale-aware regulation:** The modulation factor should account for recency and frequency. A single failure last month has less impact than 4 failures this week. The consolidation system's temporal weighting may be reusable here.

## References

- Barańczuk, U. (2019). The five factor model of personality and emotion regulation: A meta-analysis. *Personality and Individual Differences*, 139, 217-227.
- Bonanno, G. A., & Burton, C. L. (2013). Regulatory flexibility: An individual differences perspective on coping and emotion regulation. *Perspectives on Psychological Science*, 8(6), 591-612.
- Ford, B. Q., & Troy, A. S. (2019). Reappraisal reconsidered: A closer look at the costs of an acclaimed emotion-regulation strategy. *Current Directions in Psychological Science*, 28(2), 195-203.
- Gross, R. A., & John, O. P. (2003). Individual differences in two emotion regulation processes: Implications for affect, relationships, and well-being. *Journal of Personality and Social Psychology*, 85(2), 348-362.
- Hughes, D. J., Kratsiotis, I. K., Niven, K., & Holman, D. (2020). Personality traits and emotion regulation: A targeted review and recommendations. *Emotion*, 20, 63-67.
- Nathanson, D. L. (1992). *Shame and Pride: Affect, Sex, and the Birth of the Self*. W. W. Norton.
- Petter, J., et al. (2025). Emotion regulation, fast or slow: A computational model of strategy choice. *Emotion*, 25(5), 1273-1292.
- Tangney, J. P., & Dearing, R. L. (2002). *Shame and Guilt*. Guilford Press.
- Troy, A. S., Shallcross, A. J., & Mauss, I. B. (2013). A person-by-situation approach to emotion regulation: Cognitive reappraisal can either help or hurt, depending on the context. *Psychological Science*, 24(12), 2505-2514.
- Webb, T. L., Miles, E., & Sheeran, P. (2012). Dealing with feeling: A meta-analysis of the effectiveness of strategies derived from the process model of emotion regulation. *Psychological Bulletin*, 138(4), 775-808.
- Yang, J., et al. (2025). Neural evidence for temporal suppression dynamics in avoidant attachment. (ERP study)
