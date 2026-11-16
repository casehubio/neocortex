# SEC→OCC Emotion Mapping — Arousal Calibration and Taxonomy Gaps

**Issue:** casehubio/neocortex#957
**Date:** 2026-11-14

## Problem

3/4 `PipelineCoherenceEvalTest` scenarios fail due to gaps in `EmotionMapper`:

1. **No novelty-based emotion** — `novelty` SEC dimension is used in `mapTendencies()` but ignored in `mapEmotions()`. Novel discovery produces JOY (CURIOSITY at SECONDARY_BOOST 0.25), not HOPE (CURIOSITY at BOOST 0.5). The curiosityExplorationChain eval gets a CURIOSITY delta of ~0.07 — far too small.

2. **FEAR threshold too strict** — FEAR requires `controllability < 0.4`. The exclusive `else if` chain means controllability in [0.4, 0.5] falls through to DISTRESS. Urgency plays no role. The appraisalChannelWeightCoherence eval's security breach scenario maps to DISTRESS + SHAME instead of FEAR.

3. **Context-blind arousal** — `AlmaPadTable.project()` scales canonical PAD by intensity. SHAME's canonical arousal is 0.1, so even at full intensity arousal = 0.1. The shameRegulationChain eval's public confrontation expects high arousal.

4. **LLM prompt missing urgency** — `LlmAppraisalStrategy` requests only 6 of 8 SEC dimensions. Urgency is absent, so urgency-dependent fixes (2 and 3) never activate through the LLM appraisal path used by eval tests.

## Changes

### C1: Add urgency to LLM appraisal prompt

**File:** `LlmAppraisalStrategy.java`

Add `urgency` to the JSON schema in the system prompt:
```
"urgency": <0.0-1.0, how time-pressured is the response>
```

This is the minimum change needed to surface urgency through the LLM path. The dimension is already defined in `SecDimensions.URGENCY` and consumed by `EmotionMapper.mergeDimensions()`.

### C2: Novelty-based HOPE rule

**File:** `EmotionMapper.java`

New rule in `mapEmotions()`:
```java
if (novelty > 0.4 && conduciveness >= 0) {
    intensity = clampIntensity(relevance * novelty);
    → HOPE
}
```

OCC grounding: HOPE is prospect-based, triggered by positive evaluation of uncertain outcomes. Novel + non-threatening situations represent unexplored positive prospects. HOPE maps to CURIOSITY at BOOST (0.5) in `AppraisalDriveModulator`, giving a combined CURIOSITY delta of ~0.33 (with JOY's 0.25 contribution) vs the current ~0.07.

JOY still fires independently when `conduciveness > 0.3`. Novel positive events produce both JOY and HOPE.

### C3: Urgency-widened FEAR threshold

**File:** `EmotionMapper.java`

Replace the `else if` chain for FEAR/ANGER/DISTRESS with independent `if` statements:

- FEAR: `conduciveness < -0.2 && controllability < fearThreshold` where `fearThreshold = urgency > 0.5 ? 0.6 : 0.4`
- ANGER: `conduciveness < -0.2 && controllability > 0.5` (unchanged logic)
- DISTRESS: `conduciveness < -0.2` only when neither FEAR nor ANGER fired (fallback)

This allows FEAR + ANGER co-occurrence in crises (fear of situation + anger at cause). The urgency-based threshold relaxation captures threat scenarios where the agent has moderate controllability but faces time pressure.

### C4: Urgency-based arousal boost

**File:** `EmotionMapper.java`

After `AlmaPadTable.project()`, add `urgency * 0.5 * intensity` to the arousal component, clamped to [-1, 1]:

```java
arousalBoost = urgency * 0.5
adjusted arousal = min(1.0, base_arousal + arousalBoost * intensity)
```

Preserves canonical AlmaPadTable values when urgency is absent. For SHAME with urgency=0.7 and intensity=0.56: base arousal 0.056 → adjusted 0.252.

### C5: Three-tier test strategy

**Tier 1 — Unit tests** (`EmotionMapperTest.java`):
- `hopeFromNoveltyWithPositiveConduciveness` — novelty 0.7, conduciveness 0.5 → HOPE
- `noHopeWhenNegativeConduciveness` — novelty 0.7, conduciveness -0.3 → no HOPE
- `fearWithUrgencyElevatedThreshold` — urgency 0.8, controllability 0.5 → FEAR
- `fearAndAngerCoOccurrence` — urgency 0.8, controllability 0.55 → both FEAR and ANGER
- `arousalBoostedByUrgency` — urgency 0.8 + SHAME → arousal significantly above baseline

**Tier 2 — Harness integration** (new `EmotionMappingIntegrationTest.java` in cognition test dir):
Verify EmotionMapper changes propagate through to drives and mood via CognitionTestHarness. Use `withAppraisalResult()` to inject controlled emotions (HOPE, FEAR with boosted arousal) and verify downstream: CURIOSITY drive activation, mood arousal state, action tendencies. No LLM API required — runs in CI.

**Tier 3 — LLM eval** (existing `PipelineCoherenceEvalTest.java`):
The three failing scenarios should now pass with the mapping fixes. No changes to the eval test code — the improvements flow through EmotionMapper automatically.

## Files Changed

| File | Change |
|------|--------|
| `cognition/src/main/java/.../appraisal/EmotionMapper.java` | HOPE rule, urgency FEAR threshold, arousal boost, independent if statements |
| `cognition/src/main/java/.../appraisal/LlmAppraisalStrategy.java` | Add urgency to system prompt |
| `cognition/src/test/java/.../appraisal/EmotionMapperTest.java` | 5 new unit tests |
| `cognition/src/test/java/.../appraisal/EmotionMappingIntegrationTest.java` | New harness integration test |

## Not Changed

- `AlmaPadTable.java` — canonical Mehrabian PAD values preserved
- `AppraisalDriveModulator.java` — drive mappings unchanged by #957 (subsequently modified by #983: removed SHAME→AFFILIATION reduction, and #984: added tanh diminishing returns)
- `EmotionType.java` — using existing OCC types, no new INTEREST/SURPRISE
- `PipelineCoherenceEvalTest.java` — existing scenarios validate the fix

## References

- EmotionMapper.java:14-102 — current mapping rules
- AlmaPadTable.java:32 — SHAME PAD (−0.3, 0.1, −0.6)
- AppraisalDriveModulator.java:64-68 — HOPE→CURIOSITY(BOOST), JOY→CURIOSITY(SECONDARY_BOOST)
- LlmAppraisalStrategy.java:24-64 — system prompt missing urgency
- PipelineCoherenceEvalTest.java — failing scenarios at lines 128, 200
- casehubio/neocortex#938 — prior cross-stage coherence fix
