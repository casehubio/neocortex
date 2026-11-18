## D1: Adaptation phase placement

**Choice:** HygieneAdaptationPhase runs in the ConsolidationScheduler, reads KnowledgeGapSummary cross-system from MemoryHygieneOrchestratorImpl, stores adjusted parameters for the next hygiene tick to pick up. Two tick loops stay decoupled.
**Alternatives:**
- Move hygiene into consolidation scheduler — larger refactor, risks breaking separate cadence
- CDI event bridge — adds coupling without colocation benefit
**Rationale:** Preserves existing architecture boundaries. Consolidation and hygiene already run on independent schedules. Async parameter propagation is natural for a system where parameter changes take effect gradually.
**Trade-offs:** One-tick lag between adaptation computation and hygiene applying the new params. Acceptable because retention parameters are slow-moving.
**Sources:** MemoryHygieneOrchestratorImpl (cognition), ConsolidationScheduler (mindmap-intelligence), KnowledgeGapSummary (memory-api)
**Exploration:** quick
**Status:** captured

## D1a: Cross-system agent resolution (revision from review R1-03)

**Choice:** Add `tenantGapSummary(String tenantId)` on MemoryHygieneOrchestrator SPI. Aggregates KnowledgeGapSummary across all agents for that tenant (sum lowRetentionCount, sum consolidationGroups, sum totalScored). The adaptation phase calls this — no agentId needed.
**Alternatives:**
- Expose agent enumeration from gap state map — leaks internal storage structure
- Per-agent adaptation with separate configs — RetentionConfig is global, not per-agent; over-scoped for v1
**Rationale:** ConsolidationPhase.run() provides only tenantId. The hygiene orchestrator already holds per-agent gap state internally — a tenant-level aggregate is a natural read operation that doesn't expose internals.
**Trade-offs:** Per-agent granularity lost in aggregation. If one agent has poor retention while others are fine, the aggregate masks it. Acceptable for v1 — per-agent configs can be added later.
**Depends on:** D1
**Sources:** ConsolidationPhase.run(String tenantId, List<String>), MemoryHygieneOrchestratorImpl.gapStates
**Exploration:** quick (reviewer-prompted)
**Status:** captured

## D2: Making RetentionConfig dynamic

**Choice:** Define `RetentionConfigSupplier` (@FunctionalInterface, `RetentionConfig current()`) in cognition-api. Implement as `AdaptiveRetentionConfig` (@ApplicationScoped, volatile AtomicReference<RetentionConfig>) in cognition. Adaptation phase writes via `update(RetentionConfig)`; hygiene orchestrator reads via `current()` each tick instead of a constructor field. MemoryHygieneOrchestratorImpl modified to inject RetentionConfigSupplier and call `current()` per tick.
**Alternatives:**
- AtomicReference holder directly in cognition-api — CDI bean doesn't belong in API module (reviewer R1-06)
- MindMap sentinel node persistence — couples hygiene to MindMap
**Rationale:** Functional interface in API module is clean — no CDI annotations, no runtime concerns. Implementation in cognition alongside both producer and consumer. Hygiene orchestrator change is minimal: replace `this.retentionConfig` reads with `supplier.current()`.
**Trade-offs:** In-memory only — holder starts with DEFAULT on restart. No persistence in v1. Acceptable for a gradually-adapting system where startup defaults are safe.
**Depends on:** D1 (cross-system async propagation requires a shared mutable reference)
**Sources:** RetentionConfig (cognition-api), MemoryHygieneOrchestratorImpl (cognition)
**Exploration:** quick → revised after review R1-04, R1-06
**Status:** revised

## D3: Adaptation algorithm

