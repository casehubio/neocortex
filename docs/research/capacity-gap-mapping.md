# Emotion-Personality Capacity Gap Mapping

Ref: casehubio/neocortex#832

## Principle

When an emotion or cognitive process demands an operation (reappraisal, perspective-taking, self-reflection, anticipatory planning) and the personality lacks the traits to execute that operation, no mechanical prediction will produce a good outcome. These mismatches are properties of the psychological model, not design flaws. They should be identified explicitly and routed to contextual reasoning (LLM).

## Known Capacity Gaps

### Emotion Regulation

| Gap combination | Operation needed | Personality constraint | Current handling | Source |
|---|---|---|---|---|
| Suppressor + shame | Cognitive reappraisal (CC) | Low reappraisal capacity, non-avoidant conflict mode | `RegulationStrategyPredictor` returns `Optional.empty()` → LLM with shame-specific prompt guidance | #818, #830 |
| Avoidant + shame | Cognitive reappraisal (CC) | Low reappraisal, but has redirect capacity → may use situation selection | Falls through unless redirect capacity exceeds threshold → SITUATION_SELECTION | #818 |
| Surface-level shame (grandiosity ≥ 0.7 + callousness ≥ 0.7) | Self-evaluation engagement | Personality cannot genuinely engage with shame → surface-level processing | Hard gate → RESPONSE_MODULATION pinned by SHAME_CONTEXT | #830 |

**Detection mechanism:** `RegulationStrategyPredictor.predictSelfConsciousFromCapacities()` returns `Optional.empty()` when no capacity-personality combination can produce a viable strategy. Implicit — no explicit "capacity gap" signal.

