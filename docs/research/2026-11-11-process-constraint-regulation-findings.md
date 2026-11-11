# Process Constraint Technique — Regulation Findings

**Issue:** #933 (extends #905)
**Date:** 2026-11-11

## Background

Issue #905 established the process constraint technique for cognitive capacity simulation: tell the model HOW to reason under constraint rather than WHAT the character experiences. This resolved capacity eval failures from 0% to 90% pass rate. Issue #933 tested whether the same technique generalises to emotion regulation, fatigue, personality-based strategy selection, and drive profile differentiation.

## Four Interventions

### 1. Cognitive Load → Strategy Menu Restriction

Petter et al. (2025) identify cognitive load as a determinant of regulation strategy availability. The descriptive approach ("HIGH load → prefer ATTENTIONAL_DEPLOYMENT") was ignored by the LLM. The process constraint replaces this with a hard menu restriction: "Under HIGH cognitive load, you cannot hold the multi-step reasoning chain needed for COGNITIVE_CHANGE. Only ATTENTIONAL_DEPLOYMENT and RESPONSE_MODULATION are executable — they're reflexive, not deliberative. You MUST choose one of these two."

Applied in both the standalone `CognitiveLoadStrategyEvalTest` prompt and in `EmotionRegulationGenerationPhase.buildUserMessage()`.

### 2. Graded Fatigue Degradation

Replaced the flat "Regulation fatigue: N%" label with three procedural tiers:

- **Low (<50%):** Descriptive label only — no constraint needed, strategies still available.
- **Moderate (50–70%):** "COGNITIVE_CHANGE requires deliberate effort — each attempt is slower and less convincing than the last." Describes degraded process, not degraded state.
- **Severe (>70%):** "Your reappraisal capacity is depleted. Only ATTENTIONAL_DEPLOYMENT remains. You MUST choose it." Hard process constraint — the menu shrinks.

### 3. Personality-Based Strategy Inner Monologue

When `RegulationDefaults` indicate a dominant capacity (>0.6, not tied), the prompt now renders an inner-monologue constraint: "Your instinct is to control the outward expression. You feel the emotion fully inside but your face stays steady. Choose RESPONSE_MODULATION." This replaces numeric capacity scores (reappraisal=0.80, suppression=0.40) that the LLM had no procedural anchor for.

### 4. Drive Description Rendering Fallback

Root cause analysis of DriveProfilePipelineTest (12/12 fail) revealed a simpler problem than expected. `CognitionObservationBuilder.renderInternalState()` only rendered LLM-generated drive descriptions (`DriveDescriptionGenerated` trait nodes). The test never seeds these nodes, so drives rendered as bare labels (`GALLANTRY (0.9)`) without description text. A 3-line fallback to `DriveSnapshot.description()` resolved all 12 failures.

## Key Finding

The process constraint technique generalises beyond capacity to any trait-dependent LLM behaviour. The pattern: replace descriptive metadata ("you are X") with procedural constraints ("you can only do Y, using process Z"). This works because process constraints operate at the generation level — the model follows instructions to restrict its own reasoning, which naturally produces trait-consistent output without requiring genuine cognitive limitation.

## Surprise

The 12/12 drive profile failure appeared to be a CAPS discrimination problem (overlap coefficients, classifier sensitivity, activation thresholds). Investigation showed the architecture is sound — resting activations and trivially small overlap gaps mean most drives pass filtering. The actual root cause was a missing rendering fallback. Hours of structural analysis yielded a 3-line fix.

## Open Questions

- **#939:** Empty classification results bypass drive filtering entirely — tests can pass for wrong reasons.
- **#940:** `detectRenderedDrives()` uses fragile verbatim string matching that breaks on any text transformation.
- **#941:** Whether personality-aware situation classification (passing disposition to the classifier) produces measurably different CAPS activations for the same scenario across characters.

## References

- Gross, J. J. (1998). The emerging field of emotion regulation. *Review of General Psychology*, 2(3), 271–299.
- Milicka, J. et al. (2024). Can LLMs simulate child cognition? *PLOS ONE*.
- Petter, E. A. et al. (2025). Cognitive load and emotion regulation strategy selection.
