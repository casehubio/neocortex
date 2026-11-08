# Corpus Library Redesign — Git Backend + Unified Pipeline

**Date:** 2026-11-06
**Status:** Draft
**Tracks:** casehubio/neocortex#789 (epic)
**Requires new issues:** Git backend, pipeline unification, and SPI renames are new work items under #789. Specific tracking issues to be filed before implementation begins.
**Supersedes:** 2026-06-11-corpus-storage-module-design.md (partially — zip chain model retained, architecture restructured)

## Problem

The corpus infrastructure has three problems:

1. **Git-unaware change detection.** The Hortora garden is a git repo, but the indexing pipeline uses filesystem mtimes for change detection. A `git checkout` triggers a full re-index. Mtime-based cursors can't distinguish content changes from git operations.

2. **Two parallel paths to Qdrant.** `CorpusIngestionService` (ChangeSource → CorpusReader → embed) and `SqliteCorpusEntryStore.add()` (direct → EmbeddingIngestor) duplicate metadata extraction and embedding logic with no shared pipeline. Provenance tracking only covers one path.

3. **Zip-centric vocabulary in the SPI.** `CorpusStore.append()` and `VersionInfo.zipFile` assume the zip storage model. The API leaks identified in the 2026-11-04 audit (L1–L6) further embed Hortora assumptions into the generic SPIs.

## Design Principles

