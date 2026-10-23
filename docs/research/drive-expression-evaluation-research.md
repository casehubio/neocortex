# Era 2: How the Pipeline Expresses Drives — CAPS, Appraisal & Differentiation

**Status:** COMPLETE

Rolling research journal for the drive expression investigation. Covers the regression recovery from BASELINE2, pipeline instrumentation, appraisal filter mechanics, and the discovery that the "regression" was quality redistribution.

---

## Context from Era 1

Era 1 (`era1-character-taxonomy-research.md`) established:

- **BASELINE0:** full brief, mean drive expression 3.93. All tendencies, no metacognitive instruction.
- **L1 emotional echo:** mean 4.06 (+0.13). Improves 13/17 drives but regresses 4: PP protection (-1.13), DD gloating (-0.48), DD recognition (-0.37), PEN social-harmony (-0.33). These 4 regressions motivated this era's pipeline work.
- **Drives are authoritative** (P1). Tendencies that restate them are redundant. Drives carry personality; the pipeline controls how they express.
- **Identity activation** (P13, P19). "Think AS, not ABOUT" + "name your feeling" = compounding feedback loop. L1 instruction stays unchanged throughout Era 2.
- **Analytical instructions kill expression** (P11). The main agent must stay immersive. Sub-LLMs can be analytical (P39).
- **Phase 7 validated architecture:** minimal drives + one Haiku appraisal call + L1 instruction.

---

## Phase 1: The Regression (Sessions 1-8)

### Starting point

BASELINE2 eval (`bugfixes-pinned-2026-10-17`, 310 events) showed regressions from the prior eval:

| Drive | Prior eval | BASELINE2 | Delta |
|---|---|---|---|
| PP protection | 4.16 | 3.45 | -0.71 |
| AHM protection | 4.71 | 4.26 | -0.45 |
| PEN adventure | 3.00 | 2.78 | -0.22 |
| PP gallantry | 2.50 | 3.34 | +0.84 |

Five variables changed simultaneously between evals — impossible to attribute to a single cause.

### Bug fixes that preceded the regression

Four bugs fixed between the two evals (conductor session 1):

