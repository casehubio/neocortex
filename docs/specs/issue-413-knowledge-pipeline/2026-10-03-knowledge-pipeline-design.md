# Knowledge Pipeline — Design Specification

## 0. Tracking

**Epic:** casehubio/neocortex#413
**Phase:** 2 of the Real-World Knowledge Platform
**Dependencies:** Phase 1 complete (#412 — ACTIVITY/PLACE types, traits, CheckInService, ActivityQueryService), LocationPlatform SPI complete (connectors#133)
**Decisions:** [decisions.md](decisions.md) — 12 decisions (D1–D12), validated via adversarial decision review

## 1. Problem Statement

Phase 1 validated the MindMap graph model for activities, places, and CRM queries using single-source user input. Phase 2 adds external data sources: the system fetches structured data from location providers (Google Places, TripAdvisor, yell.com), caches results with TTL-based eviction, deduplicates across sources, resolves entity identity, and orchestrates multi-session research projects.

The knowledge pipeline sits between connector SPIs (pure fetch) and MindMap (durable knowledge). It owns all post-fetch processing that connectors must not do (D10 from the original design: connectors stay pure-fetch, no persistence or aggregation).

### 1.1 What This Phase Delivers

1. **Query cache with subsumption** — SQLite-backed cache for search results with query subsumption (D5), R*Tree spatial index for nearby/within queries, field-type TTLs, and structured query normalization
2. **Identity resolution** — cross-source entity matching using spatial blocking + domain-specific heuristics, composing with the existing attention pipeline for uncertain matches
3. **Research lifecycle** — multi-session investigation orchestration over MindMap RESEARCH_AREA subgraphs, the key differentiator from single-query search
4. **Entity promotion** — user-triggered transfer from ephemeral cache to durable MindMap knowledge, with cross-system deduplication against MindMapExtractor-created nodes
5. **Cache eviction and decay** — field-type-aware TTLs, deduplication index persistence across cache eviction, research session TTL extension

### 1.2 Deferred

- **LLM extraction from unstructured sources** (blogs, web pages) — listed in epic #413 scope but requires separate design: HTML content extraction, structured entity recognition from free-text reviews, LLM prompt engineering for Place/Activity data extraction. Distinct from the conversation-to-knowledge extraction in #295. Tracked as neocortex#414.

## 2. Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Consumers (LLM agents, research orchestration)             │
│  — natural language queries, research session management    │
└────────────┬────────────────────────────┬───────────────────┘
             │                            │
             ▼                            ▼
┌─────────────────────────────┐  ┌────────────────────────────┐
│  Knowledge Pipeline         │  │  MindMap (existing)         │
│  (neocortex — new modules)  │  │  — durable knowledge graph  │
│                             │  │  — PLACE, ACTIVITY nodes    │
│  ┌───────────────────────┐  │  │  — RESEARCH_AREA subgraphs  │
│  │ Query Normalizer      │  │  │  — CheckInService, CRM      │
│  │ (structured dispatch  │  │  └────────────────────────────┘
│  │  + LLM fallback)      │  │           ▲
│  └───────────┬───────────┘  │           │ promotion
│              ▼              │           │
│  ┌───────────────────────┐  │  ┌────────┴───────────────────┐
│  │ Entity Cache (SQLite)  │──┼──│ Entity Promoter            │
│  │ R*Tree spatial index  │  │  │ (resolveNode + create/     │
│  │ TTL, subsumption      │  │  │  enrich + dedup update)    │
│  └───────────┬───────────┘  │  └────────────────────────────┘
│              │              │
│  ┌───────────┴───────────┐  │
│  │ Entity Resolver       │  │
│  │ blocking (NEARBY) +   │  │
│  │ matching (PlaceMatcher)│  │
│  │ + decision (3-tier)   │  │
│  └───────────────────────┘  │
│                             │
│  ┌───────────────────────┐  │
│  │ Dedup Index (SQLite)  │  │
│  │ {source,extId} →      │  │
│  │ {cacheId, nodeId?}    │  │
│  └───────────────────────┘  │
│                             │
│  ┌───────────────────────┐  │
│  │ Research Orchestrator  │  │
│  │ SQLite session state   │  │
│  │ + MindMap subgraph ref │  │
│  └───────────────────────┘  │
└────────────┬────────────────┘
             │ fetches via SPI
             ▼
┌─────────────────────────────────────────────────────────────┐
│  Source Connectors (connectors repo — existing)             │
│  LocationPlatform: PlaceSearch, PlaceDetails, Geocoding,    │
│  Directions — location-spi, location-ref, location-google   │
└─────────────────────────────────────────────────────────────┘
```

### 2.1 Dependency Direction

```
connectors (location-spi)  ←  knowledge-pipeline  →  mindmap-api
                                      │
                                      ↓
                                  sqlite-support
```

The knowledge pipeline depends on:
- `location-spi` (connectors) — for fetch via LocationPlatform SPI
- `mindmap-api` (neocortex) — for promotion, cross-system resolution, AttentionSignal, SignalCategory
- `mindmap-intelligence` (neocortex) — for CognitiveAttentionAccumulator (signal emission), JaroWinkler (name matching)
- `sqlite-support` (neocortex) — for dedup index and research session state

Connectors have no dependency on neocortex. MindMap has no dependency on the pipeline.

## 3. Module Structure

```
knowledge-pipeline-api/     — SPIs and value types (zero CDI)
knowledge-pipeline/         — CDI wiring, orchestration, SQLite spatial cache,
                              query subsumption, entity resolution, research
                              lifecycle, cache eviction, dedup index, promotion
```

### 3.1 knowledge-pipeline-api

Pure Java module, zero CDI dependencies. Contains:

**SPIs:**
- `KnowledgePipelineService` — entry point: `search(KnowledgeQuery, tenantId)`, `promote(PromotionRequest)`, `refreshStale(tenantId)`
- `SpatialCacheStore` — cache SPI: `set(CachedEntity)`, `get(entityId)`, `nearby(Coordinates, radiusMeters, filters)`, `within(BoundingBox, filters)`, `remove(entityId)`, `expire(entityId, Instant)`
- `EntityMatcher<T>` — `@FunctionalInterface`: `MatchResult match(T candidate, T existing)`
- `QueryNormalizer` — `@FunctionalInterface`: `NormalizedQuery normalize(String naturalLanguage, QueryContext)`
- `ResearchSessionService` — `create(name, criteria, tenantId)`, `pause(sessionId)`, `resume(sessionId)`, `complete(sessionId)`, `listActive(tenantId)`

**Value types:**
- `KnowledgeQuery` — sealed: `TextSearch(query)`, `NearbySearch(center, radiusMeters, filters)`, `CategorySearch(category, center, radiusMeters)`
- `NormalizedQuery` — query type + parameters + cache key (deterministic serialization with geohash spatial rounding)
- `CachedEntity` — id, name, coordinates, properties, source, externalId, fetchedAt, expiresAt, sessionIds (Set — many-to-many via entity_sessions), hasDetail
- `MatchResult` — confidence (0.0–1.0), matchedSignals (list of signal descriptions), tier (DEFINITIVE/HIGH/MEDIUM/LOW)
- `PromotionRequest` — cacheEntityId, tenantId, researchSessionId? (optional — when present, creates cross-subgraph edge linking promoted PLACE node to RESEARCH_AREA subgraph)
- `PromotionResult` — mindMapNodeId, created (boolean — new vs enriched existing)
- `ResearchState` — enum: ACTIVE, PAUSED, COMPLETED
- `ResearchSession` — id, name, criteria (structured JSON), mindMapSubgraphId, state, createdAt, lastActive
- `CacheFilter` — category, priceLevel, minRating, maxRadius
- `SpatialBucket` — geohash precision 6 (~1.2km cells) for cache key normalization

**Maven coordinates:**
- groupId: `io.casehub`
- artifactId: `casehub-neocortex-knowledge-pipeline-api`
- Root Java package: `io.casehub.neocortex.knowledge`

### 3.2 knowledge-pipeline

CDI-wired runtime module. Contains:

**Orchestration:**
- `KnowledgePipelineOrchestrator` — `@ApplicationScoped`, implements `KnowledgePipelineService`. Injects `Instance<LocationPlatform>` for multi-provider discovery. Coordinates: query normalization → subsumption check → cache check → fetch from all available providers (iterate `Instance<LocationPlatform>`, sequential per provider, per-provider failure isolation: log + skip) → entity resolution across aggregated results → cache store → return results.

**Query subsumption (D5):**
- `SubsumptionRule` — `@FunctionalInterface` SPI: `Optional<List<CachedEntity>> subsume(NormalizedQuery query, QueryCacheStore queryCache, SpatialCacheStore entityCache, String tenantId)`. Returns non-empty if a cached broader query subsumes the incoming query (filter the broader result set client-side). Per-domain implementations define "broader than" semantics.
- `SpatialSubsumptionRule` — spatial subsumption: a cached `NearbySearch` or `CategorySearch` at the same geohash cell with a larger radius subsumes one with a smaller radius. Category filter narrowing: a cached result without category filter subsumes one with a filter (client-side category filtering). Geometric containment: if the queried circle is contained within the cached circle, the cached result subsumes.
- Subsumption scanning is O(n) over cached queries per tenant. Mitigated: cached query count is small (tens to hundreds), check is cheap (geometric math or string comparison).

**SQLite entity cache:**
- `SqliteSpatialCacheStore` — `@ApplicationScoped`, implements `SpatialCacheStore`. Unified SQLite store for entity data and spatial queries. Uses R*Tree virtual table for spatial indexing:
  - `set()` → INSERT/REPLACE into `entity_metadata` + INSERT into R*Tree `entity_spatial` (minLat, maxLat, minLng, maxLng = point coordinates)
  - `nearby()` → R*Tree range query (bounding box from center+radius) + Haversine post-filter for exact distance + category filter from metadata
  - `within()` → R*Tree range query (bounding box coordinates)
  - `remove()` → DELETE from both `entity_metadata` and `entity_spatial`
  - `expire()` → UPDATE `entity_metadata` SET `expires_at` = ?
- **Tenant isolation:** tenant_id column on all tables. Indexed for efficient per-tenant queries.
- **TTL management:** `expires_at` column on `entity_metadata`. `CacheEvictionScheduler` is the sole eviction mechanism — scans for expired entities via SQL WHERE clause.
- `InMemorySpatialCacheStore` test stub stores everything in-memory with Haversine filtering.

**Query normalization:**
- `StructuredQueryNormalizer` — `@ApplicationScoped`, implements `QueryNormalizer`. Maps LocationPlatform SPI methods to `KnowledgeQuery` subtypes via structured dispatch. LLM fallback (via platform AgentProvider) only for ambiguous natural language that cannot be mapped to a specific SPI method.
- `CacheKeyGenerator` — static utility: `NormalizedQuery → String`. Deterministic serialization with geohash precision 6 spatial rounding for coordinates. Ensures semantically equivalent nearby queries hit the cache despite minor coordinate differences.

**Entity resolution:**
- `PlaceMatcher` — implements `EntityMatcher<CachedEntity>`. Domain-specific matching signals:
  - Same external ID (source + externalId) → DEFINITIVE (1.0)
  - Coordinates < 50m (Haversine) + Jaro-Winkler name > 0.85 → HIGH (0.9)
  - Phone match (normalized: strip country code, spaces, dashes) → HIGH (0.85)
  - Address match (normalized) + Jaro-Winkler name > 0.7 → MEDIUM (0.7)
  - Name-only Jaro-Winkler > 0.9 within 200m → MEDIUM (0.65)
- `EntityResolutionEngine` — `@ApplicationScoped`. Three phases:
  1. **Blocking:** `SpatialCacheStore.nearby()` to find spatial candidates within 200m (backed by SQLite R*Tree in production, Haversine in-memory in tests)
  2. **Matching:** `PlaceMatcher.match()` for each candidate pair
  3. **Decision:** DEFINITIVE/HIGH → auto-merge in cache. MEDIUM → emit `AttentionSignal(category=MERGE_CANDIDATE, reason="spatial proximity + matching signals")` via `CognitiveAttentionAccumulator`. LOW → separate entities.
- `JaroWinkler` — `PlaceMatcher` uses `JaroWinkler.similarity()` from `mindmap-intelligence`'s consolidation package (already `public final class` with `public static` method — no visibility change needed).
- `PhoneNormalizer`, `AddressNormalizer` — static utilities for signal preprocessing

**Deduplication index:**
- `DedupIndexStore` — `@ApplicationScoped`. SQLite via `SqliteDataSourceFactory`. Schema:
  ```sql
  CREATE TABLE dedup_index (
      source       TEXT NOT NULL,
      external_id  TEXT NOT NULL,
      cache_entity_id TEXT NOT NULL,
      mindmap_node_id TEXT,
      first_seen   TEXT NOT NULL,
      last_seen    TEXT NOT NULL,
      PRIMARY KEY (source, external_id)
  );
  ```
  Flyway migration in `classpath:db/knowledge-pipeline`.

**Entity metadata store:**
- `EntityMetadataStore` — `@ApplicationScoped`. SQLite via `SqliteDataSourceFactory`. Stores all entity data including coordinates (unified with spatial index — no split between spatial and non-spatial stores):
  ```sql
  CREATE TABLE entity_metadata (
      entity_id           TEXT PRIMARY KEY,
      tenant_id           TEXT NOT NULL,
      name                TEXT NOT NULL,
      category            TEXT,
      latitude            REAL,
      longitude           REAL,
      source              TEXT NOT NULL,
      external_id         TEXT NOT NULL,
      properties          TEXT,  -- JSON (address, phone, website, types, priceLevel, etc.)
      fetched_at          TEXT NOT NULL,       -- ISO-8601, Place-level fields
      detail_fetched_at   TEXT,                -- ISO-8601, PlaceDetail-level fields (null until fetched)
      has_detail          INTEGER NOT NULL DEFAULT 0,
      expires_at          TEXT NOT NULL        -- ISO-8601
  );
  CREATE INDEX idx_entity_tenant ON entity_metadata(tenant_id);

  -- R*Tree spatial index for nearby/within queries
  CREATE VIRTUAL TABLE entity_spatial USING rtree(
      id,              -- rowid alias
      min_lat, max_lat,
      min_lng, max_lng
  );
  -- entity_spatial.id maps to entity_metadata.rowid

  CREATE TABLE entity_sessions (
      entity_id  TEXT NOT NULL,
      session_id TEXT NOT NULL,
      PRIMARY KEY (entity_id, session_id)
  );
  CREATE INDEX idx_entity_session ON entity_sessions(session_id);
  ```
  Shares the knowledge-pipeline SQLite database with `dedup_index`. `entity_sessions` is a many-to-many junction table — an entity can belong to multiple concurrent research sessions (see §8.3). The R*Tree virtual table `entity_spatial` enables efficient NEARBY/WITHIN queries — candidates from R*Tree range scan are post-filtered with Haversine for exact distance.

**Query result cache:**
- `QueryCacheStore` — `@ApplicationScoped`. SQLite via `SqliteDataSourceFactory`. Records which queries have been answered, separating "has this query been satisfied?" from "are there spatial entities nearby?":
  ```sql
  CREATE TABLE query_cache (
      cache_key   TEXT PRIMARY KEY,
      tenant_id   TEXT NOT NULL,
      entity_ids  TEXT NOT NULL,    -- JSON array of cache entity IDs
      answered_at TEXT NOT NULL,    -- ISO-8601
      expires_at  TEXT NOT NULL     -- ISO-8601
  );
  CREATE INDEX idx_query_tenant ON query_cache(tenant_id);
  ```
  `entity_ids` stores the result set as a JSON array of cache entity IDs. On cache HIT: retrieve entity IDs from `query_cache` → `SpatialCacheStore.get(entityId)` for each → return. This is the primary retrieval path for all query types and is essential for `TextSearch` queries which have no spatial parameters (no center/radius to NEARBY with). For `CategorySearch`/`NearbySearch`, the stored entity IDs are more precise than re-running NEARBY + category filter.
  On cache MISS: fetch from providers → store in entity_metadata + entity_spatial → insert/update query_cache entry (including entity_ids) → return.

**Entity promotion:**
- `EntityPromoter` — `@ApplicationScoped`. Three-stage resolution (D10):
  1. **Dedup index lookup:** check `DedupIndexStore` for `{source, externalId}` → if `mindmap_node_id IS NOT NULL`, the entity was previously promoted — use that MindMap node directly (enrich via `updateNode()` with fresh data).
  2. **Typed resolve:** list subgraphs → filter by PLACE type → resolve with UUID. Follows the `ActivityQueryService.findPlace()` pattern:
     ```
     store.listSubgraphs(tenantId).stream()
         .filter(s -> SubgraphTypes.PLACE.equals(s.type()))
         .map(s -> store.resolveNode(name, s.id(), tenantId))
         .filter(n -> n != null)
         .findFirst().orElse(null)
     ```
     Note: `SubgraphTypes.PLACE = "place"` is a type constant, NOT a subgraph UUID. `resolveNode()` filters on `subgraph_id` (UUID column), so passing the type constant directly would match nothing. The stream pattern resolves type → UUID correctly. A tenant may have multiple PLACE-type subgraphs (from CheckInService, MindMapExtractor, prior promotions) — the stream checks all of them.
     If found: enrich via `updateNode()` (add coordinates, rating, address, NodeRef provenance). Existing subgraph preserved.
  3. **Create new:** no dedup hit and no PLACE-subgraph match. Use `ensureSubgraph(SubgraphTypes.PLACE, tenantId)` (same pattern as `CheckInService.ensureSubgraph()`) to get or create the PLACE subgraph, then `addNode(NodeInput.of(name, subgraphId), tenantId)`. NodeRef provenance added (scheme=source, id=externalId).
  4. All paths: update dedup index with MindMap node ID.
- **ensureSubgraph pattern extraction:** `CheckInService`, `MindMapExtractor`, `ExperienceConsolidationPhase`, and `ConversationBridge` all have private copies of the find-or-create-subgraph pattern. The pipeline would be a 5th copy. Implementation should extract a shared `SubgraphUtils.ensureSubgraph(MindMapStore, String type, String tenantId)` utility in `mindmap-intelligence` and consolidate the existing copies (see issue filed below).
- **No untyped resolve fallback:** `resolveNode(name, null, tenantId)` with `LIMIT 1` and no `ORDER BY` is non-deterministic across subgraph types. Cross-subgraph name collisions are handled asynchronously by `MergeDetectionPhase`.
- **Cache entity ID generation:** deterministic — `sha256(source + ":" + externalId)`, truncated to 32 hex chars. Multiple pipeline instances processing the same Place produce identical IDs. SQLite INSERT/REPLACE and dedup index INSERT are idempotent.

**Research lifecycle:**
- `ResearchSessionStore` — `@ApplicationScoped`. SQLite via `SqliteDataSourceFactory`. Schema:
  ```sql
  CREATE TABLE research_sessions (
      id           TEXT PRIMARY KEY,
      name         TEXT NOT NULL,
      tenant_id    TEXT NOT NULL,
      criteria     TEXT,          -- JSON
      subgraph_id  TEXT NOT NULL, -- MindMap RESEARCH_AREA subgraph
      state        TEXT NOT NULL DEFAULT 'ACTIVE',
      created_at   TEXT NOT NULL,
      last_active  TEXT NOT NULL
  );
  CREATE INDEX idx_sessions_tenant_state ON research_sessions(tenant_id, state);
  ```
- `ResearchOrchestrator` — `@ApplicationScoped`, implements `ResearchSessionService`. Creates RESEARCH_AREA subgraph with root node in MindMap, stores session state in SQLite. Manages state transitions:
  - `create()` → (1) `createSubgraph(new SubgraphInput(name, SubgraphTypes.RESEARCH_AREA, null), tenantId)` → `subgraphId`, (2) `addNode(NodeInput.of(name, subgraphId).withProvenance("knowledge-pipeline"), tenantId)` → `rootNodeId` — this root node represents the research project itself, (3) `updateSubgraph(subgraphId, rootNodeId, tenantId)` — sets the root node so `getSubgraph().rootNodeId()` returns a non-null anchor for cross-subgraph edges (§4.3 step 3), (4) SQLite INSERT with state=ACTIVE
  - `pause()` → state=PAUSED, freezes `expires_at` values (scheduler skips PAUSED entities — no extension, no eviction)
  - `resume()` → state=ACTIVE, updates lastActive, extends `expires_at` for session entities — new expires_at computed as `now + CacheDecayPolicy.ttlFor(entity)` based on the entity's populated fields (`has_detail` flag). There is no stored "original TTL" value; the TTL is recomputed from `CacheDecayPolicy` and the entity's current field population.
  - `complete()` → state=COMPLETED, `expires_at` extended by grace period (default 7 days, configurable via `casehub.knowledge.research.completed-grace-period=P7D`), then normal eviction applies
  - Active sessions: on each access (search, compare, promote), `expires_at` is reset for all session entities to `now + CacheDecayPolicy.ttlFor(entity)` (recomputed from populated fields, not a stored value)
  - **Bounds:** `casehub.knowledge.research.max-entity-age=P90D` — hard cap on entity retention regardless of session state. `casehub.knowledge.research.max-session-duration=P180D` — auto-complete sessions after 6 months. Promoted entities (mindmap_node_id IS NOT NULL) are durable in MindMap, so cache eviction is lossless for saved items.

**Cache eviction:**
- `CacheEvictionScheduler` — `@ApplicationScoped`, `ScheduledExecutorService` daemon thread. Sole eviction mechanism. Runs periodically (configurable, default 1 hour):
  0. Tenant discovery: `SELECT DISTINCT tenant_id FROM entity_metadata`
  1. For each tenant: `SELECT entity_id FROM entity_metadata WHERE tenant_id = ? AND expires_at < ?`
  2. For each expired entity:
     a. Check `entity_sessions` junction table — entity may belong to multiple sessions
     b. ANY ACTIVE or PAUSED session → skip
     c. All sessions COMPLETED (past grace period), or no sessions → DELETE from entity_metadata + entity_spatial + entity_sessions
  3. Enforce max-entity-age: remove entities where `firstSeen + maxEntityAge < now` regardless of session state
  4. Dedup index entries preserved — identity records survive eviction
  5. Auto-complete sessions past max-session-duration
- `CacheDecayPolicy` — configurable per data type:

  | Data type | Default TTL |
  |---|---|
  | Coordinates, address | 30 days |
  | Business hours | 7 days |
  | Prices, availability | 1 day |
  | Reviews, ratings | 3 days |
  | Images, photos | 14 days |
  | Search result sets | 24 hours |
  | Research session entities | Extended while session ACTIVE |

**CDI defaults:**
- `KnowledgePipelineDefaultBeans` — `@DefaultBean` producers for config records

**Maven coordinates:**
- groupId: `io.casehub`
- artifactId: `casehub-neocortex-knowledge-pipeline`
- Root Java package: `io.casehub.neocortex.knowledge`
- Sub-packages: `resolution`, `cache`, `research`, `promotion`, `dedup`

**Dependencies:**
- `casehub-neocortex-knowledge-pipeline-api`
- `casehub-neocortex-mindmap-api` — MindMapStore, AttentionSignal, SignalCategory, NodeInput, NodeUpdate, NodeRef, SubgraphTypes, CognitiveAttentionRequired
- `casehub-neocortex-mindmap-intelligence` — CognitiveAttentionAccumulator (signal emission), JaroWinkler (name matching)
- `casehub-neocortex-sqlite-support` — SqliteDataSourceFactory (dedup index, research sessions, entity metadata, spatial cache, query cache)
- `io.casehub:casehub-connectors-location-spi` — LocationPlatform, Place, PlaceDetail, PageRequest, Page

## 4. Data Flow

### 4.1 Search Flow

```
User: "Find Italian restaurants near King's Cross"

1. StructuredQueryNormalizer
   → CategorySearch(category="italian_restaurant",
                    center=Coordinates(51.5317, -0.1240),
                    radiusMeters=1000)

2. CacheKeyGenerator
   → "CATEGORY:italian_restaurant:gcpvj0:1000"
      (geohash precision 6 for spatial rounding)

3. Subsumption check + query cache check:
   a. SubsumptionRule.subsume(normalizedQuery, queryCache, entityCache, tenantId)
      → scans cached queries for a broader result that subsumes this query
      → HIT (subsumption): filter the broader result set client-side → return
   b. QueryCacheStore.lookup(cacheKey, tenantId)
      → cache_key = "CATEGORY:italian_restaurant:gcpvj0:1000"
      → HIT (exact match, not expired):
          Retrieve entity_ids from query_cache → SpatialCacheStore.get(entityId)
          for each → return (entities evicted since cache entry was recorded are
          silently omitted from results)
      → MISS (entry absent or expired): continue to step 4

4. For each LocationPlatform in Instance<LocationPlatform>:
   LocationPlatform.placeSearch(userId).searchByCategory("italian_restaurant",
       Coordinates(51.5317, -0.1240), 1000, PageRequest.first(20))
   → Page<Place> from provider
   → Fetch all pages (follow nextCursor while hasMore=true, max 3 pages / 60 results
     per provider, configurable via casehub.knowledge.fetch.max-pages=3)
   → Per-provider failures: log + skip, continue with remaining providers
   → Aggregate results across providers before entity resolution

5. EntityResolutionEngine
   a. For each new Place, check DedupIndexStore: {source, externalId}
      → known entity? Compare fetched data with cached data:
        - If data changed: update entity_metadata + DedupIndexStore last_seen
        - If unchanged: update DedupIndexStore last_seen only
        → return refreshed cache entity
      → unknown? Continue to blocking
   b. Blocking: SpatialCacheStore.nearby() for each new entity → spatial candidates
   c. Matching: PlaceMatcher.match(newEntity, candidate)
   d. Decision:
      DEFINITIVE/HIGH → merge: combine properties, keep best data per field
      MEDIUM → emit AttentionSignal(MERGE_CANDIDATE), store as separate entity
      LOW → store as separate entity

6. SpatialCacheStore.set(cachedEntity, tenantId) for each result
   → SQLite: INSERT/REPLACE entity_metadata
       (entity_id, tenant_id, name, category, latitude, longitude, source,
        external_id, properties JSON, fetched_at, has_detail, expires_at)
   → SQLite: INSERT INTO entity_spatial (R*Tree index for spatial queries)
   → DedupIndexStore: INSERT dedup_index for new entities
       (source, external_id, cache_entity_id, first_seen=now, last_seen=now)
       — entities already in dedup index were updated in step 5a, not here
   → If researchSessionId set: INSERT entity_sessions (entity_id, session_id)

7. QueryCacheStore.record(cacheKey, tenantId, entityIds, expiresAt)
   → INSERT/REPLACE query_cache entry with entity_ids JSON array

8. Return aggregated, deduplicated results to consumer
```

### 4.2 Promotion Flow

```
User: "Remember that second one — Ondine"

1. EntityPromoter.promote(PromotionRequest(cacheEntityId, tenantId, researchSessionId?))

2. Load CachedEntity from SpatialCacheStore

3. Three-stage MindMap resolution:
   a. Dedup index: DedupIndexStore.lookup(source, externalId)
      → mindmap_node_id IS NOT NULL? Use that node → enrich via updateNode()
   b. Typed resolve: listSubgraphs → filter PLACE type → resolveNode with UUID
      → For each PLACE-type subgraph: resolveNode("Ondine", subgraph.id(), tenantId)
      → FOUND → enrich via updateNode()
      → addRef(): NodeRef(scheme="google-places", id="ChIJ...")
   c. Create new: no match in dedup index or PLACE subgraph
      → ensureSubgraph(PLACE, tenantId) → get or create PLACE subgraph UUID
      → addNode(NodeInput.of("Ondine", subgraphId), tenantId)
      → Properties: name, lat, lng, address, category, rating, phone, website
      → NodeRef(scheme="google-places", id="ChIJ...")
      → Provenance: "knowledge-pipeline"
   (No untyped resolveNode(name, null, tenantId) — cross-subgraph name
    collisions handled asynchronously by MergeDetectionPhase)

4. Update DedupIndexStore: set mindmap_node_id

5. Return PromotionResult(nodeId, created=true/false)
```

### 4.3 Research Flow

```
User: "Plan Edinburgh trip"

1. ResearchOrchestrator.create("Edinburgh trip", criteria, tenantId)
   → Create RESEARCH_AREA subgraph "Edinburgh trip" in MindMap → subgraphId
   → Create root node: addNode(NodeInput.of("Edinburgh trip", subgraphId)
       .withProvenance("knowledge-pipeline"), tenantId) → rootNodeId
   → Set subgraph root: updateSubgraph(subgraphId, rootNodeId, tenantId)
   → Insert research_sessions row: state=ACTIVE, subgraph_id=<sgId>

User: "Find hotels near Old Town under £150/night"

2. Pipeline search (§4.1) with researchSessionId set
   → Results cached in entity_metadata + entity_spatial
   → entity_sessions row links each entity to this session (many-to-many)
   → TTL extended while session ACTIVE

User: "Save The Balmoral to my Edinburgh trip"

3. EntityPromoter.promote(balmoral_cache_id, tenantId, researchSessionId)
   → Create/enrich MindMap node in PLACE subgraph (per §9.1)
   → Load research session → subgraphId
   → researchAreaRootNodeId = getSubgraph(subgraphId, tenantId).rootNodeId()
     (non-null — set by ResearchOrchestrator.create() in step 1)
   → Create cross-subgraph edge: EdgeInput.of(promotedNodeId, researchAreaRootNodeId,
       "discovered-in").withProvenance("knowledge-pipeline")
     (same pattern as CheckInService: activities link to places via edges, not
      by sharing a subgraph — nodes belong to one subgraph only)

User: "Pause Edinburgh planning"

4. ResearchOrchestrator.pause(sessionId)
   → state=PAUSED in SQLite
   → Cache TTLs preserved (no extension, but not evicted early)

(Next week)
User: "Resume Edinburgh planning"

5. ResearchOrchestrator.resume(sessionId)
   → state=ACTIVE, lastActive updated
   → Cache TTLs extended again for session entities
```

## 5. Query Normalization

### 5.1 Structured Dispatch (Primary Path)

The query types are bounded by the LocationPlatform SPI methods. When the pipeline orchestrator knows the query type from conversation-level intent, normalization is deterministic — no LLM call:

| SPI Method | KnowledgeQuery Subtype | Cache Key Pattern |
|---|---|---|
| `searchByText(query, PageRequest)` | `TextSearch(query)` | `TEXT:<normalized_query>` |
| `searchNearby(location, radiusMeters, PageRequest)` | `NearbySearch(center, radius)` | `NEARBY:<geohash>:<radius>` |
| `searchByCategory(category, location, radiusMeters, PageRequest)` | `CategorySearch(category, center, radius)` | `CATEGORY:<category>:<geohash>:<radius>` |

Pagination: the pipeline fetches all pages from each provider (default max 3 pages / 60 results, configurable via `casehub.knowledge.fetch.max-pages`). Cache keys are page-independent — all pages for the same query contribute to the same cache entry. `PageRequest.first(20)` starts each provider fetch; subsequent pages follow `Page.nextCursor()` while `hasMore()`.

**Text normalization** for `TextSearch` cache keys: lowercase, collapse whitespace, trim, Unicode NFKC normalization. Example: `"Find Italian  Restaurants"` → `TEXT:find italian restaurants`.

### 5.2 LLM Fallback (Ambiguous Queries)

When the consumer passes a natural language string without explicit query type:

1. LLM maps to one of the three `KnowledgeQuery` subtypes
2. Extracts parameters: category, location (geocoded via `LocationPlatform.Geocoding`), radius
3. Returns `NormalizedQuery` with the structured form

### 5.3 Spatial Rounding

Coordinates are rounded to geohash precision 6 (~1.2km cells) before cache key generation. This ensures:
- "Italian restaurants near 51.5317, -0.1240" and "Italian restaurants near 51.5320, -0.1235" hit the same cache entry
- Mobile users whose GPS drifts by meters between queries get cache hits
- Trade-off: queries in adjacent cells but physically close (~100m apart) may miss the cache. Cell size tunable via `casehub.knowledge.cache.geohash-precision` (default 6).

## 6. Identity Resolution

### 6.1 EntityMatcher SPI

```java
@FunctionalInterface
public interface EntityMatcher<T> {
    MatchResult match(T candidate, T existing);
}
```

`MatchResult` carries:
- `confidence` — 0.0 to 1.0
- `matchedSignals` — list of signal descriptions for provenance
- `tier` — DEFINITIVE, HIGH, MEDIUM, LOW (derived from confidence thresholds)

### 6.2 PlaceMatcher

Domain-specific matching signals for places:

| Signal | Confidence | Condition |
|---|---|---|
| Same external ID | 1.0 (DEFINITIVE) | source + externalId match |
| Proximity + name | 0.9 (HIGH) | Haversine < 50m AND Jaro-Winkler > 0.85 |
| Phone match | 0.85 (HIGH) | Normalized phone numbers equal |
| Address + name | 0.7 (MEDIUM) | Normalized address match AND Jaro-Winkler > 0.7 |
| Name + proximity | 0.65 (MEDIUM) | Jaro-Winkler > 0.9 AND Haversine < 200m |

Multiple signals combine: the highest single-signal confidence is used (no weighted average — a definitive external ID match should not be diluted by a weak name match).

### 6.3 Resolution Engine

Three phases:

1. **Blocking:** For each new entity from a fetch, `SpatialCacheStore.nearby()` within 200m produces spatial candidates (backed by SQLite R*Tree in production, Haversine in-memory in tests). This avoids O(n²) pairwise comparison.

2. **Matching:** `PlaceMatcher.match(newEntity, candidate)` for each candidate pair.

3. **Decision:**
   - DEFINITIVE/HIGH → auto-merge in cache: combine properties, prefer most recent data per field, preserve all source provenance
   - MEDIUM → emit `AttentionSignal(category=MERGE_CANDIDATE, significance=confidence, reason="<matched signals>")`. Entities stored separately until human/agent confirms merge.
   - LOW → separate entities, no signal

### 6.4 Attention Pipeline Integration

**Wiring:** `EntityResolutionEngine` injects `Instance<CognitiveAttentionAccumulator>` (optional — `isResolvable()` check, matching the pattern in `ConsolidationScheduler`). When a MEDIUM confidence match is detected, the engine calls `addSignals()` directly — push model, not the pull model used by `ConsolidationPhase` implementations.

MERGE_CANDIDATE signals flow through the existing cognitive attention pipeline:
1. `EntityResolutionEngine` calls `accumulator.addSignals(List.of(new AttentionSignal(null, tenantId, SignalCategory.MERGE_CANDIDATE, cacheEntityId, name, confidence, reason)))` — `null` principalId for broadcast delivery, **cache entity ID as sourceNodeId** (not null). `CognitiveAttentionAccumulator.deduplicateAndAdd()` deduplicates by `(sourceNodeId, category)` — `Objects.equals(null, null)` is true, so null sourceNodeId would collapse all MERGE_CANDIDATE signals into one. Using the deterministic cache entity ID (`sha256(source + ":" + externalId)`) ensures each merge candidate is a distinct signal. The sourceNodeId doesn't need to be a MindMap node ID — `MergeDetectionPhase` also uses it as an opaque identifier.
2. `CognitiveAttentionAccumulator` broadcasts to all agents registered in `CognitiveDefaultsRegistry.allAgentIds()`
3. When threshold crossed → `CognitiveAttentionRequired` CDI event
4. `CognitiveAttentionMediator` queues `AttentionBriefing`
5. Agent receives briefing in next conversation turn and decides
6. `AttentionSignal.reason` distinguishes pipeline signals ("spatial proximity + matching external ID") from graph-based merge candidates ("name similarity + neighbor overlap")

## 7. Deduplication Index

SQLite key-value store via `sqlite-support`. Persists identity records across cache eviction.

### 7.1 Schema

```sql
CREATE TABLE dedup_index (
    source          TEXT NOT NULL,     -- "google-places", "tripadvisor", "yell"
    external_id     TEXT NOT NULL,     -- provider-specific ID
    cache_entity_id TEXT NOT NULL,     -- internal cache entity ID
    mindmap_node_id TEXT,              -- NULL until promoted
    first_seen      TEXT NOT NULL,     -- ISO-8601
    last_seen       TEXT NOT NULL,     -- ISO-8601
    PRIMARY KEY (source, external_id)
);
```

### 7.2 Lookup Paths

- **Before fetch:** check `{source, externalId}` → known entity? Compare fetched data with cached — if changed, update entity_metadata + `last_seen`. If unchanged, update `last_seen` only. This ensures the cache stays fresh when providers return updated data (new phone number, changed rating) without waiting for TTL expiry.
- **After promotion:** set `mindmap_node_id` → "this cache entity was saved to MindMap as node X"
- **On re-fetch:** entity already in dedup index → update cache entry with fresh data rather than creating duplicate
- **Promoted entity query:** `WHERE mindmap_node_id IS NOT NULL` → all entities ever promoted

### 7.3 NodeRef as Provenance

When promoting to MindMap, `NodeRef(scheme=<source>, id=<externalId>)` is added to the MindMap node. This records data lineage ("this node's coordinates came from Google Places ID ChIJ...") but is not used as a lookup mechanism — MindMapQuery has no `withNodeRef()` filter.

## 8. Research Lifecycle

### 8.1 Session State

SQLite `research_sessions` table (§3.2) with indexed `(tenant_id, state)` for efficient active session queries.

### 8.2 State Transitions

```
                    create()
                       │
                       ▼
                   ┌────────┐
          ┌───────│ ACTIVE  │───────┐
          │       └────────┘       │
     pause()          │        complete()
          │       resume()         │
          ▼           │            ▼
     ┌────────┐       │      ┌───────────┐
     │ PAUSED │───────┘      │ COMPLETED │
     └────────┘              └───────────┘
```

### 8.3 Cache TTL Extension

TTL management uses the `expires_at` column in `entity_metadata` (ISO-8601). The `CacheEvictionScheduler` is the sole eviction mechanism.

- **Multi-session membership:** entities can belong to multiple concurrent research sessions via the `entity_sessions` junction table. When an existing cached entity appears in a new search with a different session context, an `entity_sessions` row is added (not a field overwrite). Eviction checks ALL associated sessions — an entity is protected if ANY session is ACTIVE or PAUSED.
- **ACTIVE sessions:** on each session access (search, compare, promote), `expires_at` is updated for all session entities to `now + CacheDecayPolicy.ttlFor(entity)` via `SpatialCacheStore.expire()`. The TTL is recomputed from `CacheDecayPolicy` based on the entity's populated fields (`has_detail` flag) — there is no stored "original TTL" value. Subject to max-entity-age hard cap (default 90 days).
- **PAUSED sessions:** `expires_at` values are frozen (scheduler skips entities with ANY PAUSED session — no extension, no eviction).
- **COMPLETED sessions:** on transition to COMPLETED, `expires_at` extended by grace period (default 7 days, configurable). After grace period, entities with no remaining ACTIVE or PAUSED sessions become eligible for normal eviction. Promoted entities (mindmap_node_id IS NOT NULL) are durable in MindMap — cache eviction is lossless.
- **Bounds:** max-entity-age (default P90D) enforced regardless of session state. max-session-duration (default P180D) auto-completes long-running sessions.

### 8.4 Research Criteria

Criteria stored as structured JSON in the `criteria` column:

```json
{
  "maxPrice": 150,
  "currency": "GBP",
  "location": "Old Town, Edinburgh",
  "dateRange": {"from": "2026-12-20", "to": "2026-12-23"},
  "constraints": ["walking distance to castle", "breakfast included"]
}
```

Criteria can be used by the pipeline to filter search results and by the LLM to contextualize recommendations within the research session.

## 9. Entity Promotion

### 9.1 Three-Stage Resolution Design (D10)

**Stage 1 — Dedup index:** check `DedupIndexStore` for `{source, externalId}`. If `mindmap_node_id IS NOT NULL`, the entity was previously promoted — use that MindMap node. Enrich with fresh data via `updateNode()`.

**Stage 2 — Typed resolve:** list subgraphs → filter by `SubgraphTypes.PLACE` type → for each matching subgraph, `resolveNode(name, subgraph.id(), tenantId)`. Note: `SubgraphTypes.PLACE = "place"` is a type constant, not a subgraph UUID — the `resolveNode()` WHERE clause filters on the UUID `subgraph_id` column, so the type must be resolved to UUID(s) first via `listSubgraphs()`. Follows `ActivityQueryService.findPlace()` pattern exactly. If found: enrich via `updateNode()`, add/update coordinates, rating, address, phone. Add NodeRef via `refsToAdd` in NodeUpdate. Existing subgraph preserved.

**Stage 3 — Create new:** no dedup index hit and no PLACE-subgraph match.
- `ensureSubgraph(SubgraphTypes.PLACE, tenantId)` — list subgraphs, find existing PLACE-type subgraph or create one (same pattern as `CheckInService.ensureSubgraph()`)
- `addNode(NodeInput.of(name, subgraphId), tenantId)` — create node in the resolved subgraph
- Set core properties: name, lat, lng, address, category, rating, phone, website
- Add NodeRef provenance (scheme=source, id=externalId)
- Provenance marker: "knowledge-pipeline"

All stages: update dedup index `mindmap_node_id` field.

**Why no untyped resolve:** `resolveNode(name, null, tenantId)` uses `LIMIT 1` without `ORDER BY`, making results non-deterministic across subgraph types. A restaurant named "Ondine" could match a PERSON node created by MindMapExtractor, semantically corrupting it with restaurant coordinates and rating. Cross-subgraph name collisions belong in `MergeDetectionPhase` which handles them with proper context (§9.2).

### 9.2 Cross-System Resolution (D11)

MindMapExtractor creates nodes from conversation text ("we went to Ondine last week" → restaurant-typed node in PLACE subgraph, or PERSON subgraph node if the context is ambiguous). The pipeline may later fetch the same entity from Google Places. The three-stage resolution (§9.1) detects PLACE-subgraph overlap via typed resolve. This follows the resolve-before-create pattern from `MindMapExtractor.applyExtraction()` but scoped to PLACE subgraph for type safety.

Cross-subgraph name collisions (MindMapExtractor created a PERSON "Ondine" but the pipeline fetches restaurant "Ondine" from Google Places) result in a new PLACE node — both nodes coexist until `MergeDetectionPhase` surfaces the overlap asynchronously. Name variants ("Ondine" vs "Ondine Seafood Restaurant") are also handled by `MergeDetectionPhase`.

## 10. Cache Eviction and Decay

### 10.1 Field-Type TTLs

Search results return `Place` (id, name, formattedAddress, location, types, rating, userRatingsTotal, phoneNumber, website, priceLevel). `PlaceDetail` (adding openingHours, reviews, photos, url) is fetched separately via `PlaceDetails.get(placeId)` — lazily on first detail access or eagerly on promotion.

**Place-level TTLs** (available from search):

| Data type | Default TTL | Rationale |
|---|---|---|
| Coordinates, address | 30 days | Rarely change |
| Rating, userRatingsTotal | 3 days | Accumulate gradually |
| Phone, website, priceLevel | 7 days | Change infrequently |
| Search result sets | 24 hours | Query-specific, disposable |

**PlaceDetail-level TTLs** (fetched on demand):

| Data type | Default TTL | Rationale |
|---|---|---|
| Business hours (openingHours) | 7 days | Seasonal, holiday variations |
| Reviews | 3 days | Accumulate gradually |
| Photos | 14 days | Rarely change |

The entity-level `expiresAt` uses the **longest** TTL among its populated fields — the entity lives as long as any field is valid (up to 30 days for coordinates). Per-field staleness is checked at query time: is a specific field's TTL expired relative to `fetched_at` (or `detail_fetched_at` for PlaceDetail fields)? Stale fields trigger selective re-fetch per §10.3 while the entity itself remains in the cache with its stable fields intact. This avoids prematurely evicting an entity with 27 days of valid coordinate data just because its 3-day rating TTL expired.

`CachedEntity` tracks: `hasDetail: boolean` (whether PlaceDetail has been fetched). `entity_metadata` stores `fetched_at` (Place-level) and `detail_fetched_at` (PlaceDetail-level, null until detail fetched) for per-field staleness computation. PlaceDetail fetch cost is one API call per entity — not triggered during bulk search, only on individual entity access or promotion.

### 10.2 Eviction Scheduler

`CacheEvictionScheduler` runs periodically (default: every hour). Sole eviction mechanism.

0. **Tenant discovery:** `SELECT DISTINCT tenant_id FROM entity_metadata` — consistent with the pattern used by `CbrReconciliationService.discoverTenants()`.
1. For each tenant: `SELECT entity_id FROM entity_metadata WHERE tenant_id = ? AND expires_at < ?`
2. For each expired entity:
   a. Lookup `entity_sessions` — an entity may belong to multiple sessions
   b. Does this entity belong to ANY ACTIVE or PAUSED session? → skip
   c. All associated sessions are COMPLETED (past grace period) or entity has no sessions → `DELETE FROM entity_metadata` + `DELETE FROM entity_spatial` + `DELETE FROM entity_sessions`
3. Enforce max-entity-age hard cap (entities older than 90 days removed regardless of session)
4. Auto-complete sessions past max-session-duration (180 days default)
5. Dedup index entry preserved — identity record survives eviction

### 10.3 Staleness-Triggered Re-Fetch

When a user queries for a cached entity whose volatile fields (prices, hours) have expired but stable fields (coordinates, name) are still valid:

1. Return cached entity with stale marker on volatile fields
2. Trigger async re-fetch for volatile fields only
3. Update cache entry with fresh data
4. Consumer notified of refresh via CDI event

## 11. Configuration

All configuration via MicroProfile Config (`application.properties`):

```properties
# Cache key normalization
casehub.knowledge.cache.geohash-precision=6

# Eviction scheduler
casehub.knowledge.cache.eviction-interval=PT1H

# TTL defaults (ISO-8601 durations)
casehub.knowledge.cache.ttl.coordinates=P30D
casehub.knowledge.cache.ttl.hours=P7D
casehub.knowledge.cache.ttl.prices=P1D
casehub.knowledge.cache.ttl.reviews=P3D
casehub.knowledge.cache.ttl.images=P14D
casehub.knowledge.cache.ttl.search-results=P1D

# Entity resolution
casehub.knowledge.resolution.blocking-radius-meters=200
casehub.knowledge.resolution.auto-merge-threshold=0.8
casehub.knowledge.resolution.signal-threshold=0.6

# Provider fetch
casehub.knowledge.fetch.max-pages=3
casehub.knowledge.fetch.page-size=20

# Knowledge pipeline SQLite (dedup index, entity metadata, query cache, entity sessions)
casehub.knowledge.sqlite.path=data/knowledge-pipeline.db

# Research sessions SQLite
casehub.knowledge.research.sqlite.path=data/knowledge-research.db

# Research session bounds
casehub.knowledge.research.max-entity-age=P90D
casehub.knowledge.research.max-session-duration=P180D
casehub.knowledge.research.completed-grace-period=P7D
```

## 12. Testing Strategy

### 12.1 Contract Tests

- `SpatialCacheStoreContractTest` — abstract base for SpatialCacheStore implementations (set/get/nearby/within/remove/expire)
- `EntityMatcherContractTest` — abstract base for EntityMatcher implementations
- `ResearchSessionStoreContractTest` — abstract base for research session CRUD + state transitions

### 12.2 Unit Tests

- `PlaceMatcherTest` — matching signals: external ID, proximity + name, phone, address
- `CacheKeyGeneratorTest` — deterministic serialization, geohash rounding, equivalent query detection
- `EntityResolutionEngineTest` — blocking + matching + decision tiers
- `EntityPromoterTest` — new entity path, merge path, dedup index update
- `ResearchOrchestratorTest` — state transitions, TTL extension, session queries
- `CacheEvictionSchedulerTest` — TTL expiry, research session protection
- `PhoneNormalizerTest`, `AddressNormalizerTest` — signal preprocessing

### 12.3 Integration Tests

- `SqliteSpatialCacheStoreTest` — SQLite R*Tree: nearby/within queries, spatial index correctness, tenant isolation
- `KnowledgePipelineIntegrationTest` — end-to-end: search → subsumption → cache → resolve → promote, using location-ref (in-memory LocationPlatform) + InMemoryMindMapStore

### 12.4 In-Memory Stubs

- `InMemorySpatialCacheStore` — `@Alternative @Priority(2)` for unit tests without SQLite
- Reuse existing `InMemoryMindMapStore` for promotion tests

## 13. Infrastructure

### 13.1 SQLite

- All knowledge pipeline data shares one SQLite database: `dedup_index`, `entity_metadata`, `entity_spatial` (R*Tree), `entity_sessions`, `query_cache`. Research sessions use a separate database.
- Zero external infrastructure — no Docker, no services. Same process-local storage pattern as `SqliteMindMapStore`, `SqliteCbrRetrievalTracker`.
- WAL mode, HikariCP connection pooling, Flyway migrations
- R*Tree module enabled by default in the SQLite library bundled with `sqlite-jdbc`

## 14. Observability

`KnowledgePipelineMetrics` — `@ApplicationScoped`, Micrometer-based metrics:

| Metric | Type | Tags |
|---|---|---|
| `knowledge.cache.hits` | Counter | queryType, tenantId |
| `knowledge.cache.misses` | Counter | queryType, tenantId |
| `knowledge.resolution.decisions` | Counter | tier (DEFINITIVE/HIGH/MEDIUM/LOW), tenantId |
| `knowledge.provider.fetch.duration` | Timer | providerId, tenantId |
| `knowledge.provider.fetch.errors` | Counter | providerId, tenantId |
| `knowledge.eviction.runs` | Counter | tenantId |
| `knowledge.eviction.entities.removed` | Counter | tenantId |
| `knowledge.eviction.entities.skipped.session` | Counter | tenantId |
| `knowledge.research.sessions` | Gauge | state (ACTIVE/PAUSED/COMPLETED), tenantId |
| `knowledge.promotion.operations` | Counter | result (created/enriched), tenantId |

Logging: structured JSON via `java.util.logging` at INFO level for cache misses, provider failures, eviction runs, and promotion events. DEBUG level for resolution engine matching decisions.

## References

- [connectors design spec](../../../connectors/docs/specs/real-world-knowledge-platform/2026-10-03-real-world-knowledge-platform-design.md) — §5 Knowledge Pipeline, §6 Cache Eviction, §8 Identity Resolution, §9 Sub-Project Decomposition
- [decisions.md](decisions.md) — 12 validated decisions (D1–D12)
- [LocationPlatform SPI](../../../connectors/location-spi/) — PlaceSearch, PlaceDetails, Geocoding, Directions
- [Place record](../../../connectors/location-spi/src/main/java/io/casehub/connectors/location/model/Place.java) — id, name, formattedAddress, location, types, rating, phoneNumber, website, priceLevel
- [MindMapStore SPI](../../mindmap-api/src/main/java/io/casehub/neocortex/mindmap/MindMapStore.java) — resolveNode(), addNode(), updateNode(), merge()
- [MindMapExtractor](../../mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/MindMapExtractor.java) — parse() + apply() pattern, resolveNode-before-create
- [CheckInService](../../mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/CheckInService.java) — Phase 1 activity model
- [SignalCategory.MERGE_CANDIDATE](../../mindmap-api/src/main/java/io/casehub/neocortex/mindmap/SignalCategory.java) — existing attention signal category
- [CognitiveAttentionAccumulator](../../mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/CognitiveAttentionAccumulator.java) — signal aggregation and threshold
- [SqliteDataSourceFactory](../../sqlite-support/src/main/java/io/casehub/neocortex/sqlite/SqliteDataSourceFactory.java) — shared HikariCP + Flyway factory
- [JaroWinkler](../../mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/JaroWinkler.java) — string similarity (public API, used directly from mindmap-intelligence)
- [SQLite R*Tree](https://www.sqlite.org/rtree.html) — spatial index module for nearby/within queries
