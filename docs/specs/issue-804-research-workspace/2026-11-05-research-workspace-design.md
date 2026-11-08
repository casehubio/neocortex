# Research Workspace Design

**Issue:** casehubio/neocortex#804
**Date:** 2026-11-05
**Status:** Design

## Summary

The research workspace gives LLM agents disciplined research tools. Instead of ad-hoc browsing and context-window-limited note-taking, agents get a persistent workspace that tracks what they've found, what they've covered, what's stale, and how analysis connects to evidence.

This is casehub's opinion about how LLMs should do research, expressed as tool design and platform conventions. The tools make disciplined research the path of least resistance.

## Scope

**In scope:**
- `ResearchWorkspace` SPI — the four core operations (store, search, coverage, stale) plus promotion
- `Content` sealed hierarchy for heterogeneous content
- Coverage tracking via dynamic category sets
- Freshness policy per category with per-category staleness reporting
- Source reliability tiers
- Enrichment as entry linkage (derivation DAG) via `store()` with `derivedFrom`
- Workspace-to-corpus promotion SPI (`WorkspacePromotion`)
- Workspace-native session lifecycle (independent of `ResearchSession`)
- MindMap integration via existing EntityPromoter pattern
- Consistency model: SQLite source of truth, Qdrant as derived index
- Research conventions (platform guidance for LLM research discipline)

**Out of scope:**
- `ContentAcquisition` SPI — define when the Playwright/HTTP adapter is implemented (separate issue); designing an SPI without a consumer produces speculative API surface
- Site capture implementation (Playwright adapter) — separate issue
- Multi-agent concurrent access orchestration — v2
- Automatic CaseMemoryStore/CBR integration — deferred
- corpus-management-api L1 remediation (Content sealed hierarchy is defined here but will be shared when L1 is fixed)

## Architecture

### Module Placement

All new types live in the existing knowledge-pipeline modules:

```
knowledge-pipeline-api/          (SPI additions — remains Tier 1 pure Java, no rag-api dependency)
  io.casehub.neocortex.knowledge.workspace/
    ResearchWorkspace            — facade SPI (4 operations + promotion)
    WorkspaceDescriptor          — workspace identity + config (includes mindMapSubgraphId)
    WorkspaceEntry               — stored research finding
    WorkspaceInput               — store() input record (includes agentId)
    Content                      — sealed: TextContent, BinaryContent, StructuredContent
    CoverageReport               — per-category entry counts + uncovered
    FreshnessPolicy              — default + per-category Duration overrides
    StaleEntry                   — entry + which categories are stale
    ReliabilityTier              — AUTHORITATIVE / OFFICIAL / CURATED / COMMUNITY / UNKNOWN
    WorkspacePromotion           — workspace→corpus graduation SPI (uses String corpusName, not CorpusRef)
    WorkspacePromotionResult     — promoted/skipped entries

knowledge-pipeline/              (implementation additions — adds rag-api dependency)
  io.casehub.neocortex.knowledge.workspace/
    WorkspaceOrchestrator        — implements ResearchWorkspace
    WorkspaceCoverageTracker     — category-based coverage computation
    WorkspaceFreshnessChecker    — per-category staleness detection
    WorkspaceStore               — SQLite persistence for workspace entries + sessions
    NoOpWorkspacePromotion       — @DefaultBean stub
```

**Dependency change:** `knowledge-pipeline` (the runtime module, not the API) adds a compile dependency on `rag-api` to compose with `EmbeddingIngestor` and `CaseRetriever`. The `knowledge-pipeline-api` SPI module remains Tier 1 pure Java with only `casehub-connectors-api` as a dependency — no rag-api types appear in the SPI surface.

### Relationship to Existing Infrastructure

```
┌───────────────────────────────────────────┐
│        ResearchWorkspace SPI              │
│  store / search / coverage / stale        │
│  promote / reconcile                      │
└──────────┬────────────────────────────────┘
           │ delegates to
           ▼
┌──────────────────┐  ┌───────────────────┐  ┌──────────────┐
│ WorkspaceStore   │  │ EmbeddingIngestor │  │ MindMapStore │
│ (SQLite)         │  │ CaseRetriever     │  │ (promotion)  │
│                  │  │ (Qdrant vectors)  │  │              │
│ entry CRUD       │  │ semantic search   │  │ subgraph     │
│ session CRUD     │  │ chunk ingestion   │  │ nodes/edges  │
│ coverage queries │  │                   │  │              │
│ freshness checks │  │                   │  │              │
└──────────────────┘  └───────────────────┘  └──────────────┘
```

