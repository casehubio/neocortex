# Decisions — Corpus Library Redesign (GitCorpusStore)

## D1: Corpus library as backend-agnostic coordination layer

**Choice:** The corpus library provides catalogue, search, provenance tracking, and change-driven ingestion over backend-agnostic content stores. It does not own content storage — backends implement CorpusStore/CorpusReader for their storage substrate. The library provides the coordination layer above: lifecycle management, metadata extraction, pipeline orchestration, and search integration.
**Alternatives:**
- Storage abstraction layer — wraps backends behind uniform read/write APIs. Too low-level; hides what matters (search, provenance) behind what doesn't (file I/O).
- "Methodology" framing — captures intent but has no testable architectural predicate. Two engineers will interpret it differently.
**Rationale:** This framing is testable: an ArchUnit rule can verify that coordination logic (catalogue, search, provenance, pipeline) lives in corpus-management, while storage concerns (read, write, change detection) live in corpus-api implementations. It preserves the insight that the library is NOT infrastructure wrapping while stating a concrete boundary.
**Trade-offs:** The coordination/storage boundary must be enforced — new features must be placed on the correct side.
**Sources:** User direction, corpus-management-api audit (2026-11-04), R1-04 review feedback
**Exploration:** quick
**Status:** revised — replaced vague "methodology" framing with testable architectural predicate per R1-04

## D2: Write API for programmatic and agent access

**Choice:** The corpus library exposes write operations via CorpusEntryStore (add, update, remove, get) for programmatic and MCP agent access. The write path delegates to the backend's CorpusStore for storage, then triggers the shared ingestion pipeline (D7) for immediate consistency. Content can also enter through the backend's native mechanism (git commit, file drop) and be detected asynchronously via ChangeSource.
**Alternatives:**
- No write API — content enters only through native backend mechanisms and ChangeSource detection. Fails the MCP use case: agents cannot "just write files and commit" — they need an API surface.
- CorpusStore.append() only — too low-level; no metadata, no catalogue integration, no pipeline triggering.
**Rationale:** Issue #811 is about giving LLM agents programmatic corpus access. The issue body lists "Entry operations — add, update, remove, get entries" as required MCP resolvers. Without a write API, the MCP domain has no write surface. CorpusEntryStore already provides this contract — the redesign retains it and routes it through the shared pipeline.
**Trade-offs:** Two write paths (programmatic via CorpusEntryStore, native via ChangeSource) must produce identical pipeline results. D7's shared pipeline guarantees this.
**Sources:** Issue #811 body ("add, update, remove, get entries"), existing CorpusEntryStore SPI, R1-01 review feedback
**Exploration:** quick
**Status:** revised — reversed "no write API" position; agents need a write surface, CorpusEntryStore provides it

## D3: Reads go through corpus layer for provenance

**Choice:** All agent reads go through the corpus layer so every access can be tracked — what was read, when, by whom. Provenance tracking is added to the read path via a CDI decorator on CorpusReader that emits CDI events on read, following the platform's existing decorator pattern (CRAG decorator, CBR tracking decorator). The MCP domain's read resolvers use the decorated path by default. Batch operations (indexing, migration) may use the undecorated path.
**Alternatives:**
- Direct file reads — simpler but loses provenance trail
- Provenance as a mandatory CorpusReader contract change (e.g. ReadContext parameter) — forces all implementations to handle provenance, including batch/migration paths that don't need it
**Rationale:** Without provenance, you lose the ability to track quality signals, staleness, and usage patterns. The corpus layer is the "checkout desk" — content lives in the backend, but the library knows what was read. The decorator approach is idiomatic for the platform (CDI decorators already provide cross-cutting tracking in CRAG and CBR) and avoids polluting the core CorpusReader SPI with tracking parameters.
**Trade-offs:** Agents must use the corpus read API rather than reading files directly. The decorator adds a layer of indirection. Batch paths that opt out of provenance must be explicit about it.
**Sources:** User correction ("you lose provenance"), existing CDI decorator patterns in rag (CRAG) and memory-cbr (CBR tracking), R1-03 review feedback
**Exploration:** quick
**Status:** revised — acknowledged current SPI has no provenance contract; specified CDI decorator mechanism per R1-03

