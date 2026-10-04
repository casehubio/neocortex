# Knowledge Pipeline — Decisions

## D1: Cache engine — SQLite with query subsumption

**Choice:** SQLite as the unified cache engine, using R*Tree for the spatial subset. No external infrastructure. Query result memoization with pluggable subsumption rules.
**Alternatives:**
- Tile38 — purpose-built geospatial DB (MIT, Redis RESP). Excellent for NEARBY/WITHIN/INTERSECTS and real-time geofencing. However: the cache is fundamentally query-result memoization, not a spatial database. Most cache hits come from query subsumption ("I already have a broader result set"), not spatial queries over entities. Non-spatial queries (text search, product search, travel routes) get no benefit from Tile38. Adds infrastructure for a capability that serves a subset of query types. Geofencing is real value but deferred to a future phase — evaluate Tile38 (or equivalent) as a dedicated geofencing service when that's in scope, not as a cache engine.
- Qdrant — wrong workload (vector search, not query memoization)
- PostGIS — violates process-local storage pattern
- Redis — adds infrastructure for less specialized tool; license concerns
- H2GIS — introduces second database engine
**Rationale:** The pipeline cache is query-result memoization with subsumption. "Find Italian restaurants within 1km" is subsumed by a cached "restaurants within 5km" at the same center — filter the cached results, no re-fetch needed. Subsumption is per-domain logic (spatial: geometric circle containment; product: category hierarchy + alias normalization), not a spatial index feature. SQLite handles both the cache store and R*Tree spatial index for the "what's near me?" subset. Zero new infrastructure — same operational pattern as SqliteMindMapStore, SqliteCbrRetrievalTracker, and every other SQLite-backed store in neocortex. Scale (hundreds to low thousands per tenant) is well within SQLite's envelope.
**Trade-offs:** No real-time geofencing. Mitigated: geofencing deferred, can add Tile38 as dedicated service when needed. R*Tree spatial queries less elegant than Tile38 NEARBY but functionally equivalent at this scale.
**Sources:** tile38.com (evaluated), connectors design spec §5, SQLite R*Tree docs
**Exploration:** deep-analysis → revised (post-implementation insight: cache is query memoization with subsumption, not a spatial database)
**Status:** revised

## D2: Query normalization and cache key design

**Choice:** Structured, backend-agnostic query records as the normalized form. The pipeline orchestrator determines query type via structured dispatch from LocationPlatform SPI methods (searchByText → TEXT_SEARCH, searchNearby → NEARBY, searchByCategory → CATEGORY_SEARCH) with LLM fallback only for ambiguous natural-language queries. Cache keys derive from the structured query record via deterministic serialization. Spatial rounding (geohash precision 6, ~1.2km cells) applied to coordinates before cache key generation ensures semantically equivalent nearby queries hit the cache despite minor coordinate differences.
**Alternatives:**
- Raw string cache keys — terrible hit rate, semantically equivalent queries miss each other
- Rule-based pattern matching only — faster but brittle, can't handle natural language variation
- Tile38 command syntax as cache key — couples cache key format to the Tile38 implementation, contradicting D3's backend independence goal
- LLM normalization as primary path — unnecessary latency for structured queries that already have a determined type
**Rationale:** LocationPlatform SPI methods already define the query type taxonomy. When the pipeline orchestrator knows the query type from conversation-level intent, a structured query record (type, center, radius, filters, spatialBucket) is deterministic — no LLM call needed. LLM normalization applies only when intent is ambiguous and cannot be mapped to a specific SPI method. Cache keys derive from the structured record, not backend command syntax, ensuring backend independence (D3). Spatial rounding via geohash prevents cache miss degradation for mobile users whose coordinates shift by meters between queries.
**Trade-offs:** Geohash precision 6 (~1.2km cells) means queries in different cells but physically close may miss the cache. Deliberate trade-off: cache correctness over hit rate. Cell size tunable via configuration.
**Depends on:** D3 (backend-agnostic SPI boundary)
**Exploration:** quick → revised (R1-03, R1-10: spatial rounding, structured dispatch, backend-agnostic key format)
**Status:** revised

## D3: Cache SPI boundary

**Choice:** Cache behind an SPI so implementations can change; Tile38 as first implementation
**Alternatives:**
- Direct Tile38 coupling — simpler but locks in the backing store
- No SPI, SQLite only — too narrow, misses spatial capabilities
**Rationale:** The cache implementation may need to change if Tile38 proves unsuitable — operational complexity, Java client gaps, or licensing changes are real risks from D1's infrastructure choice. The pipeline logic should not be rewritten when the backing store changes. The SPI boundary also enables in-memory stubs for testing without a running Tile38 instance.
**Trade-offs:** SPI abstraction adds a layer. Minimal cost given the established neocortex pattern (MindMapStore, CaseMemoryStore, CbrCaseMemoryStore all behind SPIs).
**Exploration:** quick → revised (R1-11: rationale strengthened from convention to risk-based justification)
**Status:** revised