1. **The corpus library is a coordination layer** — catalogue, search, provenance, and ingestion pipeline over backend-agnostic content stores. It does not own content storage.
2. **Users don't know or care about the backend** — `write()` puts content somewhere; `read()` gets it back with provenance; `search()` finds it semantically. Whether it's git, zip, or flat is the library's concern.
3. **Git operations stay in git** — no wrapping of branches, tags, merging, or collaboration features. The API provides CRUD and search; users who want git's power use git directly alongside the API.
4. **One pipeline, two entry points** — programmatic writes (CorpusEntryStore) and detected changes (ChangeSource) both flow through the same IngestionPipeline.

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                   Corpus Library                     │
│                                                     │
│  CorpusCatalogue    CorpusSearch    ProvenanceStore  │
│  (registry)         (semantic)      (decision lineage│
│                                      — existing SPI) │
│                                                     │
│  CorpusEntryStore ──→ IngestionPipeline (rag-core)   │
│  (programmatic CRUD)   (extract → chunk → embed)     │
│                            ↑                        │
│  CorpusIngestionService ───┘                        │
│  (ChangeSource poll loop, also in rag-core)         │
│                                                     │
│  AccessTrackingReader wrapping CorpusReader           │
│  (construction-time wrap — emits read events for    │
│   access provenance, distinct from decision prov.)  │
├─────────────────────────────────────────────────────┤
│                Content Backends                      │
│                                                     │
│  GitCorpusBackend     ZipCorpusBackend   FlatBackend │
│  ├ CorpusStore        ├ CorpusStore      ├ CorpusStore│
│  ├ CorpusReader       ├ CorpusReader     ├ CorpusReader│
│  ├ GitChangeSource    ├ ZipChangeSource  ├ FlatChange │
│  └ JGit (ref-based)   └ Zip4j (chain)   └ mtime     │
├─────────────────────────────────────────────────────┤
│                   Search Index                       │
│                                                     │
│  EmbeddingIngestor ──→ Qdrant                       │
└─────────────────────────────────────────────────────┘
```

**Content-flow paths:**

- **Programmatic:** `CorpusEntryStore.add()` does two things in parallel from the same `CorpusEntry`:
  1. **Backend persistence:** `ContentSerializer.serialize(entry)` → `CorpusStore.write()` → `CorpusStore.flush()` — stores content in git/zip/flat for later reads
  2. **Embedding:** builds `ExtractionResult` directly from `CorpusEntry` fields (no re-extraction needed) → `IngestionPipeline.ingest(path, extractionResult, context)` → Qdrant
  Metadata is also stored in SQLite for query access.
- **ChangeSource:** external write (git commit, file drop) → `ChangeSource` detects → `CorpusIngestionService` (in `rag-core`) reads via `CorpusReader` → `IngestionPipeline.ingest(path, bytes, context)` (runs `MetadataExtractor.extract()`) → Qdrant

Both paths share `IngestionPipeline` (in `rag-core/`) which receives per-corpus configuration via `IngestionContext` (TextChunker + CorpusRef). The ChangeSource path uses the raw-bytes overload (needs extraction); the programmatic path uses the pre-extracted overload (metadata already available). No duplicate logic, no wasteful serialization roundtrip.

**Dual storage model:** SQLite stores the management layer (catalogue, entry metadata, decision provenance). Content lives in git/zip/flat. For the programmatic path, `CorpusEntryStore.add()` writes to both: content to the backend store (via `CorpusStore.write()`), metadata to SQLite. For the ChangeSource path, content already exists in the backend; metadata is extracted by `IngestionPipeline` and stored only in the embedding index.

**Serialization bridge:** `CorpusEntryStore.add()` must serialize `CorpusEntry(id, metadata, body, artifactRef)` into the backend format for persistent storage. The path is `entry.id()` (with extension determined by corpus type config). For markdown corpora, serialization writes YAML frontmatter + body. For binary content (future: Content sealed hierarchy), serialization writes raw bytes. `ContentSerializer` SPI (in `corpus-management-api/`, alongside `CorpusEntry`) handles per-corpus format differences — the inverse of `MetadataExtractor`. The serialized bytes go ONLY to backend storage; they are NOT re-parsed for embedding (the programmatic path uses the pre-extracted `IngestionPipeline` overload instead).

## SPI Changes

### CorpusStore — rename append to write, add flush

```java
public interface CorpusStore {
    void write(String path, byte[] content);
    void write(String path, InputStream content);
    void write(String path, Path file);
    void delete(String path);
    void flush();
}
```

`write()` replaces `append()`. The SPI contract: `write(path, content)` stages content at `path`; after `flush()`, `read(path)` returns the written content. Writes may be buffered until `flush()` is called.

**Read-before-flush visibility is undefined.** Between `write()` and `flush()`, `read(path)` may return the new content, stale content, or empty — this is backend-dependent and callers MUST NOT rely on it:
- **Git:** returns stale/absent (reads from committed refs; writes are in the working tree)
- **Zip/Flat:** may return new content (writes are immediately durable on the filesystem)

**Crash before flush() loses uncommitted writes.** If the process terminates between `write()` and `flush()`, writes that were not flushed are lost. This is analogous to an unflushed file buffer. Callers must treat `flush()` as the durability boundary. See §Failure Modes for per-backend recovery details.

**`write()` has replace semantics** — calling `write("doc.md", v2)` after `write("doc.md", v1)` replaces v1 with v2 at the path level. Version history preservation is backend-dependent, not an SPI guarantee:
- **Git:** version history preserved via git commits (created on `flush()`)
- **Zip:** version history preserved (each flush appends a new archive entry)
- **Flat:** no version history (filesystem overwrite)

`versions(path)` on `CorpusReader` returns available history when the backend supports it. Callers that require version history must use a backend that provides it.

**`flush()` behaviour:**
- **Git:** `git add` all pending writes → `git commit` with batch message → updates ref
- **Zip:** finalises the current entry batch
- **Flat:** no-op (writes are immediately durable on the filesystem)

### VersionInfo — backend-neutral source field

```java
public record VersionInfo(int version, Instant timestamp, String source) {}
```

`source` replaces `zipFile`. Contains the zip filename for zip, commit SHA for git, or file path for flat.

### API leak remediation (from 2026-11-04 audit)

**Already completed** (Phase 1 #800, Phase 2 #801 — both CLOSED):
- L3: InteractionContext/InteractionType/ActivityType — already moved to `corpus-management/impl/`
- L6: CorpusEntry.frontmatter → metadata — `CorpusEntry` already uses `metadata` field

**Remaining work in this spec:**

| Leak | Change | Status |
|------|--------|--------|
| L1: CorpusEntry.body is String | Introduce `Content` sealed hierarchy (TextContent, BinaryContent, StructuredContent) | New work |
| L2: BuiltInCorpusTypes in corpus-management | `KNOWLEDGE_GARDEN`/`RESEARCH`/`TRANSCRIPT` constants were moved out of the `CorpusType` API record (Phase 2 done). This spec further refines: move from `BuiltInCorpusTypes` in `corpus-management/impl/` to `HortoraCorpusTypes` in `corpus-management-hortora/` — separating garden-specific constants from the generic module | Refinement of Phase 2 |

## Git Backend — corpus-git Module

**New module.** Depends on corpus-api + JGit. Hortora-eligible (zero casehub domain dependencies).

### GitCorpusStore implements CorpusStore

- `write(path, content)` → write file to working tree → stage for next flush
- `delete(path)` → `git rm path` → stage for next flush
- `flush()` → `git add` all staged paths → `git commit` with descriptive message → ref updated
- Directory creation handled implicitly (JGit creates parent directories)
- Writes are buffered in the working tree until `flush()`. `GitCorpusReader` reads from refs (committed tree), so uncommitted writes are invisible to readers until flushed. This is intentional — it enables atomic batch visibility.
- Single-write convenience: callers that need immediate visibility (e.g. MCP tool calls) call `write()` then `flush()`. Batch operations call `write()` N times, then `flush()` once.

### GitCorpusReader implements CorpusReader

- Reads from a configured ref's tree via JGit (`RevWalk` + `TreeWalk`)
- `read(path)` → resolve ref → walk tree → read blob
- `readVersion(path, version)` → `git log --follow path` → nth commit → read blob at that commit
- `versions(path)` → `git log --follow path` → `List<VersionInfo>` with commit SHA as source, author timestamp
- `list()` / `list(prefix)` → `TreeWalk` over ref tree, prefix filtering

### GitChangeSource implements WatchableChangeSource

- `changesSince(cursor)` → `JGit DiffFormatter` between cursor SHA and current ref HEAD → `ChangeSet` with new SHA as cursor
- `fullScan()` → `TreeWalk` over entire ref tree → `ChangeSet` with HEAD SHA as cursor
- `watch()` → polls using JGit's ref resolution API (`Repository.exactRef()`), which correctly handles both loose refs under `.git/refs/` and packed refs in `.git/packed-refs`. Poll interval is configurable (default: 5s). Does NOT poll the filesystem directly.
- `currentCursor()` → resolves the configured ref via JGit → returns current commit SHA
- Cursor is a commit SHA — monotonic, immune to clock skew, branch-switch safe

### Configuration

```properties
casehub.corpus.mylib.backend=git
casehub.corpus.mylib.path=/path/to/repo
casehub.corpus.mylib.ref=refs/heads/main    # default: HEAD
casehub.corpus.mylib.poll-interval=5s       # default: 5s
```

Each git-backed corpus specifies which ref to index. Different corpora can track different branches (e.g. main for published, draft for WIP).

### Failure Modes

| Failure | Behaviour | Recovery |
|---------|-----------|----------|
| **Concurrent writes** | JGit uses `.lock` files for ref updates. If a lock is held, `flush()` retries with exponential backoff (3 attempts, 100ms/200ms/400ms), then throws `ConcurrentModificationException`. | Caller retries or queues the write. Single-writer-per-corpus is the expected deployment pattern. |
| **Corrupt repository** | JGit throws `CorruptObjectException` on read or write. `GitCorpusReader.read()` returns `Optional.empty()` for unreadable paths; `GitCorpusStore.flush()` throws, leaving the working tree dirty. | Manual `git fsck` + repair. The working tree changes are preserved and can be committed after repair. |
| **Missing ref** | `GitCorpusReader` operations return empty results. `GitChangeSource.fullScan()` returns an empty `ChangeSet`. `GitCorpusStore.flush()` creates the ref (first commit on the branch). | Self-healing: first write creates the ref. |
| **Disk full** | JGit's commit is not atomic — a partial object write can leave orphaned loose objects. The ref is updated last, so a failure before ref update leaves the previous state intact. | `git gc` cleans orphaned objects. Retry after freeing disk space. |
| **Detached HEAD** | When `ref` defaults to `HEAD` and HEAD is detached, reads work (detached HEAD points to a commit). Writes via `flush()` advance HEAD. | Configure an explicit `ref=refs/heads/main` to avoid detached HEAD issues. |
| **Crash between write() and flush()** | Pending writes exist as uncommitted files in the working tree. `GitCorpusReader` (reads from refs) and `GitChangeSource` (reads from committed state) do not see them. On restart, `GitCorpusStore` detects dirty working tree via `git status` (JGit `Status` API). | On construction, `GitCorpusStore` checks for uncommitted modifications. If found: logs a warning with the list of dirty paths, then discards them (`git checkout -- .`). The upstream source (ChangeSource or CorpusEntryStore) will re-deliver on the next poll/reconciliation cycle. This mirrors the zip backend's "only entries since the last rollover may be lost" contract. |

## Shared IngestionPipeline

**Lives in `rag-core/`** alongside `CorpusIngestionService` and `CorpusIngestionBinding`. All its dependencies are `rag-api` types (`MetadataExtractor`, `TextChunker`, `ExtractionResult`, `ChunkInput`, `EmbeddingIngestor`) plus `CorpusReader` from `corpus-api`. Both already on `rag-core`'s dependency path — no new module dependencies. This preserves `rag-core`'s "zero CDI, zero Spring" constraint.

```java
public record IngestionContext(
    CorpusRef corpusRef,
    MetadataExtractor metadataExtractor,  // used only by raw-bytes overload
    TextChunker textChunker               // nullable — null means no chunking
) {}