WorkspaceStore is a new SQLite-backed store (not CacheStore) because the entry shape differs significantly from CachedEntity: Content sealed hierarchy, derivedFrom DAG, coverage categories, reliability tier, freshness policy. CacheStore remains for its original purpose (spatial entity cache with TTL). The workspace composes with existing RAG infrastructure (EmbeddingIngestor for vector ingestion, CaseRetriever for semantic search) and MindMap (EntityPromoter for knowledge graph promotion).

### Relationship to Existing Knowledge Pipeline Infrastructure

The Phase 3 R&D spec (`2026-10-05-knowledge-pipeline-phase-3-rnd.md` §3) identifies `DedupIndexStore`, `EntityResolutionEngine`, and `CacheDecayPolicy` as building blocks for persistent research content. The research workspace intentionally does NOT compose with these, for specific reasons:

- **`CacheDecayPolicy`**: Designed for spatial cache field groups (coordinates TTL, rating TTL, contact TTL, hours TTL). Its `ttlFor(CachedEntity)` and `staleGroups(CachedEntity, Instant)` methods are CachedEntity-specific. Workspace freshness is per-category (e.g. "pricing data expires in 7 days, product features in 30 days") — a fundamentally different dimension. `WorkspaceFreshnessChecker` composes with `FreshnessPolicy`, which is the right abstraction for category-based freshness.

- **`EntityResolutionEngine`**: Merges entities across connector sources using name matching and subgraph resolution. Workspace entries are research findings, not entities — they don't have names, coordinates, or external IDs in the connector sense. Two workspace entries from the same source URL are not "the same entity" — they may be different snapshots or different extractions from the same page.

