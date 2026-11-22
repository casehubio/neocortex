# Decisions — #894 Pipeline Coherence Eval

## D1: Harness extension scope

**Choice:** ~~Include all harness extensions within #894~~ **REVISED (R1-07):** No harness extensions needed. Use existing `CognitionCoreTestBuilder` + `EvalTestSupport` in `caps-llm-classifier`.
**Alternatives:**
- Extend CognitionTestHarness — weaker harness, lacks SessionPipeline, stubbed drives, regulation dead path
**Rationale:** CognitionCoreTestBuilder already provides real SessionPipeline, real drives, full orchestrators. Post-build configure calls on CognitionCore handle appraisal, regulation, and snapshot store.
**Trade-offs:** None — strictly better than extending the weaker harness
**Sources:** CognitionCoreTestBuilder.java, RegulationFullPipelineEvalTest.java, design review R1-07
**Exploration:** quick
**Status:** revised

## D2: LLM judge tier

**Choice:** Configurable — build single-tier now with an abstraction that allows swapping in two-tier (Haiku→Opus) later
**Alternatives:**
- Single-tier only — simpler but locks out the two-tier approach specified in the issue
- Two-tier Haiku→Opus — as specified but more complex to build, more expensive per run, and unproven in this codebase
**Rationale:** Single-tier is proven (20+ existing eval tests). Abstraction is cheap. Two-tier can be added when/if single-tier proves insufficient.
**Trade-offs:** Slight abstraction overhead; two-tier implementation deferred
**Sources:** EvalLlmJudge.java (caps-llm-classifier/src/test/java), issue #894 spec
**Exploration:** quick
**Status:** captured

## D3: Test module placement

**Choice:** ~~cognition module~~ **REVISED (R1-03):** `caps-llm-classifier` — follows established eval pattern (20+ tests, existing Maven profile, CognitionCoreTestBuilder, EvalTestSupport)
**Alternatives:**
- cognition module — would need new Maven profile, new dependencies, and a weaker harness
**Rationale:** All eval infrastructure lives in caps-llm-classifier. The test uses CognitionCoreTestBuilder + EvalTestSupport which are already there. No pom.xml changes needed.
**Trade-offs:** None — follows the established pattern
**Sources:** Design review R1-03, caps-llm-classifier/pom.xml
**Exploration:** quick
**Status:** revised

## D4: Scenario scope

**Choice:** All 4 scenarios — shame→regulation (revised from shame→suppression), curiosity→exploration, cross-tick trajectory, appraisal→channel weight
**Alternatives:**
- 3 scenarios (defer regulation) — unnecessary now that CognitionCoreTestBuilder provides real regulation
- 2 foundational + 2 stretch — incremental but leaves coverage gaps
**Rationale:** CognitionCoreTestBuilder with SessionPipeline provides all needed infrastructure. All 4 scenarios exercise different pipeline pathways.
**Trade-offs:** LLM cost per test run (real appraisal + regulation calls)
**Sources:** Issue #894 scenario list, design review R1-01/R1-02
**Exploration:** quick
**Status:** revised

## D5: Coherence gate threshold

**Choice:** Hard gate at mean ≥ 4.0 across all stage-transition coherence scores (0.0-5.0 double scale)
**Alternatives:**
- Soft report + hard at 3.0 — more forgiving during calibration but weaker quality signal
- Per-scenario thresholds — different thresholds per complexity, but adds calibration burden
**Rationale:** Pipeline incoherence is a bug, not an edge case. Double scale (R1-11) gives finer granularity.
**Trade-offs:** May need initial calibration passes to ensure scenarios are achievable at 4.0
**Sources:** Issue #894, design review R1-11
**Exploration:** quick
**Status:** revised

## D6: Coherence classification approach

**Choice:** Stage-transition coherence — define the pipeline as an ordered sequence of stages, assess coherence at each transition with directional semantics. Tick-time and consolidation-time transitions distinguished (R1-01).
**Alternatives:**
- Trace-pair matrix — independent judge call per field pair, more granular but pairs lack directional semantics
- End-to-end narrative — single judge pass per scenario, cheapest but least diagnostic
**Rationale:** Transitions carry causal semantics. Per-transition scores pinpoint where coherence breaks. Architectural separation of tick vs consolidation data prevents false causal chains.
**Trade-offs:** More judge calls than end-to-end. Requires defining expected transitions per scenario.
**Sources:** PipelineTrace fields, CognitionCore.currentTrace(), design review R1-01
**Exploration:** quick
**Status:** revised