public class IngestionPipeline {
    IngestionPipeline(EmbeddingIngestor embeddingIngestor);

    // ChangeSource path — needs metadata extraction from raw content
    void ingest(String path, byte[] content, IngestionContext context);
    void ingest(String path, CorpusReader reader, IngestionContext context);

    // Programmatic path — metadata already extracted, skip extraction step
    void ingest(String path, ExtractionResult extracted, IngestionContext context);

    void delete(String path, CorpusRef corpusRef);
}
```

- `IngestionContext` carries per-corpus configuration — different corpora use different `MetadataExtractor` and `TextChunker` implementations. This replaces the implicit per-corpus configuration currently carried by `CorpusIngestionBinding.metadataExtractor()` and resolved by `CorpusIngestionService.splitterFor()`.
- Raw-bytes overloads (first two `ingest()` methods): run `context.metadataExtractor().extract()` → `context.textChunker().chunk()` → `EmbeddingIngestor.ingest()` → Qdrant. Used by the ChangeSource path.
- Pre-extracted overload (third `ingest()`): skips extraction, runs `context.textChunker().chunk()` → `EmbeddingIngestor.ingest()` → Qdrant. Used by the programmatic path where `CorpusEntry` fields are mapped directly to `ExtractionResult` without a serialize-then-re-parse roundtrip.

### What moves where

- Metadata extraction + chunking + embedding: extracted into `IngestionPipeline` (in `rag-core/`)
- `CorpusIngestionService` (already in `rag-core/`): becomes a thin ChangeSource poll loop. Builds `IngestionContext` from `CorpusIngestionBinding` fields, delegates to `IngestionPipeline`
- `SqliteCorpusEntryStore.add()` (in `corpus-management/`): reworked to:
  1. Store metadata in SQLite (unchanged)
  2. `ContentSerializer.serialize(entry)` → `CorpusStore.write()` → `CorpusStore.flush()` (backend persistence)
  3. Build `ExtractionResult` from `CorpusEntry` fields → `IngestionPipeline.ingest(path, extracted, context)` (embedding — no re-extraction)
  `corpus-management` reaches `IngestionPipeline` through its existing optional dependency on `rag` (which depends on `rag-core`)

## Access Tracking

**Distinct from decision provenance.** The existing `ProvenanceTracker` SPI (in `rag-api/`) and `ProvenanceStore` implementation (in `corpus-management/impl/`) track *decision provenance*: which documents informed which issues (forward/reverse lineage, feedback context, staleness reports). That system is unchanged.

This spec introduces *access provenance*: recording that a corpus read occurred, with who/when/what context. This is a complementary concern — decision provenance answers "what documents did this decision use?", access provenance answers "who read this document and when?".

**Not a CDI `@Decorator`.** `CorpusReader` instances are plain Java objects — created programmatically by `CorpusBindingProducer` and stored in `CorpusIngestionBinding` records, never managed as CDI beans. A CDI `@Decorator` on `CorpusReader` would have nothing to decorate. Instead, this uses construction-time wrapping — the same two-layer pattern as the platform's `MutationTrackingDecorator` (plain Java, `Consumer<T>` callback) + `MutationTrackingCdiDecorator` (CDI adapter bridging `Event::fire`).

**Layer 1 — framework-neutral wrapper** (in `corpus/`, alongside other `CorpusReader` implementations):

```java
public class AccessTrackingReader implements CorpusReader {
    private final CorpusReader delegate;
    private final CorpusRef corpusRef;
    private final Consumer<AccessEvent> eventSink;