## D4: Scope — connectors vs neocortex

**Choice:** This issue (#413) scopes to neocortex only. LocationPlatform SPI work already complete in connectors#133.
**Alternatives:**
- Include connectors work — unnecessary, already done
**Rationale:** connectors#133 is closed. LocationPlatform SPI (location-spi, location-ref, location-google) ships PlaceSearch, PlaceDetails, Geocoding, Directions capability sub-interfaces. The knowledge pipeline consumes this SPI.
**Trade-offs:** None — dependency is satisfied.
**Exploration:** quick
**Status:** captured

## D5: Query subsumption as first-class cache concept

**Choice:** Cache lookup uses pluggable SubsumptionRule SPI before falling back to provider fetch. Per-domain rules define what "broader than" means for each query type.
**Alternatives:**
- Exact cache key match only — misses opportunities: "Italian restaurants within 1km" is answerable from a cached "restaurants within 5km" at the same center. Cache hit rate would be poor without subsumption.
- LLM-based cache matching — could semantically match queries but adds latency and non-determinism to every cache lookup
**Rationale:** Most cache hits come from subsumption, not exact key match. A spatial search with a broader radius subsumes a narrower one (geometric containment). A search without category filter subsumes one with a filter (client-side filtering). For locations, subsumption is deterministic (circle containment + category narrowing). For products (Phase 3), subsumption requires category hierarchies and alias normalization — harder but same SPI shape. The SubsumptionRule SPI makes the cache engine domain-agnostic while each domain defines its own "broader than" semantics.
**Trade-offs:** Subsumption scanning adds O(n) over cached queries per tenant. Mitigated: cached query count is small (tens to hundreds), check is cheap (geometric math or string comparison).
**Depends on:** D1 (SQLite cache engine), D3 (cache behind SPI)
**Exploration:** deep-analysis (surfaced during implementation review of Tile38 cache assumptions)
**Status:** captured

## D6: Research lifecycle in scope

**Choice:** Research lifecycle orchestration is in scope for #413 — it's a key differentiator, not a follow-on
**Alternatives:**
- Defer to separate issue — pipeline works without it, but misses the core value proposition
**Rationale:** The system is a logistical problem solver, not a search cache. Multi-session research ("plan Edinburgh trip") that accumulates entities, comparisons, and decisions across sessions is what differentiates it from a normal chatbot. Research workflow state lives in a dedicated SQLite table (D9), with orchestration logic (transitions, cache TTL extension, session resume) in the pipeline module. Package-level separation within the module keeps research orchestration, cache management, and entity resolution independently testable.
**Trade-offs:** Increases issue scope. Mitigated by clean package-level separation within the pipeline module.
**Exploration:** quick
**Status:** revised

## D7: Identity resolution — blocking + matching + decision pipeline

**Choice:** Standard entity resolution pattern: Tile38 NEARBY for spatial blocking, domain-specific EntityMatcher SPI for matching, three-tier decision (definitive → auto-merge, high confidence → auto-merge with provenance, medium → MERGE_CANDIDATE signal via attention pipeline)
**Alternatives:**
- Embedding-based semantic name matching — adds embedding infrastructure dependency for a problem that domain-specific heuristics solve better (coordinates + phone + external IDs are stronger signals than name embeddings)
- MindMap MergeDetectionPhase for cache entities — operates on MindMap nodes, not cache entities; wrong lifecycle stage
- Custom verification UI — unnecessary when the attention pipeline already surfaces signals for agent judgment
**Rationale:** Entity resolution is a known pattern (blocking → matching → decision). Tile38 NEARBY provides the spatial blocking step. Domain-specific matchers (PlaceMatcher for Phase 2, ProductMatcher for Phase 3) behind an EntityMatcher SPI keep matching logic testable and swappable. The decision tier composes with existing attention infrastructure: medium-confidence candidates emit an AttentionSignal with category MERGE_CANDIDATE → CognitiveAttentionAccumulator aggregates with other signals → CognitiveAttentionRequired event fired when threshold crossed → CognitiveAttentionMediator queues AttentionBriefing → agent receives briefing in next conversation turn and decides whether to merge. Pipeline MERGE_CANDIDATE signals share SignalCategory with MergeDetectionPhase's graph-based merge candidates, but the AttentionSignal.reason field distinguishes evidence type ("spatial proximity + matching external ID" vs "name similarity + neighbor overlap").
**Trade-offs:** Domain-specific matchers require per-domain implementation. Mitigated by the bounded set of connector SPIs — one matcher per SPI.
**Sources:** MergeDetectionPhase, SignalCategory.MERGE_CANDIDATE, CognitiveAttentionAccumulator, CognitiveAttentionMediator (mindmap-intelligence, cognition)
**Exploration:** deep-analysis → revised (R1-07: corrected CuriosityDrive reference, clarified attention signal path, documented signal conflation mitigation)
**Status:** revised

## D8: Deduplication index — SQLite as primary lookup, NodeRef as provenance

**Choice:** SQLite key-value store as the primary deduplication lookup for ALL entities: `{source, externalId} → {cacheEntityId, mindMapNodeId?}`. NodeRef on promoted MindMap nodes records provenance (scheme="google-places", id=<placeId>) as metadata, not a query mechanism — MindMapQuery does not support NodeRef-based filtering.
**Alternatives:**
- Tile38-only dedup index — Tile38 optimized for spatial queries, not key-value lookups; identity records need durability across cache eviction
- NodeRef-based MindMap lookup for promoted entities — MindMapQuery has no withNodeRef() filter. Answering "have we seen Google Places ID xyz123?" via MindMapQuery would require scanning all PLACE nodes and filtering client-side by refs() — O(n) and unacceptable.
- Adding withNodeRef() to MindMapQuery — requires MindMap SPI changes and backend index support. Future enhancement if multiple consumers need NodeRef-based queries, but the dedup index solves the immediate need.
**Rationale:** The SQLite dedup index is the single lookup path for both promoted and non-promoted entities. For non-promoted: `{source, externalId} → {cacheEntityId}`. For promoted: `{source, externalId} → {cacheEntityId, mindMapNodeId}`. NodeRef on the MindMap node is a provenance marker useful for data lineage, not for "have we seen this before?" queries.
**Depends on:** D1 (Tile38 as cache — identity records survive cache eviction)
**Exploration:** deep-analysis → revised (R1-06: clarified NodeRef role as provenance, SQLite as primary lookup for all entities)
**Status:** revised

## D9: Research workflow state in SQLite, not MindMap

**Choice:** Research session workflow state (status, criteria, constraints, timeline, lastActive) stored in a SQLite `research_sessions` table in the pipeline module. Foreign key to MindMap subgraph ID links operational state to the knowledge graph. Orchestration logic (transitions, cache TTL extension, session resume) in the pipeline module.
**Alternatives:**
- Properties on RESEARCH_AREA subgraph node — conflates structural knowledge ("what the agent knows about Edinburgh") with operational state ("is this research session active?"). MindMap's Map<String, String> properties require serialization for structured criteria. MindMapQuery cannot efficiently filter by status or lastActive — querying active sessions requires scanning all RESEARCH_AREA subgraphs and filtering client-side.
- Tile38 metadata on session objects — spatial cache is ephemeral, research state needs durability
- CaseMemoryStore memory records — wrong abstraction; memory is for experiences, not workflow state
**Rationale:** Research entities (shortlisted places, hotels) belong in MindMap as nodes within the RESEARCH_AREA subgraph — that's structural knowledge. Workflow metadata (status, criteria, lastActive) is operational state ABOUT the subgraph. Separating these follows the CbrRetrievalTracker pattern: tracking metadata lives in SQLite alongside the primary store, not inside it. The `research_sessions` table can have indexed columns for efficient queries (`WHERE status = 'active' AND last_active > ?`) that MindMap's property model cannot support.
**Depends on:** D6 (research lifecycle in scope)
**Exploration:** quick → revised (R1-05: moved from MindMap properties to SQLite, following CbrRetrievalTracker precedent)
**Status:** revised

## D10: Entity promotion — cache to MindMap

**Choice:** Cached entities are promoted to MindMap when the user explicitly saves/bookmarks them during a research session. Two paths: (1) **New entity** — no existing MindMap node found by `resolveNode(name, null, tenantId)` → created as a PLACE subgraph node. (2) **Merge with existing** — `resolveNode` finds a node already created by MindMapExtractor (e.g., in a "restaurant" subgraph from conversation extraction) → enriched in place via D11, subgraph unchanged. In both cases: core properties transferred (name, coordinates, rating, category, address), NodeRef provenance added (scheme=source, id=externalId), SQLite dedup index updated with MindMap node ID. PLACE subgraphs are per-tenant, not per-research-session. Promoted entity discovery is via the dedup index (D8: `WHERE mindMapNodeId IS NOT NULL`), not by scanning PLACE subgraphs — this is correct because merged entities may live in any subgraph.
**Alternatives:**
- Auto-promote all cached entities — floods MindMap with ephemeral search results, degrading signal-to-noise
- Per-research-session PLACE subgraphs — isolates promoted places, preventing cross-session discovery
- Promotion by confidence threshold — implicit promotion without user intent; hard to calibrate
- Move existing node to PLACE on merge — requires a new MindMap SPI method (NodeUpdate has no subgraphId). Semantically wrong: a "restaurant" extracted from conversation IS a restaurant with richer data after enrichment, not a generic "place." Moving it loses type information.
- Cross-subgraph edge to PLACE — adds structural complexity for what is an index concern. The dedup index already provides "find all promoted entities" without graph edges.
**Rationale:** Promotion is a user-intent signal. The cache holds everything the pipeline retrieved; MindMap holds what the user chose to keep. The two-path design follows the same pattern as MindMapExtractor.applyExtraction() (lines 309-318): when resolveNode finds an existing node, enrich it via updateNode() without changing its subgraph; when no match exists, create a new node. Preserving the existing subgraph respects the fine-grained type taxonomy that MindMapExtractor established — a restaurant enriched with Google Places data is still a restaurant.
**Depends on:** D8 (dedup index tracks promoted status), D11 (cross-system merge behavior)
**Exploration:** surfaced by review (R1-13) → revised (R2-01: qualified PLACE subgraph to new-entity path only; merged entities preserve existing subgraph)
**Status:** revised

## D11: Cross-system entity resolution — MindMapExtractor vs pipeline

**Choice:** When the pipeline promotes a cached entity to MindMap, it first checks `resolveNode(name, null, tenantId)` to detect if MindMapExtractor already created a node with the same name from conversation text. If found: enrich the existing node via `updateNode()` — add location properties (coordinates, rating, address), add NodeRef provenance (`refsToAdd`), update the dedup index with the existing node's ID. The existing node's subgraph is preserved (e.g., stays in "restaurant" subgraph, not moved to PLACE). If not found: create a new PLACE node (D10 new-entity path). This extends D7's entity resolution from pipeline-internal to cross-system at the promotion boundary.
**Alternatives:**
- Ignore cross-system duplicates — MindMapExtractor and pipeline create separate nodes for the same entity
- Defer to MergeDetectionPhase — graph-based merge detection would eventually detect the duplicate, but with delay and without the pipeline's domain-specific signals (external IDs, coordinates)
- NodeRef-based cross-system lookup — requires MindMapQuery to support NodeRef filtering (not available today)
**Rationale:** MindMapExtractor already uses resolveNode() to deduplicate during extraction (verified: MindMapExtractor.applyExtraction lines 307-318 calls `store.resolveNode(pe.name(), null, tenantId)`, then enriches existing nodes via `updateNode()` without changing subgraph). The pipeline follows the same pattern at promotion time. Subgraph preservation is correct: `NodeUpdate` has no `subgraphId` field (deliberate SPI design — nodes are not moved between subgraphs), and the entity's type-based subgraph (assigned by MindMapExtractor from the LLM's fine-grained type taxonomy) is more specific than the pipeline's generic PLACE type. Name-based resolution handles the common case; edge cases (different names for the same entity) are resolved by MergeDetectionPhase asynchronously.
**Depends on:** D7 (entity resolution), D10 (entity promotion)
**Exploration:** surfaced by review (R1-14) → revised (R2-01: specified subgraph preservation on merge, documented updateNode constraints)
**Status:** revised

## D12: Geofencing deferred — evaluate Tile38 when in scope

**Choice:** Geofencing ("alert me when I'm near a saved restaurant") is deferred to a future phase. When geofencing becomes a requirement, evaluate Tile38 as a dedicated geofencing service — separate from the cache engine.
**Alternatives:**
- Include Tile38 now for geofencing readiness — adds infrastructure for a speculative use case (YAGNI)
- Build custom geofencing on SQLite — wrong tool, geofencing needs push-model event delivery
**Rationale:** Tile38's unique value is real-time geofencing via webhooks/pub-sub. This is genuinely useful for passive check-ins ("you're near Ondine — you were here in March"). But geofencing is a push-model service (watches object movement, fires events), not a cache feature. When it's in scope, Tile38 should be evaluated as a standalone geofencing service alongside the SQLite cache, not replacing it.
**Trade-offs:** No passive check-in capability in Phase 2. Explicit check-ins via CheckInService (Phase 1) remain the only path.
**Depends on:** D1 (Tile38 removed from cache role)
**Exploration:** quick
**Status:** captured