**Reference pattern:** Intelligence capacity layer (search budget, #840) uses explicit per-parameter gap values (`depthGap`, `breadthGap`, `lateralityGap`) and a `capacityConstrained` boolean. This is the systematic model.

### Proactive Regulation

| Gap combination | Operation needed | Personality constraint | Current handling | Source |
|---|---|---|---|---|
| Bold/impulsive + anticipatory avoidance | Predictive reasoning about future emotional distress | Low redirect tendency → high avoidance threshold | Threshold math in `ProactiveRegulationParticipant` naturally reduces avoidance goals — fewer proposals generated, not an explicit gap | #819 |

**Detection mechanism:** None. Parameter math produces the correct behavioral outcome (fewer avoidance goals for bold personalities) without identifying the underlying capacity mismatch.

### Social Regulation

| Gap combination | Operation needed | Personality constraint | Current handling | Source |
|---|---|---|---|---|
| Avoidant + confiding (SOCIAL_SUPPORT_SEEKING) | Trust + vulnerability disclosure | Low social regulation tendency, autonomy-dominant | CAPS settling via `SocialContextInputComputer` → low attractor strength for support-seeking → falls through to LLM | #805 |
| Callous + co-regulation (CO_REGULATION) | Empathic concern for another's distress | Low empathy spectrum → low perceived responsiveness | CAPS weights reduce co-regulation attractor activation | — |

**Detection mechanism:** None. CAPS network weights and personality modifiers (e.g., `flight_freeze_withdraw_pathways: 1.3` for avoidant) produce appropriate behavioral tendencies without explicit gap identification.

### Theory of Mind

| Gap combination | Operation needed | Personality constraint | Current handling | Source |
|---|---|---|---|---|
| Callous + empathic modeling | Perspective-taking, emotional resonance | High callousness → reduced empathy capacity | **Addressed.** `TheoryOfMindBridge` maps appraisal SEC→threat and action tendencies→behavior as soft evidence, runs the Bayesian network per-subject via `TomPersonalityModulation`. Anxiety shifts the threat prior (Bar-Haim 2007); callousness weakens the distress→avoidant coupling in the behaviour CPT (Blair 2005/2013); suspiciousness scales the behavior evidence vector, transferring approach weight to avoidant (Fenigstein & Vanable 1992; Combs et al. 2007). BDI intent biases trust via `RelationalSchemaEvolutionPhase`. | #1094, #1095, #1096, #1097, #1127 |

**Detection mechanism:** Personality modulation is now automatic. `CognitionCore.tick()` extracts anxiousness, callousness, and suspiciousness facets from `EffectiveCognitiveDefaults` and passes `TomPersonalityModulation` to the bridge. Different personality profiles produce measurably different posteriors from the same appraisal inputs.

**What's wired (2026-11-17):**
- `TheoryOfMindBridge` in `CognitionCore.tick()` — runs per-subject after `mentalModel.tick()`, mapping SEC dimensions (conduciveness→incongruence, controllability→powerlessness) to soft threat evidence and ActionTendencies to soft behavior evidence. Posterior stored on `MentalModelOrchestrator`.
- `MentalModelPromptSection` renders the Bayesian posterior alongside BDI text when dominant state exceeds 60% confidence.
- `RelationalSchemaEvolutionPhase` checks `MentalModelLookup` for inferred hostile/cooperative intent and biases trust delta asymmetrically (-0.15 hostile, +0.05 cooperative, gated at confidence ≥0.5).

**What was done (2026-11-18, #1097):**
- **Personality-modulated CPTs** — `TomPersonalityModulation` record carries anxiousness and callousness from `EffectiveCognitiveDefaults`. Anxiety interpolates the threat prior between relaxed `{0.80, 0.15, 0.05}` and anxious `{0.30, 0.40, 0.30}`. Callousness interpolates the distressed→behaviour row from `{0.05, 0.20, 0.75}` toward `{0.15, 0.50, 0.35}`. Linear interpolation preserves row normalisation automatically.
- `TheoryOfMindEngine(TomPersonalityModulation)` constructor builds the personality-conditioned network. No-arg constructor delegates with NEUTRAL (backward-compatible).
- `TheoryOfMindBridge.infer(AppraisalResult, TomPersonalityModulation)` overload. Original method delegates with NEUTRAL.

**What was done (2026-11-18, #1127):**
- **Suspiciousness-modulated evidence weighting** — `TomPersonalityModulation` now carries a third dimension: `suspiciousness` [0,1]. In `TheoryOfMindBridge.infer()`, after computing the behavior distribution, `scaleBehaviorEvidence()` transfers approach weight to avoidant proportional to `suspiciousness * 0.6`, then renormalizes. This is evidence scaling, not CPT modulation — the network structure stays unchanged. Literature: Fenigstein & Vanable 1992 (paranoia and interpretive bias); Combs et al. 2007 (attributional bias in suspicious individuals).
- `CognitionCore.tomModulation()` extracts `PersonalityFacet.SUSPICIOUSNESS` alongside anxiousness and callousness.
- Three modulation mechanisms now span distinct levels: prior (anxiousness), CPT (callousness), evidence (suspiciousness).

**What remains:**
- **Personality-gated empathic rendering** — `OtherModelPromptSection` still renders empathic content unconditionally. High callousness should attenuate the felt-sense of another's distress.

**Note:** Expression style derivation (CDE) differentiates callousness for behavioral output (e.g., "gallantry" expression varies from "genuine, protective" to "instrumental, helps when it serves a purpose"). With #1097, the observation quality is now also personality-modulated — the full chain from perception to expression is personality-conditioned.

### Defence Mechanisms

Defence mechanisms do not exist in the codebase. The CAPS topology defines cognitive distortions (all-or-nothing, overgeneralization, catastrophizing, mental filtering, emotional reasoning) as data-driven multiplicative effects, which are structurally different from personality-contingent coping strategies. The avoidant+projection gap listed in #832 is a future concern, not a current gap.

## Gap Detection Maturity

| Level | Description | Subsystems |
|---|---|---|
| **Explicit + systematic** | Per-parameter gap values, boolean signal, downstream rendering | Intelligence capacity (#840) |
| **Implicit + ad-hoc** | Returns empty/null, with case-specific downstream handling | Emotion regulation predictor |
| **Implicit via parameters** | Personality traits adjust thresholds, reducing feature activation | Proactive regulation, social regulation (CAPS) |
| **Personality-modulated** | Bayesian engine wired with personality-conditioned priors (anxiousness), CPTs (callousness), and evidence weighting (suspiciousness). Posterior feeds prompts and trust. Remaining: empathic rendering gating | Theory of mind |

## Development Checklist

When building personality-contingent cognitive features:

1. **Map cognitive operations** — What operations does this feature require? (reappraisal, perspective-taking, anticipatory planning, trust, vulnerability, self-reflection)

2. **Map enabling traits** — Which personality traits enable or disable each operation? Check against CDE derivation maps and CAPS disposition modifiers.

3. **Identify gap combinations** — Cross the operations with the trait space. Which (personality, operation) pairs produce a structural mismatch? Document these in this file.

4. **Route gaps explicitly** — Use one of:
   - **Explicit gap signal** (preferred for new features): follow the intelligence capacity pattern — compute gap values, propagate a boolean, generate personality-appropriate prose. See `SearchBudgetTickParticipant` step 7.
   - **Implicit fall-through** (acceptable for existing features): return `Optional.empty()` from the predictor/mechanical layer, ensure the LLM receives personality context and gap-specific prompt guidance. See `RegulationStrategyPredictor`.
   - **Parameter math** (acceptable when the parameter correctly captures the mismatch): adjust thresholds or weights based on personality traits so the feature naturally attenuates. See `ProactiveRegulationParticipant` threshold.

## Literature

- Gross, J.J. (1998). The emerging field of emotion regulation. *Review of General Psychology*, 2(3), 271-299.
- Nathanson, D.L. (1992). *Shame and Pride: Affect, Sex, and the Birth of the Self*. Norton.
- Bonanno, G.A. & Burton, C.L. (2013). Regulatory flexibility. *Perspectives on Psychological Science*, 8(6), 591-612.
- Zaki, J. & Williams, W.C. (2013). Interpersonal emotion regulation. *Emotion*, 13(5), 803-810.
- Halford, G.S. et al. (1998). Processing capacity defined by relational complexity. *Behavioral and Brain Sciences*, 21(6), 803-864.