    public AccessTrackingReader(CorpusReader delegate, CorpusRef corpusRef,
                                Consumer<AccessEvent> eventSink) { ... }

    @Override
    public Optional<byte[]> read(String path) {
        Optional<byte[]> result = delegate.read(path);
        if (result.isPresent()) {
            eventSink.accept(new AccessEvent(path, corpusRef, principalFromContext(), Instant.now()));
        }
        return result;
    }
    // remaining CorpusReader methods delegate without tracking
}

public record AccessEvent(String path, CorpusRef corpus, String principal, Instant timestamp) {}
```

`CorpusRef` is bound at construction time — the producer knows the corpus identity when creating each reader. `Consumer<AccessEvent>` decouples the wrapper from CDI.

**Layer 2 — CDI wiring** (in `corpus-quarkus/`):

`CorpusBindingProducer` wraps readers for MCP-facing paths:

```java
// In CorpusBindingProducer (corpus-quarkus)
CorpusReader raw = new GitCorpusReader(...);
CorpusReader tracked = new AccessTrackingReader(raw, corpusRef, accessEvent::fire);
```

Where `accessEvent` is a CDI `Event<AccessEvent>` injected into `CorpusBindingProducer`.

**Batch exclusion by construction:** `CorpusIngestionService` uses the reader from `CorpusIngestionBinding` — which holds the **unwrapped** reader for ingestion bindings. MCP resource handlers get the **wrapped** reader. The split happens in `CorpusBindingProducer`: ingestion bindings use the raw reader, MCP-facing beans get the tracked reader. No runtime flag or `ThreadLocal` needed.

`AccessEvent` observers can persist to the existing provenance SQLite database (new table) or to any other store.

## Module Structure

This section lists only modules being created or modified. Existing modules not touched by this spec (`corpus-management-parsers/`, `corpus-management-spring/`, `rag-cache/`, `rag-query-augmentation/`, `rag-scoring/`, `rag-spring/`, `rag-tracking/`, etc.) are unchanged.

```
corpus-api/                — SPIs: CorpusStore (write+flush, not append), CorpusReader, ChangeSource
corpus/                    — ZipCorpusStore, FlatCorpusStore (retained), CompositeCorpusStore,
                             AccessTrackingReader (new — wraps CorpusReader with access events)