## D4: Three backends — git, zip, and flat

**Choice:** Three corpus backends: git (via JGit, for curated knowledge with version history and ref-based access), zip (for high-volume distribution), and flat (zero-dependency filesystem baseline). FlatCorpusStore is retained for consumers that don't need git features — including non-git directories and git working trees where only filesystem access is needed. GitCorpusStore adds NEW capabilities (version history from commits, commit-level change detection, configurable ref) rather than replacing FlatCorpusStore.
**Alternatives:**
- Two backends (git + zip), FlatCorpusStore removed — "git subsumes flat." Wrong: git subsumes the storage substrate, not the access pattern. A git repo's working tree IS a flat directory. FlatCorpusStore handles it correctly with zero dependencies. JGit adds value for version history and ref-based access, but those are new capabilities, not a replacement for filesystem access.
- Git only — but zip handles the scale case where millions of entries degrade filesystem performance
**Rationale:** FlatCorpusStore has distinct value: (1) zero external dependencies — no JGit 5MB; (2) simplest onboarding — point at a directory, get change detection and RAG; (3) used in production by CorpusBindingProducer as the default FLAT mode; (4) serves any filesystem-backed corpus regardless of git status. GitCorpusStore serves the NEW use case of reading from arbitrary refs and getting commit-level change detection.
**Trade-offs:** Three backend implementations to maintain. The git/flat boundary needs clear guidance: use flat for working-tree access, git for object-level access from refs/history. The three backends are NOT symmetric in write semantics: (1) Flat — `FlatCorpusStore.append()` writes a file to the filesystem, immediately visible to CorpusReader and ChangeSource. (2) Zip — `ZipCorpusStore.append()` writes to the zip archive, immediately visible. (3) Git — `GitCorpusStore.append()` must write to the working tree AND auto-commit to the configured ref (D6), because the git CorpusReader reads from refs, not the working tree. This serializes concurrent writes (JGit's `.git/index` lock) and creates one commit per programmatic write. This is acceptable for curated corpora where write frequency is moderate — the audit explicitly concludes that high-frequency agent-driven workflows (research workspaces) need a separate abstraction rather than stretching corpus-management. Commit message policy, author identity, and batching are implementation details of GitCorpusStore, not architectural decisions.
**Sources:** Verified FlatCorpusStore is the default mode in CorpusBindingProducer (line ~124), R1-02 review feedback, R2-02 review feedback
**Exploration:** quick
**Status:** revised — retained FlatCorpusStore; acknowledged git write asymmetry per R2-02

## D5: JGit for git operations

**Choice:** JGit (pure Java) for all git operations in the git backend.
**Alternatives:**
- Git CLI — shells out to git commands. Lighter but requires git installed, process management overhead, platform-specific behavior.
- Both with fallback — more code, marginal benefit
- Start with CLI, migrate to JGit if needed — adds a migration step for operations we already know we need
**Rationale:** Pure Java, no system dependency. Works in any JVM deployment. Apache-2.0. Consistent cross-platform behavior. The operations needed are defined by D6 and D7: reading files from arbitrary refs (tree resolution), detecting changes between commits (diff walking), listing files in a tree, resolving branch/tag refs. These are core JGit territory — the CLI equivalent would be multiple process invocations per operation with output parsing. JGit provides a coherent API, an in-process test infrastructure (TestRepository), and avoids process lifecycle management.
**Trade-offs:** Larger dependency (~5MB). JGit API is more verbose than CLI for simple operations. The dependency is isolated in the git backend module — flat and zip consumers don't pull it.
**Sources:** Standard practice for JVM git integration, operations defined by D6 (ref resolution) and D7 (change detection)
**Exploration:** quick
**Status:** captured

## D6: Configurable ref per corpus

**Choice:** Each git-backed corpus specifies which branch/ref to index (default: HEAD). Different corpora can track different branches. This implies object-level access: the git backend reads from a specific ref's tree via JGit, not the working tree filesystem. Working-tree access is the flat backend's domain (D4).
**Alternatives:**
- Always HEAD — simplest but branch switch changes what's indexed
- Always a named branch like 'main' — predictable but can't index draft content
**Rationale:** Lets different corpora serve different lifecycle stages (main for published, draft for WIP) without the library managing branches itself. Ref configuration maps to JGit tree resolution — reading file content from the specified ref's tree object without requiring a checked-out working copy.
**Trade-offs:** Configuration complexity — users must understand which ref they're indexing. Requires JGit (D5) for object-level access.
**Sources:** User direction on git operations staying direct
**Exploration:** quick
**Status:** captured

## D7: Converge two Qdrant paths via shared IngestionPipeline

**Choice:** The two existing paths to Qdrant converge into a shared IngestionPipeline that handles metadata extraction, chunking, and embedding. CorpusIngestionService (ChangeSource-driven batch) and CorpusEntryStore.add() (programmatic) both invoke the shared pipeline. The programmatic path (CorpusEntryStore) runs the pipeline synchronously for immediate consistency; the ChangeSource path runs it on poll/watch for batch efficiency.
**Alternatives:**
- Keep both paths — duplicated logic, inconsistent provenance tracking, inconsistent metadata extraction
- Drop corpus-management path — but it serves the MCP programmatic use case (D2)
- Converge at ChangeSource level only — forces all writes through eventual-consistency detection, unacceptable for interactive agent workflows
**Rationale:** Duplicate paths mean duplicate bugs, inconsistent metadata extraction, and gaps in provenance tracking. SqliteCorpusEntryStore.add() (line ~62) directly calls EmbeddingIngestor.ingest(), completely bypassing metadata extraction and the pipeline. The shared IngestionPipeline extracts the common processing (MetadataExtractor → TextChunker → EmbeddingIngestor) into a reusable unit that both paths invoke. The two paths feed content to the pipeline differently: in the programmatic path (CorpusEntryStore.add()), content flows directly from the method call to the pipeline — the pipeline does NOT re-read content from the backend via CorpusReader. Backend persistence (e.g., git auto-commit per D4) happens for durability but does not gate pipeline processing. In the ChangeSource path (CorpusIngestionService), the pipeline reads content from CorpusReader because the content is already committed to the backend by the time change detection occurs.
**Trade-offs:** The shared pipeline must be designed as a composable unit that CorpusIngestionService and CorpusEntryStore implementations can both call. CorpusEntryStore.add()'s current direct-to-Qdrant shortcut is removed. The pipeline must accept content both ways: directly (programmatic) and via CorpusReader (ChangeSource).
**Sources:** Verified in code: SqliteCorpusEntryStore bypasses ChangeSource entirely, R1-06 review feedback
**Exploration:** quick
**Status:** revised — specified convergence mechanism, consistency model, and dual content-flow paths per R2-02

## D8: corpus-api and corpus-management-api module boundary preserved

**Choice:** corpus-api (L8, zero casehub domain dependencies, Hortora-eligible) and corpus-management-api remain separate modules. The 6 API-layer leaks identified in the audit (L1–L6) are remediated as part of this redesign: garden terminology, markdown assumptions, and Hortora-specific constants are moved out of the API SPIs into Hortora-specific implementation modules.
**Alternatives:**
- Merge into a single corpus-api — loses the zero-dependency property of the storage SPI layer; forces all consumers to pull catalogue/search/lifecycle dependencies
- Leave leaks unaddressed — MCP domain (#811) inherits garden terminology and markdown assumptions in its agent-facing API
**Rationale:** The two-tier architecture identified in the audit is sound: Tier 1 (corpus-api) provides backend-agnostic storage SPIs, Tier 2 (corpus-management-api) provides coordination SPIs. The leaks blur this boundary. Remediating them before the MCP domain ensures agents get a clean, domain-neutral API. Key remediation items: L1 (CorpusEntry.body String→Content), L2 (garden constants→Hortora module), L3 (agent interaction types→transcript module), L6 (frontmatter→metadata rename).
**Trade-offs:** Remediation touches the public API surface — consumers must update to renamed/restructured types.
**Sources:** Corpus-management-api audit (2026-11-04), leaks L1–L6, R1-08 review feedback
**Exploration:** surfaced by reviewer
**Status:** captured

## D9: CorpusBindingProducer moves to corpus-quarkus module

**Choice:** CorpusBindingProducer (currently in the rag module) moves to a dedicated corpus-quarkus module that owns CDI wiring and backend selection. The rag module should not import corpus implementation types.
**Alternatives:**
- Leave in rag module — adding a git backend means rag also imports JGit, violating separation of concerns
- Inline wiring in each backend module — duplicates CDI configuration across git, zip, and flat modules
**Rationale:** CorpusBindingProducer (rag/runtime/CorpusBindingProducer.java) is the only class in rag that imports FlatCorpusStore, ZipCorpusStore, and CompositeCorpusStore directly (as its own javadoc acknowledges). Adding GitCorpusStore would add another implementation dependency. A dedicated wiring module isolates backend selection and CDI binding from the RAG pipeline.
**Trade-offs:** New module to maintain. CorpusBindingProducer's configuration-driven backend selection must be refactored to use SPI discovery or a registry pattern.
**Sources:** Corpus-management-api audit (2026-11-04) identified this debt, CorpusBindingProducer javadoc, R1-09 review feedback
**Exploration:** surfaced by reviewer
**Status:** captured

## D10: SQLite coexists with content backends for metadata

**Choice:** SQLite stores (SqliteCorpusCatalogue, SqliteCorpusEntryStore, ProvenanceStore) are retained for catalogue metadata, entry management, and provenance regardless of the content backend. Git stores content; SQLite stores the management layer above it. They serve orthogonal concerns.
**Alternatives:**
- Git-native metadata (manifest files in the repo, git notes for provenance) — ties metadata format to git; breaks for zip and flat backends; loses SQL query capability for catalogue operations
- Replace SQLite with git for everything — inappropriate; git is a content-addressed store, not a query engine
**Rationale:** CorpusDescriptor (catalogue), CorpusEntry metadata, and provenance records need structured queries (list by type, search by field, lineage traversal). SQLite provides this. Content (document bodies) needs versioned storage, change detection, and ref-based access. Git provides this. The two are complementary, not competitive.
**Trade-offs:** Consistency between git content and SQLite metadata must be maintained — the shared IngestionPipeline (D7) is the synchronization point.
**Sources:** Existing architecture, R1-10 review feedback
**Exploration:** surfaced by reviewer
**Status:** captured

## D11: Rename CorpusStore.append() to write()

**Choice:** Rename `CorpusStore.append()` to `write()`. Backend-neutral terminology that works for git (file write + commit), zip (archive append), and flat (filesystem write).
**Alternatives:**
- Keep `append` — zip-centric vocabulary; misleading for git/flat where writes overwrite rather than append versions
- Separate storage SPIs per backend type (ArchiveStore vs FileStore) — the actual write operations are the same (put content at path, remove content at path); splitting creates false distinction
**Rationale:** `write` accurately describes what all three backends do. Directory creation, versioning, and commit semantics are implementation details behind the shared interface.
**Trade-offs:** Rename touches all CorpusStore consumers. Mechanical migration.
**Sources:** User observation that "append is just wrong for git"
**Exploration:** quick
**Status:** captured