**Choice:** Single-parameter adaptation: only adjust `retentionThreshold` in v1. Compute eviction pressure ratio (lowRetentionCount/totalScored). If pressure exceeds upper threshold (e.g. 0.3) → lower retentionThreshold by step (0.02). If pressure below lower threshold (e.g. 0.05) → raise retentionThreshold by step (0.02). Clamp to [0.01, 0.5]. Track previous tick's summary to detect direction — only adjust if quality degraded since last change; hold if it improved (previous adjustment was working).
**Alternatives:**
- Multi-parameter adaptation with "weakest dimension" — two signals can't uniquely drive five parameters (reviewer R1-05); weights should be personality-driven not quality-driven
- Stateless per-tick — creates oscillation feedback loop (reviewer R1-08)
- CBR-driven — cold-start problem; layer later
**Rationale:** retentionThreshold directly controls eviction aggressiveness. The four weights (confidence, recency, scope, trust) are relative importance settings that should reflect agent personality, not quality outcomes. Single-parameter with direction tracking is testable, explainable, and oscillation-free.
**Trade-offs:** Only one of five parameters adapts. If the problem is weight imbalance (e.g. recency overweighted), threshold adaptation won't fix it. Acceptable — weight adaptation is a personality concern, not a hygiene concern.
**Depends on:** D1 (phase reads KnowledgeGapSummary cross-system), D1a (tenant-level aggregation)
**Sources:** KnowledgeGapSummary (memory-api), RetentionConfig (cognition-api)
**Exploration:** quick → revised after review R1-05, R1-08
**Status:** revised

## D4: Module placement

**Choice:** RetentionConfigSupplier interface in cognition-api. AdaptiveRetentionConfig implementation + HygieneAdaptationPhase + HygieneAdaptationConfig + HygieneAdaptationEvent in cognition.
**Alternatives:**
- New module — over-engineering for this scope
- Holder in cognition-api — CDI bean doesn't belong in API module (reviewer R1-06)
**Rationale:** Follows existing pattern: cognition already has BeliefRevisionPhase, DriveAdaptationPhase, RelationshipStagePhase, RelationalSchemaEvolutionPhase, SituationalDeformationPhase, DispositionEvolutionPhase as consolidation phases. RetentionConfigSupplier is a pure functional interface — clean in the API module.
**Trade-offs:** cognition gains one more ConsolidationPhase — acceptable given 6 already exist there.
**Depends on:** D2 (SPI/impl split)
**Sources:** cognition module existing phases
**Exploration:** quick → revised after review R1-06
**Status:** revised

## D5: Consolidation phase priority

**Choice:** @Priority(48) — late in the pipeline, after SituationalDeformationPhase (46), before DispositionEvolutionPhase (50). The phase is ordering-independent within consolidation since it reads cross-system hygiene data.
**Alternatives:**
- @Priority(22) — collision with DriveDescriptionGenerationPhase (reviewer R1-02)
- @Priority(51+) — after everything, but puts it outside the natural 10-50 range
**Rationale:** The phase reads KnowledgeGapSummary from the previous hygiene tick, not current consolidation outputs. No ordering dependency on other phases. Running late ensures all phases that might indirectly affect hygiene quality have completed before the next adaptation cycle.
**Trade-offs:** None — ordering-independent by design.
**Depends on:** D1, D4
**Sources:** Full priority map verified via IDE: 28 phases, 10 (AccessFrequency) through 50 (DispositionEvolution)
**Exploration:** quick → revised after review R1-02
**Status:** revised

## D6: Config gate (from review R1-09)

**Choice:** `casehub.cognition.hygiene-adaptation.enabled` config property, default `false`. Phase is `@IfBuildProperty`-gated following the established platform pattern.
**Rationale:** Automated parameter tuning changes system behavior. Must be opt-in until validated. Follows CRAG, reranking, expansion, etc. pattern.
**Sources:** casehub.rag.crag.enabled, casehub.rag.reranking.enabled (established pattern)
**Exploration:** quick (reviewer-prompted)
**Status:** captured

## D7: Observability event (from review R1-11)

**Choice:** Fire `HygieneAdaptationEvent` CDI event after each parameter change. Record: previous config, new config, quality signals that drove the change, rationale (eviction pressure ratio, direction, step applied).
**Rationale:** Parameter drift must be visible to operators. Aligns with HygieneEvent pattern in existing hygiene system.
**Sources:** HygieneEvent sealed hierarchy (cognition-api), cognitive-observability module
**Exploration:** quick (reviewer-prompted)
**Status:** captured