corpus-git/                — NEW: GitCorpusStore, GitCorpusReader, GitChangeSource (JGit)
corpus-management-api/     — Remediated: CorpusCatalogue, CorpusEntryStore, CorpusSearch,
                             ContentSerializer (new SPI — serializes CorpusEntry to backend format)
corpus-management/         — SqliteCorpusCatalogue, SqliteCorpusEntryStore (reworked)
corpus-management-hortora/ — EXISTS (module shell with pom.xml, no source yet).
                             Populated with: HortoraCorpusTypes (moved from BuiltInCorpusTypes),
                             GardenMetadataExtractor (moved from corpus-management/impl),
                             GardenContentSerializer (new), garden-specific scoring
rag-core/                  — CorpusIngestionService (reworked to delegate to IngestionPipeline),
                             IngestionPipeline (new), IngestionContext (new),
                             CorpusIngestionBinding (unchanged)
corpus-quarkus/            — NEW: CorpusBindingProducer (moved from rag/), CDI backend wiring
```

**Dependency flow:**
```
corpus-management-api → corpus-api + rag-api
corpus-management → corpus-management-api + quarkus-arc + sqlite-support +
                    rag (optional, for IngestionPipeline via rag-core)
corpus-management-hortora → corpus-management + rag-api (for MetadataExtractor)
corpus-git → corpus-api + JGit
corpus/ → corpus-api + Zip4j
rag-core → corpus-api + rag-api              (NO dependency on corpus-management)
corpus-quarkus → rag-core (for CorpusIngestionBinding) +
                 corpus-management + corpus-git (optional) + corpus (optional)
