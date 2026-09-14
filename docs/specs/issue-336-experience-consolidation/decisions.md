## D1: Graduation scoring via SPI in neocortex

**Choice:** Define a `GraduationScorer` @FunctionalInterface SPI in memory-api (package `io.casehub.neocortex.memory.experience`). @DefaultBean in mindmap-intelligence returns the memory's confidence (or 0.5 if null). Blocks or application code provides richer scoring (surprise, arousal, etc.).
**Alternatives:**
- Inline pure Java — hardcode composite score directly in the phase. No extension point for downstream consumers.
- Reuse ConfidenceScorer from blocks — requires extracting the interface to a shared module. Wrong input type (ScoredCbrCase vs Memory).
**Rationale:** Blocks scorers operate on `ScoredCbrCase<CbrCase>`, not `Memory`. Dependency direction is blocks → neocortex, so neocortex can't import blocks types. An SPI in neocortex lets blocks provide a rich implementation while the default works standalone.
**Trade-offs:** Slightly more surface area than inline scoring, but matches the established neocortex SPI pattern (ReflectionSynthesizer, PlanAdapter, QueryExpander).
**Sources:** blocks/memory/SurpriseScorer.java, blocks/memory/ArousalScorer.java, blocks/memory/ConfidenceScorer.java, memory-api MemoryInput/Memory types
**Exploration:** quick
**Status:** captured

## D2: Classification via SPI with rule-based default

**Choice:** Define a `GraduationClassifier` SPI that maps a graduated Memory to a cognitive type and properties. @DefaultBean uses metadata-driven mapping (event-type → cognitive kind). LLM-backed implementations provided by downstream consumers.
**Alternatives:**
- Always LLM — requires AgentProvider, limits deployability, no fallback when LLM unavailable
- Hybrid (rules then async LLM) — category error: ConversationBridge's two-speed model (fast paragraph segmentation → async LLM entity extraction) works because segments are generic placeholders refined to typed entities. Experience events are already structured (sealed interface: Observation, Action, Outcome) — rule-based classification produces definitive types, not placeholders needing refinement. LLM reclassification would be retraction (Belieflike → Fearlike), not refinement. Downstream processing (trait rules, derived edges) would run on wrong data during the interim.
- Rule-based only, no SPI — no extension point, not disposition-aware
**Rationale:** Classification is interpretive — the same observation can produce a Belieflike, Fearlike, or Evaluative node depending on agent disposition. This is the downstream consumer's concern, not neocortex's. The SPI lets wacky-manor provide disposition-aware classification while neocortex works standalone with deterministic defaults.
**Trade-offs:** Rule-based default produces generic classification that doesn't reflect agent personality. Acceptable because MergeDetectionPhase and trait rules provide post-hoc enrichment.
**Sources:** #322 cognitive node types (Belieflike, Evaluative, etc.), ConversationBridge.java, ExtractionRequestedObserver.java, MindMapExtractor.java
**Exploration:** deep-analysis
**Status:** captured

## D3: Cursor-based pagination for idempotency

**Choice:** Store a `graduation-cursor` (memory ID) on a sentinel node in TYPE_SYSTEM. Use `CaseMemoryStore.scan(MemoryScanRequest)` with `afterMemoryId` cursor for pagination. Advance cursor after each successful pass.
**Alternatives:**
- Timestamp watermark with MemoryQuery.withSince — MemoryQuery requires non-empty subjects, making it unsuitable for cross-agent domain scans. MemoryScanRequest supports domain-based scanning without subjects.
- Per-memory attribute stamp — requires write-back to CaseMemoryStore. No `updateMemory` SPI exists.
- Separate tracking store — more state to manage, overkill for a single cursor.
**Rationale:** O(1) state, persistent via MindMapStore backend, cursor-based pagination via the existing scan API. MemoryScanRequest.domain filters to experience memories; afterMemoryId resumes from last processed position.
**Trade-offs:** Cursor relies on stable memory ordering in the scan implementation. If a store reorders memories, the cursor could skip or reprocess. Acceptable because all current store implementations maintain insertion order.
**Sources:** MemoryScanRequest.java (domain + afterMemoryId), MemoryQuery.java:30 (subjects non-empty validation)
**Exploration:** quick
**Status:** revised — watermark → cursor after self-review found MemoryQuery requires non-empty subjects