1. **Delete-before-write** (neocortex#609) — consolidation tick 2 deleted tick 1 descriptions before generating replacements. PEN lost all somatic descriptions.
2. **Missing differentiation** (neocortex#608) — only 10/21 drive pairs had differentiation. Required shared CAPS nodes. Fix: generate for all C(n,2) pairs.
3. **findFirst() drop** (examples#126) — `readDriveDifferentiation()` used `.findFirst()`. Only one differentiation text per character reached the prompt.
4. **Template leak** (examples#127) — raw `{{serves:}}` metadata leaking into attractor prompt text.

The `findFirst()` fix (bug 3) combined with all-pairs differentiation (bug 2) caused differentiation text count to jump from 1→3 (PP) and 0→3 (AHM).

### Early hypothesis: differentiation text dilution

Session 8 built micro regression tests for PP and AHM protection. Both scored 5/5 — but the tests only had 1 differentiation text seeded. The session ended with the observation that "micro tests must reproduce regression conditions."

### Active-drive differentiation filter

Implemented (commit 81b889a): only emit differentiation texts for drive pairs where BOTH drives passed the appraisal filter. Easterbrook (1959) — irrelevant content degrades attentional performance.

## Phase 2: First-Principles Re-evaluation (Session 9)

### The handover was wrong

Session 8's handover claimed "only the gallantry/proving-worth differentiation text remains (protection isn't active alongside gallantry in a pure protection scenario)."

First-principles calculation showed the opposite. With PP's real intensities:

| Drive | Intensity | Resting (i² × 0.22) | Self-sustains (> 0.15)? |
|---|---|---|---|
| gallantry | 0.9 | 0.178 | Yes |
| protection | 0.8 | 0.141 | No |
| proving-worth | 0.7 | 0.108 | No |

Gallantry self-sustains at 0.178 — it passes appraisal regardless of what the classifier returns. With a protection-triggering classifier, active drives = {gallantry, protection}. The surviving text is gallantry↔protection, not gallantry↔proving-worth. The handover had the wrong text.

### All-pairs micro tests still score 5/5

Built `buildFullPipelineObservationAllPairs` with all 3 differentiation texts seeded. Both variants — classifier-unaware (all texts included) and classifier-aware (filter active) — scored 5/5 on the ceiling-collapse trigger.

**Learning:** A single strong trigger overwhelms differentiation text noise. The dilution effect is statistical — visible across hundreds of diverse events, not per-observation on strong triggers. Micro tests with strong triggers can serve as capability gates but not regression reproducers.

### The test classifier is dead code for protection

The test classifier in `buildFullPipelineObservation` had no protection-triggering keywords (no "crash", "danger", "screams"). When the classifier returns empty, `appraisalFilterDrives` returns ALL drives unfiltered (bypass path at line 515). The active-drive filter never ran during PP protection tests. The existing 5/5 scores passed not because the filter worked, but because there was only 1 differentiation text.

### Pipeline instrumentation

Added three structured log lines per event to enable causal analysis:

```
[appraisal-filter]        — classifier activations, per-drive effective activation, rendered/filtered
[differentiation-filter]  — total/included/excluded texts, which pairs excluded, active drives
[observation-assembly]    — component counts (drives, diffTexts, memories, attractors, totalParts)
```

## Phase 3: Statistical Confirmation (Session 10 — Conductor)

### 50-event instrumented eval

Overnight, the conductor ran a 50-event eval with instrumentation active. 86 events collected, 93 instrumentation records captured.

**PP protection score by differentiation text count:**

| DiffTexts | Avg protection score | n |
|---|---|---|
| 0 | 4.00 | 2 |
| 1 | 3.00 | 1 |
| 3 | 2.80 | 5 |

Clear inverse correlation: -1.20 from 0→3 diff texts. Dilution hypothesis confirmed statistically.

### Two failure modes identified

**Failure mode 1 — classifier bypass (PP).** `LlmSituationClassifier` returned empty in 67% of PP events. All drives passed unfiltered → all 3 diff texts included → maximum noise.

**Failure mode 2 — threshold too low (AHM).** AHM's drive intensities (loyalty 0.9, protection 0.8, suspicion 0.6) all clear the appraisal threshold when the classifier is active. All 3 diff texts always included — 100% of events.

### Fix A — guard diff texts against classifier bypass

When the classifier returns empty, skip differentiation entirely. Without situation context, no evidence of simultaneous activation → diff texts are noise not signal. Barrett (2017).

**Results:** AHM protection +0.30, HC dominance +0.79, PEN social-harmony +0.56. But PP protection -0.58.

## Phase 4: The Test Infrastructure Was Broken (Session 11)

### Discovery: fake profiles

The test pipeline hardcoded drive profiles with almost no relationship to production:

| Drive | Test profile | Production profile |
|---|---|---|
| PP protection | `acceptance:0.5, physical_threat:0.4` (total 0.9) | `modeling_observed:0.650, physical_threat:0.800, secure_attachment:0.900, agency_granted:0.550, neglect:0.700` (total 3.6) |
| PP gallantry | `acceptance:0.7, sharing:0.5` (total 1.2) | `acceptance:0.850, aversive_removal:0.700, social_threat:0.800` (total 2.35) |
| PP proving-worth | `mastery:0.8, competence_recognition:0.6` (total 1.4) | `reward:0.850, failure:0.800, performance_judged:0.880, competition:0.850, mastery:0.920` (total 4.3) |

The overlap formula divides by `profileTotal`. A 0.9-total profile produces completely different appraisal behaviour from a 3.6-total profile. Every appraisal conclusion drawn from tests was unreliable.

### Production-aligned pipeline

Built `buildRealisticPipelineObservation` using:
- Real derived profiles from cognitive dump data
- Real `LlmSituationClassifier` (Haiku + 28-node CAPS topology)
- Real differentiation texts from cognitive dump

### The over-filtering hypothesis was wrong

With real profiles, PP protection survives appraisal for BOTH physical and social threat scenarios:
- Physical threat: classifier returns `physical_threat:0.95` → protection overlap via `physical_threat:0.800` in profile → passes
- Social threat: classifier returns `neglect:0.58, social_threat:0.80` → protection overlap via `neglect:0.700` → passes

The fake profiles were too narrow to find the overlaps the real pipeline finds. The 40% over-filtering observed in production eval data happens in genuinely non-protective scenarios — the filter is working correctly.

### The regression was redistribution

Principle 74 (already documented but not connected to the investigation): "Differentiation redistributes quality between confused drives — combined quality is conserved, not created."

PP gallantry +0.84 and protection -0.71 = combined conserved (6.66→6.79). The 4.16 protection score was inflated by gallantry confusion. Protection's true level, once gallantry confusion is removed, is ~3.0-3.5.

This reframing changed the path forward: from "fix the regression" to "raise the true level through richer cognitive content."

### Fix A harms PP specifically

Per-event data: PP protection max collapsed from 5→3 after Fix A. Protection scored 2.80 even with 0 diff texts — noise removal didn't help.

The differentiation texts HELP PP by providing somatic cues that guide the LLM toward protection-specific behavior. Fix A removes these during classifier bypass (67% of PP events), removing helpful signal rather than noise.

Fix A is net positive (other characters improve), but has a PP-specific cost. Unresolved — options include improving classifier coverage, reverting for PP only, or improving somatic descriptions.

## Phase 5: Focused Eval Framework (Session 11)

### Problem: 300 events is too slow for iteration

Two hours per eval run. Most events irrelevant to the drive under investigation. Need a faster loop for cognitive layering experiments.

### Design: 10 scenarios covering the combinatorial space

Eight PP scenarios spanning:
- **Scenario strength:** strong (physical danger), medium (social/psychological), weak (emotional/subtle), none (control)
- **Threat type:** physical, social, psychological, emotional, achievement
- **Drive competition:** protection alone, protection + gallantry, all three, non-protection controls

Plus two sanity checks (AHM protection, HC scheming).

Production-aligned pipeline throughout. Results saved to JSON for cross-run comparison. Total runtime ~25 minutes.

### Baseline results (2026-10-18)

| # | Scenario | Prot | Gal | PW |
|---|----------|------|-----|-----|
| S1 | Ceiling collapse | 5 | 1 | 1 |
| S2 | Fire rescue | 5 | 3 | 1 |
| S3 | Social insult | 5 | 5 | 2 |
| S4 | Gaslighting | 4 | 4 | 4 |
| S5 | Clara withdrawn | 2 | 5 | 2 |
| S6 | Engine repair | 2 | 2 | 4 |
| S7 | Door stuck | 1 | 5 | 3 |
| S8 | Bridge collapse | 5 | 1 | 2 |
| S9 | AHM protection | 5 | — | — |
| S10 | HC scheming | 5 | — | — |

Drive differentiation working correctly: protection at 5 for danger, 1 for non-danger; gallantry at 5 for chivalric moments, 1 for pure emergencies; proving-worth at 4 for competence challenges, 1-2 elsewhere.

## Key Principles Established

1. **Redistribution before regression.** When differentiation is added, always check combined scores across confused pairs before diagnosing regression.

2. **Test profiles must match production.** Hardcoded test profiles that differ from production-derived profiles produce unreliable appraisal behavior. Use real values from cognitive dumps.

3. **Statistical effects need statistical tests.** Single-observation micro tests with strong triggers cannot reproduce effects that are visible only across many diverse events. Use focused eval (10+ scenarios) or full eval (300 events) for statistical hypotheses.

4. **Instrumentation before hypothesis.** Adding per-event structured logging (`[appraisal-filter]`, `[differentiation-filter]`, `[observation-assembly]`) enabled causal analysis that guessing from averages never could.

5. **First principles over handover claims.** Calculate the math yourself. The appraisal filter mechanics, overlap ratios, and self-sustaining thresholds are deterministic — verify claims against the formula, not against someone's description.

## References

- Barrett, L.F. (2017). *How Emotions Are Made: The Secret Life of the Brain.*
- Barrett, L.F. & Satpute, A.B. (2019). Historical pitfalls and new directions in the neuroscience of emotion.
- Easterbrook, J.A. (1959). The effect of emotion on cue utilization and the organization of behavior.
- Green, D.M. & Swets, J.A. (1966). *Signal Detection Theory and Psychophysics.*
- Gross, J.J. (1998). The emerging field of emotion regulation.
- Mischel, W. & Shoda, Y. (1995). A cognitive-affective system theory of personality.
- Tracy, J.L. & Robins, R.W. (2004). Putting the self into self-conscious emotions.
- Baker, C.L., Saxe, R. & Tenenbaum, J.B. (2017). Rational quantitative attribution of beliefs, desires and percepts.