```

**Key constraint preserved:** `rag-core` is "Framework-neutral RAG runtime POJOs — zero CDI, zero Spring" (from pom.xml description). `IngestionPipeline` and `IngestionContext` belong in `rag-core` because all their dependencies are `rag-api` types (`MetadataExtractor`, `TextChunker`, `ExtractionResult`, `ChunkInput`, `EmbeddingIngestor`) plus `CorpusReader` from `corpus-api`. No new dependencies introduced.

`corpus-management` reaches `IngestionPipeline` through its existing optional dependency on `rag` (which depends on `rag-core`). `corpus-quarkus` produces `CorpusIngestionBinding` beans consumed by `CorpusIngestionService` — standard CDI producer/consumer pattern, no circular dependency.

## Hortora Migration

The Hortora garden is already a git repo. Migration:

1. `CorpusBindingProducer` (in `rag/`, moving to `corpus-quarkus/`) gains a `"GIT"` case in its mode switch — alongside existing `"ZIP"`, `"COMPOSITE"`, and `"FLAT"` cases. The GIT case constructs `GitCorpusStore` + `GitCorpusReader` + `GitChangeSource`.
2. Config: `backend=git`, `ref=refs/heads/main`, `path=~/.hortora/garden`
3. First run: `fullScan()` rebuilds the Qdrant index from the git tree (one-time)
4. After that: `changesSince(sha)` uses commit-based change detection — no more mtime fragility
5. Existing git workflow (forage writes files, commits) works unchanged

## What Does NOT Change

- `CorpusSearch`, `FederatedCorpusSearch` — untouched
- `CorpusCatalogue` — SPI interface untouched. It registers corpora by type/lifecycle/federation — backend-agnostic. Backend configuration (mode, source path, ref) is handled by `CorpusStorageConfig` (in `rag-core/`), which IS updated to support git config properties.
- `EmbeddingIngestor` — SPI interface untouched. The call chain through which chunks reach it changes (via `IngestionPipeline` instead of inline in `CorpusIngestionService` and `SqliteCorpusEntryStore`), but the interface and its implementations are unchanged.
- `ProvenanceTracker` SPI and `ProvenanceStore` — decision provenance tracking is untouched. The new `AccessTrackingDecorator` is a separate concern (access provenance).
- Zip chain model — retained for high-volume use cases
- Git workflow operations — not wrapped; users use git directly

## Deferred Items

The following epic (#789) items are out of scope for this spec but are tracked:

| Item | Status | Notes |
|------|--------|-------|
| #787 Rich document ingestion | OPEN | L1's `Content` sealed hierarchy enables this; full PDF/Word/image support deferred |
| #788 Corpus annotations | OPEN | Independent of this spec |
| #804 Research workspace methodology | OPEN | Independent of this spec |
| L5 CorpusQuery text-only | No standalone issue | Needs a tracking issue filed under #789 |

## References

- `corpus-api/src/main/java/io/casehub/neocortex/corpus/` — current SPIs
- `corpus/src/main/java/io/casehub/neocortex/corpus/zip/` — current implementations
- `corpus-management-api/src/main/java/io/casehub/neocortex/corpus/management/` — management SPIs
- `rag-core/src/main/java/io/casehub/neocortex/rag/runtime/` — CorpusIngestionService, CorpusIngestionBinding, IngestionPipeline (new), IngestionContext (new)
- `rag/src/main/java/io/casehub/neocortex/rag/runtime/CorpusBindingProducer.java` — backend wiring (moving to corpus-quarkus)
- `docs/specs/2026-06-11-corpus-storage-module-design.md` — original corpus design (partially superseded)
- `docs/specs/2026-11-04-corpus-domain-abstraction-audit.md` — API leak audit
- casehubio/neocortex#789 — parent epic
- casehubio/neocortex#800 — Phase 1 renames (CLOSED)
- casehubio/neocortex#801 — Phase 2 API fixes (CLOSED)
- casehubio/neocortex#802 — Phase 3 structural extraction (OPEN)