## D4: One node per graduated event

**Choice:** Create one mindmap node per graduated experience event. Let MergeDetectionPhase (@Priority 20) handle deduplication in the same or subsequent tick.
**Alternatives:**
- Batch by subject+turn — fewer nodes but partially duplicates MergeDetection's responsibility
- Batch by agent+session — maximum compression but loses individual event granularity
**Rationale:** Separation of concerns. The graduation phase decides what's worth keeping. MergeDetection decides what's redundant. Combining them couples two independent concerns.
**Trade-offs:** May create duplicate nodes that get merged one tick later. Acceptable because MergeDetection runs at @Priority 20 in the same tick.
**Sources:** MergeDetectionPhase.java, knowledge consolidation pipeline spec §7
**Exploration:** quick
**Status:** captured

## D5: @Priority(15) — between AccessFrequency and MergeDetection

**Choice:** ExperienceConsolidationPhase runs at @Priority(15), after AccessFrequencyPhase (10) and before MergeDetectionPhase (20).
**Alternatives:**
- @Priority(12) — immediately after AccessFrequency. Same effect, tighter coupling.
- @Priority(35) — after CommunitySummary. New nodes don't participate in merge detection or summaries until the next tick.
**Rationale:** New cognitive nodes should be available for merge detection (@Priority 20), schema discovery (@Priority 25), and community summaries (@Priority 30) in the same consolidation tick.
**Trade-offs:** None significant — the phase ordering is natural.
**Sources:** ConsolidationScheduler.java phase ordering, SchemaDiscoveryPhase.java (@Priority 25)
**Exploration:** quick
**Status:** captured

## D6: Provenance via source-memory-id property

**Choice:** Store `source-memory-id` as a node property on graduated cognitive nodes. Follows the provenance property pattern used by ConversationBridge (`provenance: "conversation-bridge"`) and MindMapExtractor.
**Alternatives:**
- Edge to agent node — richer graph structure but doesn't link to the specific memory
- Both property and edge — maximum information but increases graph density
- No provenance — simplest but no audit trail
**Rationale:** Lightweight, queryable, consistent with existing provenance conventions. Does not add edge density to the graph.
**Trade-offs:** No graph traversal from the cognitive node to its source — requires a memory store query by ID.
**Sources:** ConversationBridge.java:58 (provenance property), MindMapExtractor.java (provenance pattern)
**Exploration:** quick
**Status:** captured

## D7: Max 20 graduated nodes per pass

**Choice:** Configurable via `casehub.consolidation.graduation.max-per-pass`, default 20.
**Alternatives:**
- Max 10 — conservative, same as MergeDetection. Multiple ticks to clear backlog.
- No cap — graduate everything above threshold. Could create large bursts.
**Rationale:** 20 is generous for agent-scale workloads without overwhelming the graph. Higher than MergeDetection (10) because graduation is cheaper (no pairwise comparison). Configurable for tuning.
**Trade-offs:** High-activity periods may need multiple ticks to process backlog.
**Sources:** CommunitySummaryPhase max-per-pass=5, MergeDetectionPhase max-per-pass=10
**Exploration:** quick
**Status:** captured

## D8: Pipeline orchestrator with two SPIs

**Choice:** ExperienceConsolidationPhase is a thin pipeline orchestrator: query → score (GraduationScorer SPI) → filter by threshold → classify (GraduationClassifier SPI) → create nodes → advance watermark.
**Alternatives:**
- Single SPI combining score + classify — simpler interface but couples independent concerns. Can't swap scorer without reimplementing classification.
- No SPI, all inline — simplest but no extension point for blocks/wacky-manor
**Rationale:** Two independent SPIs give independent extensibility. Blocks can provide a rich scorer (surprise/arousal/confidence composite) without touching classification, and vice versa. Matches the neocortex pattern exactly.
**Trade-offs:** Two SPIs is slightly more surface area than one.
**Sources:** ReflectionSynthesizer (SPI pattern), PlanAdapter (SPI pattern), QueryExpander (SPI pattern)
**Exploration:** quick
**Status:** captured
