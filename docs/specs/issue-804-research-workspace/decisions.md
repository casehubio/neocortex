## D1: Workspace SPI Shape

**Choice:** Single `ResearchWorkspace` interface with all five core operations (store, search, coverage, stale, enrich) plus promotion.
**Alternatives:**
- ISP decomposition (5 small SPIs) — over-fragments the tool surface for MCP exposure; LLM gets a cleaner contract with a single interface
- Extend existing SPIs (CacheStore, SearchableProvider) — stretches those SPIs beyond their purpose, muddles domain boundaries
**Rationale:** The issue describes a cohesive tool contract for LLM research. One interface maps 1:1 to that contract. Internal implementation can still delegate to CacheStore, EmbeddingIngestor, etc.
**Trade-offs:** Larger interface surface — but it's an SPI with a single runtime implementation, not a CDI bean with multiple consumers.
**Sources:** Issue #804 tool contract description, knowledge-pipeline-api existing SPIs
**Exploration:** quick
**Status:** captured

## D2: Content Model

**Choice:** `Content` sealed interface hierarchy with `TextContent`, `BinaryContent`, `StructuredContent` variants, each carrying `mediaType()`.
**Alternatives:**
- String body + byte[] attachment — simpler but loses type safety and pattern matching
- URI reference to CorpusStore — separates metadata from content but adds indirection and doesn't let the workspace manage content lifecycle
**Rationale:** Research workspaces hold heterogeneous content (HTML, PDF, screenshots, JSON). A sealed hierarchy enables exhaustive pattern matching and aligns with the audit doc's L1 remediation direction for CorpusEntry.
**Trade-offs:** More complex than a simple String body. The `Content` type could be shared with corpus-management-api later (L1 fix), but that's a separate issue.
**Sources:** Issue #804, audit doc L1 (CorpusEntry.body is String), CachedEntity properties model
**Exploration:** quick
**Status:** captured

## D3: Coverage Tracking

**Choice:** Dynamic category sets — categories defined per-workspace as `Set<String>`, entries tagged with categories they cover, `CoverageReport` shows per-category entry count and uncovered categories.
**Alternatives:**
- Structured coverage schema (sub-dimensions, thresholds, quality requirements) — over-engineering for a feature whose value is LLM interpretability
- Implicit coverage from search patterns — opaque and hard for LLMs to reason about
**Rationale:** Simple, flexible, LLM-interpretable. The LLM defines what dimensions to cover up-front, tags entries as it goes, and queries gaps. The platform enforces nothing — it reports completeness and lets the LLM decide.
**Trade-offs:** No quality assessment — a category with 1 low-quality entry shows as "covered." Good enough for v1; quality scoring can layer on later.
**Sources:** Issue #804 coverage description, research conventions section
**Exploration:** quick
**Status:** captured

## D4: Enrichment Model

**Choice:** Enrichment as entry linkage — analysis creates a NEW WorkspaceEntry with `derivedFrom` references to source entry IDs. The enrichment chain is a DAG.
**Alternatives:**
- Inline annotations on entries — analysis becomes metadata on sources, not first-class searchable content
- Separate enrichment store — clean separation but adds another persistence layer
**Rationale:** Each level of analysis (raw → extracted → compared → synthesized) is independently searchable and promotable. The `derivedFrom` DAG makes the provenance chain traceable. Aligns with the audit doc's product-research lens (derivation chain tracking).
**Trade-offs:** More entries in the workspace. But entries are cheap, and the alternative (annotations) makes analysis invisible to search.
**Sources:** Issue #804 enrichment description, audit doc product-research lens
**Exploration:** quick
**Status:** captured

## D5: Freshness Model

**Choice:** Per-category freshness policy — workspace defines `Duration` per category with a workspace-level default. `stale()` returns entries past their category's threshold. Multi-category entries use shortest duration.
**Alternatives:**
- Per-entry freshness — maximum flexibility but forces the LLM to set freshness on every store() call
- Lifecycle states (DRAFT/CURRENT/STALE/SUPERSEDED) — more expressive but requires transition logic
**Rationale:** Declarative and LLM-interpretable. The LLM sets policy once at workspace creation; the platform enforces it on every `stale()` call. Different research domains naturally have different freshness expectations.
**Trade-offs:** Policy is per-category, not per-entry. An entry can't have a custom freshness that differs from its category. Acceptable for v1.
**Sources:** Issue #804 freshness policy section, audit doc market-research lens
**Exploration:** quick
**Status:** captured

## D6: Source Reliability

**Choice:** `ReliabilityTier` enum (AUTHORITATIVE, OFFICIAL, CURATED, COMMUNITY, UNKNOWN) as a field on `WorkspaceEntry`. LLM assigns on `store()`. Default UNKNOWN.
**Alternatives:**
- Defer to v2 — saves complexity now but expensive to retrofit (every entry needs backfilling)
- Numeric confidence [0,1] — more granular but LLMs assign inconsistently
**Rationale:** Cheap to add now (one enum field), expensive to retrofit. Search can filter or boost by tier. The enum values are semantically clear for LLMs.
**Trade-offs:** Fixed tier list may not fit all domains. But consumers can ignore it (UNKNOWN default), and the enum can grow.
**Sources:** Issue #804 source reliability section
**Exploration:** quick
**Status:** captured

## D7: ContentAcquisition SPI

**Choice:** `ContentAcquisition` @FunctionalInterface — `acquire(URI, AcquisitionOptions) → CapturedContent`. Adapters are CDI beans selected by URI scheme or media type. `CapturedContent` bundles content, optional screenshot, extracted text, source URI, capture timestamp, and HTTP metadata.
**Alternatives:**
- Integrated into ResearchWorkspace (fetch+store in one call) — couples acquisition to workspace, prevents standalone use
- Defer SPI — delays the adapter contract, makes the site capture issue harder to scope
**Rationale:** Clean separation: acquisition is a standalone capability that the workspace (and other consumers) can use. The @FunctionalInterface keeps it simple — one method, one responsibility.
**Trade-offs:** The LLM needs two calls (acquire + store) instead of one. But this is an internal SPI, not a tool contract — the MCP tool can compose both calls.
**Sources:** Issue #804 site capture section, DomainSupport/SearchableProvider patterns in knowledge-pipeline-api
**Exploration:** quick
**Status:** captured