- **`DedupIndexStore`**: Keyed by `(source, externalId)` for connector-sourced entities. Workspace entries have `sourceUri` but no connector source/externalId pair. Source-URI-based dedup IS relevant (don't store the same page twice), but the mechanism is different: the LLM uses `search()` before `store()` to check for existing content — this is the natural research workflow. The `idx_ws_entry_source` index enables efficient source-URI lookup if a future version needs store-time dedup. Silent merging at the data layer (as `DedupIndexStore` does) is wrong here because the LLM should decide whether to update, replace, or keep both.

The workspace is the implementation of the "persistent RAG-indexed content" concept from Phase 3, but it's a purpose-built abstraction with its own lifecycle, not a composition of spatial-cache primitives designed for connector entities.

### Consistency Model

SQLite is the source of truth. Qdrant is a derived index.

- **Write order:** SQLite first, then Qdrant. If SQLite fails, no Qdrant write occurs.
- **Qdrant failure:** The entry is stored and trackable but not semantically searchable. The failure is logged.
- **Reconciliation (additive-only):** `reconcile(workspaceId, tenantId)` loads entry IDs with non-null `extractedText` from SQLite for this workspace, then re-ingests any whose `sourceDocumentId` is missing from Qdrant. It does **NOT** delete any Qdrant documents — this is critical because multiple workspaces share a single tenant-scoped Qdrant collection (`CorpusRef(tenantId, "research-workspace")`), and `EmbeddingIngestor.listDocuments()` has no workspace filter (it returns all documents for the tenant). Deleting "orphans" would destroy other workspaces' vectors.
- **Vector cleanup:** When a workspace is archived via `archiveWorkspace()`, its Qdrant vectors are cleaned up explicitly by calling `EmbeddingIngestor.deleteDocument(corpusRef, entryId)` for each of the workspace's entry IDs (known from SQLite). This is workspace-scoped and safe — it deletes by specific entry ID, not by orphan detection.
- **Entry deletion:** Remove from Qdrant first (best-effort via `deleteDocument(corpusRef, entryId)`), then SQLite. Failures are logged; orphaned Qdrant documents for deleted entries are harmless (they point to non-existent SQLite rows and will be filtered out during search resolution in step 6).

### Workspace and Session Lifecycle

```
Workspace (per-topic, persistent)
  ├─ Session 1 (COMPLETED)
  │   └─ findings from initial research
  ├─ Session 2 (COMPLETED)
  │   └─ follow-up after product launch
  └─ Session 3 (ACTIVE)
      └─ quarterly refresh

Workspace lifecycle: OPEN → ARCHIVED
Session lifecycle:   ACTIVE → PAUSED → COMPLETED
```

A workspace is a named research topic that persists across sessions. Workspace sessions are lightweight — just open/close markers tracked by `WorkspaceStore` — and are **independent of the existing `ResearchSession` / `ResearchSessionService`**. The existing `ResearchSession` always creates a MindMap subgraph per session, but the workspace owns a single subgraph shared across all its sessions. Creating a parallel subgraph per session would fragment the research area's knowledge graph.

The workspace manages its own session lifecycle via a `workspace_session` table. `ResearchSession` / `ResearchSessionService` continue to work unchanged for non-workspace research flows.

`WorkspaceDescriptor` carries the topic-level identity including the MindMap subgraph:

```java
record WorkspaceDescriptor(
    String id,
    String name,
    Set<String> targetCategories,
    FreshnessPolicy freshnessPolicy,
    String tenantId,
    String mindMapSubgraphId,  // RESEARCH_AREA subgraph, created at workspace creation
    Instant createdAt,
    WorkspaceState state    // OPEN, ARCHIVED
) {}
```

## Type Definitions

### Content (sealed hierarchy)

```java
sealed interface Content {
    String mediaType();

    record TextContent(String body, String mediaType) implements Content {}
    record BinaryContent(byte[] data, String mediaType) implements Content {}
    record StructuredContent(Map<String, Object> data, String schemaRef,
                             String mediaType) implements Content {}
}
```

Lives in knowledge-pipeline-api initially. When corpus-management-api remediates L1 (CorpusEntry.body → Content, tracked by #785/#789), this type should be extracted to a shared location (corpus-api or a new content-api module). Placing it in knowledge-pipeline-api now avoids premature coupling — neither knowledge-pipeline-api nor corpus-api currently depends on the other, and introducing that dependency for a type that will move is unnecessary churn.

### WorkspaceEntry

```java
record WorkspaceEntry(
    String id,
    String workspaceId,
    String sessionId,
    String agentId,
    Content content,
    String extractedText,         // clean text for embedding/search
    URI sourceUri,                // nullable — derived entries may not have a source
    Instant storedAt,
    Instant fetchedAt,            // nullable — when content was originally acquired
    Set<String> categories,       // coverage categories this entry covers
    ReliabilityTier reliability,
    List<String> derivedFrom,     // IDs of source entries (enrichment DAG)
    Map<String, String> properties,  // extensible metadata
    String promotedTo,            // nullable — CorpusRef ID if promoted
    Instant promotedAt            // nullable
) {}
```

### WorkspaceInput (store request)

```java
record WorkspaceInput(
    Content content,
    String extractedText,         // nullable — auto-extracted from TextContent if absent
    URI sourceUri,                // required when derivedFrom is empty (provenance enforcement)
    String agentId,               // nullable in v1; identifies the calling agent
    Set<String> categories,
    ReliabilityTier reliability,  // default UNKNOWN
    List<String> derivedFrom,     // empty for original sources
    Map<String, String> properties
) {
    public WorkspaceInput {
        if ((derivedFrom == null || derivedFrom.isEmpty()) && sourceUri == null) {
            throw new IllegalArgumentException(
                "sourceUri is required for non-derived entries (provenance enforcement)");
        }
    }
    // Builder-style withCategories(), withReliability(), withDerivedFrom()
}
```

### ReliabilityTier

```java
enum ReliabilityTier {
    AUTHORITATIVE,  // primary source, official API docs, RFC specifications
    OFFICIAL,       // company website, press releases, official blogs
    CURATED,        // edited publications, curated databases, peer-reviewed
    COMMUNITY,      // forums, Stack Overflow, Reddit, unvetted blogs
    UNKNOWN         // default when not assessed
}
```

### FreshnessPolicy

```java
record FreshnessPolicy(
    Duration defaultMaxAge,
    Map<String, Duration> categoryOverrides
) {
    public Duration maxAgeFor(String category) {
        return categoryOverrides.getOrDefault(category, defaultMaxAge);
    }

    public Set<String> staleCategories(Set<String> categories, Instant storedAt, Instant now) {
        Set<String> stale = new LinkedHashSet<>();
        for (String category : categories) {
            if (storedAt.plus(maxAgeFor(category)).isBefore(now)) {
                stale.add(category);
            }
        }
        return stale;
    }

    public static FreshnessPolicy withDefault(Duration maxAge) { ... }
}
```

Freshness is evaluated per-category independently. An entry in "pricing" (7-day max age) and "product-features" (30-day max age) after 10 days is stale only in "pricing" — the `staleCategories()` method returns `{"pricing"}`, not `{"pricing", "product-features"}`.

### CoverageReport

```java
record CoverageReport(
    String workspaceId,
    Map<String, CategoryCoverage> categories,
    Set<String> uncovered
) {
    record CategoryCoverage(String category, int entryCount,
                            Map<ReliabilityTier, Integer> reliabilityDistribution,
                            Instant lastUpdated) {}
}
```

### WorkspacePromotion SPI

```java
interface WorkspacePromotion {
    WorkspacePromotionResult promote(String workspaceId, Set<String> entryIds,
                                     String targetCorpusName, String tenantId);
}

record WorkspacePromotionResult(
    List<PromotedEntry> promoted,
    List<SkippedEntry> skipped
) {
    record PromotedEntry(String entryId, String corpusEntryId) {}
    record SkippedEntry(String entryId, String reason) {}
}
```

The `targetCorpusName` is a plain `String` (not `CorpusRef`) to avoid coupling `knowledge-pipeline-api` to `rag-api`. The implementation constructs `CorpusRef(tenantId, targetCorpusName)` internally.

**Content → CorpusEntry conversion for promote():**
- `TextContent`: `body = content.body()` — direct mapping
- `StructuredContent`: `body = JSON.stringify(data)`, `mediaType` stored in `CorpusEntry.metadata`
- `BinaryContent`: **skipped** with reason `"binary content not promotable until CorpusEntry.body L1 remediation (#785)"` — no lossy conversion

## ResearchWorkspace SPI

```java
interface ResearchWorkspace {

    // --- Workspace lifecycle ---
    WorkspaceDescriptor createWorkspace(String name, Set<String> targetCategories,
                                         FreshnessPolicy freshnessPolicy, String tenantId);
    WorkspaceDescriptor getWorkspace(String workspaceId, String tenantId);
    void archiveWorkspace(String workspaceId, String tenantId);
    List<WorkspaceDescriptor> listWorkspaces(String tenantId);

    // --- Session lifecycle (workspace-native, NOT ResearchSessionService) ---
    String openSession(String workspaceId, String name, String tenantId);
    void closeSession(String sessionId, String tenantId);

    // --- Core operations (the tool contract) ---

    // Store a finding with provenance (also used for enrichment — set derivedFrom on WorkspaceInput)
    String store(WorkspaceInput input, String sessionId, String tenantId);

    // Semantic search across workspace (maxResults passed through to CaseRetriever)
    List<WorkspaceEntry> search(String query, String workspaceId, int maxResults, String tenantId);

    // Coverage: what's been covered, what's missing
    CoverageReport coverage(String workspaceId, String tenantId);

    // Freshness: which entries are stale per the workspace's policy, with stale categories identified
    List<StaleEntry> stale(String workspaceId, String tenantId);

    // Delete: remove a finding (Qdrant first, then SQLite — per §Consistency Model)
    void deleteEntry(String entryId, String tenantId);

    // Promote: graduate entries from workspace to corpus
    WorkspacePromotionResult promote(String workspaceId, Set<String> entryIds,
                                     String targetCorpusName, String tenantId);

    // Reconciliation: re-sync Qdrant from SQLite source of truth (additive-only)
    void reconcile(String workspaceId, String tenantId);
}
```

`enrich()` is not on the SPI because it is syntactic sugar over `store()` with `derivedFrom` set. The `WorkspaceOrchestrator` provides an `enrich()` convenience method that the LLM tool layer uses to present enrichment as a distinct operation:

```java
// On WorkspaceOrchestrator (not on the SPI)
public String enrich(WorkspaceInput analysis, List<String> sourceEntryIds,
                     String sessionId, String tenantId) {
    WorkspaceInput enriched = analysis.withDerivedFrom(sourceEntryIds);
    return store(enriched, sessionId, tenantId);
}
```

### StaleEntry

```java
record StaleEntry(WorkspaceEntry entry, Set<String> staleCategories) {}
```

Each entry is returned with the specific categories that triggered its staleness. This lets the LLM (or caller) decide how to respond — "refresh pricing data" vs "refresh product features" requires knowing which category is stale.

## Data Flow

### Store Operation

```
LLM calls store(input, sessionId, tenantId)
  │
  ├─ 1. Validate: sourceUri OR derivedFrom must be present
  ├─ 2. Generate entryId (UUID)
  ├─ 3. WorkspaceStore.save(entry)           → SQLite row (source of truth)
  ├─ 4. If extractedText is present:
  │     ├─ Chunk via TextChunker if extractedText > chunk threshold
  │     ├─ Build ChunkInput list:
  │     │     sourceDocumentId = entryId
  │     │     metadata = {"workspaceId": "...", "sessionId": "..."}
  │     ├─ EmbeddingIngestor.ingest(corpusRef, chunks)  → Qdrant vectors
  │     │     corpusRef = CorpusRef(tenantId, "research-workspace")
  │     └─ On Qdrant failure: log warning, entry remains stored but unsearchable
  └─ 5. return entryId
```

**CorpusRef derivation:** One Qdrant collection per tenant: `CorpusRef(tenantId, "research-workspace")`. Workspace isolation is achieved via `PayloadFilter.eq("workspaceId", workspaceId)` on search, not by separate collections. This matches the existing tenancy pattern and avoids collection proliferation.

**Chunking:** For `TextContent`, `extractedText` defaults to `content.body()` if absent. For `BinaryContent`, `extractedText` must be provided by the caller (e.g., from Tika or OCR). For `StructuredContent`, `extractedText` defaults to `JSON.stringify(data)` if absent. If `extractedText` is null or blank after defaults, the entry is stored in SQLite but not embedded in Qdrant — it's invisible to semantic search but still visible to coverage tracking and direct lookup.

**Dedup:** No automatic dedup on `store()`. The LLM is expected to use `search()` before storing if it wants to avoid duplicates — this is the natural research workflow ("check what I already have, then add what's missing"). The `idx_ws_entry_source` index enables efficient source-URI lookup if a future version needs store-time dedup.

### Search Operation

```
LLM calls search(query, workspaceId, maxResults, tenantId)
  │
  ├─ 1. Build RetrievalQuery from query string
  ├─ 2. Build PayloadFilter: eq("workspaceId", workspaceId)
  ├─ 3. CaseRetriever.retrieve(query, corpusRef, maxResults, filter)
  │     → List<RetrievedChunk>
  ├─ 4. Extract entry IDs: chunk.sourceDocumentId() → entryId
  │     (sourceDocumentId was set to entryId during store())
  ├─ 5. Deduplicate entry IDs (multiple chunks may come from the same entry)
  │     When an entry has multiple matching chunks, the highest chunk score
  │     determines the entry's search ranking (CaseRetriever returns chunks
  │     sorted by descending score, so first occurrence = max score).
  ├─ 6. Batch-load WorkspaceEntry records from WorkspaceStore by IDs
  ├─ 7. Order by entry's max chunk relevanceScore (preserving search ranking)
  └─ 8. return matched WorkspaceEntries
```

The `sourceDocumentId` round-trip is the key mechanism: during `store()`, each entry's ID is used as `ChunkInput.sourceDocumentId`. During `search()`, `RetrievedChunk.sourceDocumentId()` maps back to the entry ID for SQLite lookup. The `PayloadFilter` ensures workspace isolation without separate Qdrant collections.

### Enrich Operation (Convenience, not SPI)

```
LLM tool calls enrich(analysis, sourceEntryIds, sessionId, tenantId)
  │
  ├─ WorkspaceOrchestrator.enrich() sets derivedFrom = sourceEntryIds
  ├─ Delegates to store(enrichedInput, sessionId, tenantId)
  └─ return new entryId
```

Enrichment is a convenience on `WorkspaceOrchestrator`, not on the `ResearchWorkspace` SPI. It sets `derivedFrom` and delegates to `store()`. The LLM tool layer exposes it as a distinct tool ("I'm adding analysis on top of evidence") while the SPI remains minimal with a single `store()` method.

### Delete Operation

```
LLM calls deleteEntry(entryId, tenantId)
  │
  ├─ 1. Load entry from WorkspaceStore to get workspaceId
  ├─ 2. EmbeddingIngestor.deleteDocument(corpusRef, entryId)  → Qdrant (best-effort)
  │     corpusRef = CorpusRef(tenantId, "research-workspace")
  │     On failure: log warning (orphaned Qdrant document is harmless —
  │     search resolution filters it out in step 6 of Search Operation)
  ├─ 3. WorkspaceStore.delete(entryId)                        → SQLite
  └─ 4. Done — no return value
```

Entries are immutable snapshots. To "update" a finding, the LLM stores a fresh replacement and deletes the stale original. This keeps the derivation DAG clean — entries that reference the deleted entry via `derivedFrom` retain a dangling reference, which is acceptable (the derivation chain records what existed at analysis time, not what exists now).

### Coverage Operation

```
LLM calls coverage(workspaceId, tenantId)
  │
  ├─ WorkspaceCoverageTracker.compute(workspaceId)
  │   ├─ WorkspaceDescriptor.targetCategories    → what should be covered
  │   ├─ WorkspaceStore.categoryCounts()          → what IS covered
  │   └─ Set difference                           → what's missing
  └─ return CoverageReport
```

### Stale Operation

```
LLM calls stale(workspaceId, tenantId)
  │
  ├─ WorkspaceFreshnessChecker.findStale(workspaceId)
  │   ├─ Load all entries for workspace from WorkspaceStore
  │   ├─ For each entry, evaluate each category independently:
  │   │   ├─ FreshnessPolicy.maxAgeFor(category)  → category-specific threshold
  │   │   └─ entry.storedAt + threshold < now?     → this category is stale
  │   ├─ Collect stale categories per entry
  │   └─ Return entries that have at least one stale category
  └─ return List<StaleEntry>  (entry + staleCategories)
```

Each `StaleEntry` identifies which specific categories triggered staleness. An entry in "pricing" (7-day) and "product-features" (30-day) after 10 days returns `staleCategories = {"pricing"}` — the caller knows to refresh pricing data, not product features.

### Promote Operation

```
LLM calls promote(workspaceId, entryIds, targetCorpusName, tenantId)
  │
  ├─ WorkspacePromotion.promote()
  │   ├─ For each entry:
  │   │   ├─ Skip stale entries (reason: "entry is stale — refresh before promoting")
  │   │   ├─ Skip already-promoted entries (reason: "already promoted to <corpusEntryId>")
  │   │   ├─ Skip BinaryContent entries (reason: "binary content not promotable until L1 remediation")
  │   │   ├─ Convert Content → CorpusEntry.body:
  │   │   │   ├─ TextContent: body = content.body()
  │   │   │   └─ StructuredContent: body = JSON.stringify(data), mediaType in metadata
  │   │   ├─ CorpusEntryStore.add(corpusRef, corpusEntry)
  │   │   │     corpusRef = CorpusRef(tenantId, targetCorpusName)
  │   │   └─ Mark entry as promoted (promotedTo = corpusEntryId, promotedAt = now)
  └─ return WorkspacePromotionResult
```

## Storage Backend

### WorkspaceStore (SQLite)

Follows the project's established SQLite + HikariCP WAL + Flyway pattern (same as SqliteMindMapStore, SqliteMemoryStore, etc.).

**Tables:**

```sql
CREATE TABLE workspace (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    target_categories TEXT,     -- JSON array
    freshness_policy TEXT,      -- JSON
    tenant_id TEXT NOT NULL,
    mindmap_subgraph_id TEXT,  -- RESEARCH_AREA subgraph, created at workspace creation
    state TEXT NOT NULL DEFAULT 'OPEN',
    created_at TEXT NOT NULL
);

CREATE TABLE workspace_session (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspace(id),
    name TEXT NOT NULL,
    state TEXT NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / PAUSED / COMPLETED
    created_at TEXT NOT NULL,
    closed_at TEXT
);

CREATE TABLE workspace_entry (
    id TEXT PRIMARY KEY,
    workspace_id TEXT NOT NULL REFERENCES workspace(id),
    session_id TEXT NOT NULL REFERENCES workspace_session(id),
    agent_id TEXT,
    content_type TEXT NOT NULL,    -- 'text', 'binary', 'structured'
    media_type TEXT NOT NULL,
    text_body TEXT,                -- for TextContent
    binary_data BLOB,             -- for BinaryContent
    structured_data TEXT,         -- JSON for StructuredContent
    schema_ref TEXT,
    extracted_text TEXT,           -- clean text for embedding
    source_uri TEXT,
    stored_at TEXT NOT NULL,
    fetched_at TEXT,
    categories TEXT,              -- JSON array
    reliability TEXT DEFAULT 'UNKNOWN',
    derived_from TEXT,            -- JSON array of entry IDs
    properties TEXT,              -- JSON object
    promoted_to TEXT,
    promoted_at TEXT
);

CREATE INDEX idx_ws_entry_workspace ON workspace_entry(workspace_id);
CREATE INDEX idx_ws_entry_session ON workspace_entry(session_id);
CREATE INDEX idx_ws_entry_stored ON workspace_entry(workspace_id, stored_at);
CREATE INDEX idx_ws_entry_source ON workspace_entry(workspace_id, source_uri);
CREATE INDEX idx_ws_session_workspace ON workspace_session(workspace_id);
```

Configure path via `casehub.knowledge.workspace.sqlite.path`.

No FTS5 virtual table — semantic search goes through Qdrant (`CaseRetriever`), and adding FTS5 without a consumer creates dead schema with maintenance burden (sync triggers, migration complexity). FTS5 can be added when a keyword-search consumer is defined.

## MindMap Integration

When a workspace is created, a `RESEARCH_AREA` subgraph is created in MindMap (following the existing `ResearchOrchestrator` pattern but managed by `WorkspaceOrchestrator`). The subgraph ID is stored on `WorkspaceDescriptor.mindMapSubgraphId`. All sessions within the workspace share this single subgraph — unlike `ResearchSession` which creates a subgraph per session, the workspace owns one subgraph for the entire research topic.

Research findings can be promoted to MindMap nodes via `EntityPromoter`, linking them into the knowledge graph.

The workspace does NOT automatically write to CaseMemoryStore, CBR, or the experience system. Research is external knowledge, not agent experience.

## Multi-Agent Readiness

Every `WorkspaceEntry` carries an `agentId` field. In v1, a workspace is used by a single agent per session. The data model supports future multi-agent coordination:

- Cross-agent search: query all entries in a workspace regardless of agentId
- Agent-scoped views: filter by agentId for per-agent results
- Coverage by agent: which categories has each agent covered?

No concurrency control, locking, or cross-agent dedup in v1.

## Research Conventions (Platform Guidance)

The workspace tools encode research discipline through their affordances:

1. **Everything has provenance.** `store()` requires either `sourceUri` (for original sources) or `derivedFrom` (for analysis built on existing entries). At least one must be present — this is enforced at the type level by `WorkspaceInput`'s compact constructor. The tool makes provenance the only path.

2. **Coverage is explicit.** Workspaces define target categories up-front. `coverage()` shows gaps. The LLM can't claim "research complete" without checking coverage. Note: coverage tracks quantity, not quality — a category with one low-quality entry shows as "covered." Quality assessment can layer on in a future version.

3. **Freshness is tracked.** `stale()` surfaces entries past their category's threshold, identifying which specific categories are stale. The LLM is prompted to re-check stale sources with category-specific context. Freshness is per-category, not per-entry — an entry can't have a custom freshness that differs from its category. Each category is evaluated independently: a multi-category entry may be stale in some categories but fresh in others.

4. **Analysis links to evidence.** `enrich()` creates new entries with `derivedFrom` references. The derivation chain is traceable. Any claim can be traced back to its source.

5. **Source reliability is declared.** `ReliabilityTier` on every entry. Search can weight or filter by tier. The LLM is nudged to assess source quality.

6. **Research is auditable.** Every entry has `agentId`, `sessionId`, `storedAt`, `sourceUri`. A human can review any research workspace and trace every finding to its source.

These are not rules the LLM must follow — they are tool affordances that make disciplined research the easiest path.

## Error Handling

- `store()` with missing `sourceUri` AND empty `derivedFrom`: **rejected** (`IllegalArgumentException`). Every entry must have provenance — either a source or a derivation chain.
- `store()` with `BinaryContent` and no `extractedText`: stored in SQLite but not embedded in Qdrant. Coverage tracking still works. Not semantically searchable.
- `store()` with Qdrant failure: entry is stored in SQLite (source of truth). Warning logged. Entry becomes searchable after next `reconcile()`.
- `coverage()` on a workspace with no target categories: returns empty report.
- `stale()` on a workspace with no freshness policy: returns empty list (nothing is stale without a policy).
- `promote()` on already-promoted entries: skipped with reason in `WorkspacePromotionResult`.
- `promote()` on stale entries: skipped with reason.
- `promote()` on `BinaryContent` entries: skipped with reason "binary content not promotable until L1 remediation."
- `search()` on empty workspace: returns empty list.
- `deleteEntry()` on non-existent entry: no-op (idempotent).
- `deleteEntry()` with Qdrant failure: SQLite entry still deleted. Orphaned Qdrant document is harmless — search resolution (step 6) filters it out when the SQLite lookup returns nothing.
- `reconcile()`: additive-only, idempotent, safe to call at any time. Re-indexes SQLite entries missing from Qdrant. Does NOT delete Qdrant documents (shared tenant collection — orphan deletion would corrupt other workspaces).

## Testing Strategy

- **Contract test:** `ResearchWorkspaceContractTest` abstract base — tests all 4 SPI operations + promotion against the SPI contract
- **SQLite store test:** `WorkspaceStoreTest` — SQLite-specific (JSON queries, session CRUD, source-URI lookup)
- **Coverage test:** category tracking, uncovered detection, multi-category entries
- **Freshness test:** per-category policy, independent category evaluation, multi-category entries stale in subset of categories, default fallback
- **Enrichment test:** derivedFrom DAG integrity via store() with derivedFrom, multi-source derivation
- **Provenance test:** sourceUri required for non-derived entries, derivedFrom required for derived entries, rejection when both absent
- **Promotion test:** happy path, stale skip, duplicate skip, binary skip, StructuredContent→JSON conversion, promotedTo/promotedAt tracking
- **Consistency test:** SQLite write succeeds + Qdrant failure → entry stored but unsearchable; reconcile() re-indexes; reconcile() does NOT delete other workspaces' Qdrant documents in shared collection
- **Content test:** sealed hierarchy exhaustive matching, mediaType handling
- **Session test:** workspace-native session lifecycle (open/close/list), independence from ResearchSession

## Implementation Plan Sketch

1. `Content` sealed hierarchy + `ReliabilityTier` + `FreshnessPolicy` + `StaleEntry` (pure types, zero deps)
2. `WorkspaceEntry` + `WorkspaceInput` + `WorkspaceDescriptor` + `CoverageReport` + `WorkspacePromotionResult` (value types)
3. `ResearchWorkspace` SPI + `WorkspacePromotion` SPI (no CorpusRef dependency)
4. Add `rag-api` dependency to `knowledge-pipeline/pom.xml`
5. `WorkspaceStore` (SQLite — entry CRUD, session CRUD, coverage queries, freshness queries)
6. `WorkspaceOrchestrator` (implements ResearchWorkspace, wires store + EmbeddingIngestor + CaseRetriever + MindMap)
7. `NoOpWorkspacePromotion` @DefaultBean
8. Contract tests + store tests
9. Research conventions doc (platform guidance)

## References

- casehubio/neocortex#804 — parent issue
- casehubio/neocortex#785 — corpus domain abstraction audit
- `docs/specs/2026-11-04-corpus-domain-abstraction-audit.md` — Corpus vs Workspace architectural insight
- `knowledge-pipeline-api/src/.../ResearchSession.java` — existing session model
- `knowledge-pipeline-api/src/.../ResearchSessionService.java` — existing session lifecycle
- `knowledge-pipeline-api/src/.../CacheStore.java` — existing cache SPI
- `knowledge-pipeline-api/src/.../CachedEntity.java` — existing entity model with provenance fields
- `knowledge-pipeline/src/.../ResearchOrchestrator.java` — existing MindMap subgraph integration
- `knowledge-pipeline-api/src/.../SearchableProvider.java` — existing domain search SPI
- `rag-api/src/.../EmbeddingIngestor.java` — vector ingestion SPI
- `rag-api/src/.../CaseRetriever.java` — semantic search SPI
- `corpus-management-api/src/.../CorpusEntry.java` — existing entry model (L1 String body constraint)
- `corpus-management-api/src/.../CorpusEntryStore.java` — corpus CRUD SPI
