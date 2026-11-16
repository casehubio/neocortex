# Decisions — #957 SEC→OCC Emotion Mapping

## D1: Novelty-based HOPE mapping

**Choice:** Add HOPE rule: `novelty > 0.4 && conduciveness >= 0` → HOPE with intensity `relevance * novelty`
**Alternatives:**
- Add INTEREST as new EmotionType — OCC model doesn't include it; would require AlmaPadTable entry, AppraisalDriveModulator mapping, and downstream consumers
- Lower JOY's CURIOSITY weight from SECONDARY_BOOST to BOOST — conflates positive affect with exploratory drive
**Rationale:** HOPE is already in the OCC taxonomy with CURIOSITY BOOST (0.5) in AppraisalDriveModulator. Novel + non-threatening situations elicit anticipatory prospect evaluation, which is HOPE's OCC definition. Combined CURIOSITY delta jumps from ~0.07 to ~0.33.
**Trade-offs:** Novel neutral situations (conduciveness ≈ 0) now produce HOPE, which may be slightly optimistic. The `>= 0` threshold prevents HOPE in threatening situations.
**Sources:** EmotionMapper.java, AppraisalDriveModulator.java (HOPE→CURIOSITY at BOOST), AlmaPadTable.java (HOPE PAD: 0.2, 0.2, -0.1)
**Exploration:** quick
**Status:** captured

## D2: Urgency-widened FEAR threshold + co-occurrence

**Choice:** When `urgency > 0.5`, relax FEAR controllability threshold from 0.4 to 0.6. Replace else-if chain with independent if statements; DISTRESS is fallback only when neither FEAR nor ANGER fired.
**Alternatives:**
- Always use 0.6 threshold — would over-produce FEAR in non-urgent low-control situations
- Keep else-if chain but add urgency check as additional FEAR rule — preserves mutual exclusivity but misses mixed-emotion scenarios
**Rationale:** Urgency signals time pressure that amplifies threat appraisal regardless of coping potential. Independent if statements allow FEAR + ANGER co-occurrence (fear of situation + anger at cause), which is psychologically valid for crises. DISTRESS remains the fallback for ambiguous mid-range controllability without urgency.
**Trade-offs:** FEAR and ANGER can now co-occur, producing two drive modulations. This is correct for crises but produces more complex emotional states.
**Sources:** EmotionMapper.java:31-40, PipelineCoherenceEvalTest.java (appraisalChannelWeightCoherence scenario), SecDimensions.URGENCY
**Exploration:** quick
**Status:** captured

## D3: Urgency-based arousal boost on PAD projection

**Choice:** After AlmaPadTable.project(), add `urgency * 0.5 * intensity` to the arousal component, clamped to [-1, 1]. Applied in EmotionMapper's emotion() helper.
**Alternatives:**
- Modify AlmaPadTable canonical values — violates Mehrabian literature grounding
- Add arousal as a separate post-processing step in AppraisalTickParticipant — spreads the mapping logic across two classes
**Rationale:** Urgency is an environmental activation signal orthogonal to the emotion's canonical arousal. Scaling by intensity keeps the boost proportional to emotional engagement. The 0.5 coefficient means urgency=0.8 adds 0.4 * intensity to arousal — enough to lift SHAME from 0.1 to ~0.35 at full intensity.
**Trade-offs:** AlmaPadTable.project() no longer produces the final PAD — EmotionMapper applies a context-dependent adjustment. Consumers of AlmaPadTable directly won't see the boost.
**Sources:** AlmaPadTable.java (SHAME arousal = 0.1), PipelineCoherenceEvalTest.java (shameRegulationChain expects high arousal)
**Exploration:** quick
**Status:** captured

## D4: Three-tier test strategy

**Choice:** Unit tests (EmotionMapperTest), harness integration (CognitionTestHarness with stub AppraisalStrategy), LLM eval (existing PipelineCoherenceEvalTest)
**Alternatives:**
- Unit + LLM eval only — misses integration wiring between mapping and drives
- Unit + harness only — doesn't validate end-to-end coherence with real LLM appraisal
**Rationale:** Unit tests verify mapping rules mechanically. Harness tests verify propagation through drives and channel weights without API keys (CI-safe). LLM eval validates coherence with real appraisal output.
**Trade-offs:** Harness test requires a stub AppraisalStrategy, adding test infrastructure.
**Sources:** EmotionMapperTest.java, CognitionTestHarness, PipelineCoherenceEvalTest.java
**Exploration:** quick
**Status:** captured
