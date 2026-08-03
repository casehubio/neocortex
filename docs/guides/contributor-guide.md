# casehub-neocortex — Contributor Guide

> Internals, architecture, and extension points for neural text inference, RAG, and agent memory.

**GitHub:** [casehubio/neocortex](https://github.com/casehubio/neocortex)

---

## Module Structure

### Inference Modules

| Module | artifactId | Type | Purpose |
|--------|-----------|------|---------|
| `inference-api/` | `casehub-neocortex-inference-api` | Pure Java, zero deps | `InferenceModel` SPI, `InferenceInput`, `InferenceOutput`, `InferenceException`; `MultiModalEmbedder` interface (dense+sparse+ColBERT); `MultiModalEmbedding` value type; `EmbeddingMode` enum (DENSE, SPARSE, COLBERT) |
| `inference-runtime/` | `casehub-neocortex-inference-runtime` | JVM library | ONNX Runtime JVM + HuggingFace Tokenizers JNI; `OnnxInferenceModel`, `ModelConfig`, `ModelLoadException`; session management, tokenization |
| `inference-tasks/` | `casehub-neocortex-inference-tasks` | JVM library | `NliClassifier`, `TextClassifier`, `ScalarRegressor`, `CrossEncoderReranker` |
| `inference-splade/` | `casehub-neocortex-inference-splade` | JVM library | SPLADE sparse embeddings (`Map<Integer, Float>`); log-saturation + threshold |
| `inference-bge-m3/` | `casehub-neocortex-inference-bge-m3` | JVM library | `BgeM3Embedder` implements `MultiModalEmbedder` — produces dense (1024-dim), sparse (ReLU+threshold), and ColBERT multi-vector embeddings from a single ONNX `InferenceModel` run. Depends only on inference-api. |
| `inference-inmem/` | `casehub-neocortex-inference-inmem` | Test library | Deterministic `InferenceModel` stubs; no JNI; safe in all test contexts |
| `inference-quarkus/` | `casehub-neocortex-inference-quarkus` | Quarkus extension | CDI wiring, `@InferenceModel` qualifier, Dev Services, `@QuarkusTest` support |

### Fusion Module

| Module | artifactId | Type | Purpose |
|--------|-----------|------|---------|
| `fusion-api/` | `casehub-neocortex-fusion-api` | Pure Java, zero deps | `FusionStrategy` enum (RRF, DBSF, CC); `ScoreFusion` utility with `ScoredLeg`, `FusedResult` records — implements RRF and Convex Combination algorithms; `CamelCaseExpander` for BM25 text preprocessing |

### RAG Modules

| Module | artifactId | Type | Purpose |
|--------|-----------|------|---------|
| `rag-api/` | `casehub-neocortex-rag-api` | Pure Java, Mutiny provided | `EmbeddingIngestor` SPI, `CaseRetriever` SPI (blocking); `ReactiveEmbeddingIngestor`, `ReactiveCaseRetriever` (Mutiny `Uni<T>` variants); `RetrievedChunk`, `CorpusRef`; `MetadataExtractor` SPI; `CursorStore` SPI; `RetrievalTracker` SPI — records retrieval events + feedback for usefulness measurement |
| `rag/` | `casehub-neocortex-rag` | Quarkus module | LangChain4j pipeline, Qdrant, three-leg hybrid search (dense + sparse + BM25) with configurable fusion; tenancy isolation; `MatryoshkaEmbeddingModel` (truncating decorator); `DenseQuantization` (binary/scalar for dense and ColBERT vectors); `BM25Index` + `BM25IndexRegistry` (in-memory BM25 with `CodeDomainTokenizer`); `ConvexCombinationFusion` (client-side CC with per-leg weights); `RagBeanProducer` / `ReactiveRagBeanProducer` CDI wiring; `CorpusIngestionService` polling bridge; ColBERT multi-vector quantization config |
| `rag-testing/` | `casehub-neocortex-rag-testing` | Test library | In-memory `EmbeddingIngestor` + `CaseRetriever` + reactive stubs for `@QuarkusTest`; `InMemoryCursorStore @Alternative @Priority(1)` test stub |
| `rag-tika/` | `casehub-neocortex-rag-tika` | JVM library | Apache Tika document parser — `TikaDocumentParser` extracts text + metadata from binary documents (PDF, DOCX, etc.) for RAG ingestion |
| `rag-crossencoder/` | `casehub-neocortex-rag-crossencoder` | JVM library | Cross-encoder powered RAG: corrective retrieval (`CorrectiveCaseRetriever` CDI Decorator Priority 100, grades chunks as correct/ambiguous/incorrect) + reranking (`RerankingCaseRetriever` Decorator Priority 75, cross-encoder score re-ordering). Reactive variants for both. Config-gated: `casehub.rag.crag.enabled`, `casehub.rag.reranking.enabled` |
| `rag-expansion/` | `casehub-neocortex-rag-expansion` | JVM library | Query expansion — `QueryExpandingCaseRetriever` + reactive variant. Expanders: `LlmQueryExpander`, `TemplateQueryExpander`, `StepBackQueryExpander`. Multi-query fan-out with RRF fusion. `ExpansionConfig` for tuning |
| `rag-tracking/` | `casehub-neocortex-rag-tracking` | JVM library | SQLite-backed retrieval tracking. `TrackingCaseRetriever` (Decorator Priority 50) stamps chunks with retrieval ID, records queries/results/scores. `SqliteRetrievalTracker` with HikariCP + Flyway. `RetentionScheduler` for trace purging. CDI events: `RetrievalRecorded`. Config-gated: `casehub.rag.tracking.enabled` |

### Corpus Modules

| Module | artifactId | Type | Purpose |
|--------|-----------|------|---------|
| `corpus-api/` | `casehub-neocortex-corpus-api` | Pure Java, Mutiny provided | Corpus storage and change-tracking SPIs — `CorpusStore`, `CorpusReader`, `ChangeSource`, `WatchableChangeSource`, `ChangeListener`; reactive mirrors; `CorpusIntegrity` SPI for health checks |
| `corpus/` | `casehub-neocortex-corpus` | JVM library | Corpus storage implementations — `ZipCorpusStore`, `FlatCorpusStore`, `CompositeCorpusStore`; change tracking; `Compactor`; `CorpusMigrator`; `ZipIntegrityChecker`; blocking-to-reactive bridges |

### Memory Modules

| Module | artifactId | Type | Purpose |
|--------|-----------|------|---------|
| `memory-api/` | `casehub-neocortex-memory-api` | Pure Java | `CaseMemoryStore`, `ReactiveCaseMemoryStore`, `GraphCaseMemoryStore` SPIs; `CbrCaseMemoryStore` with supersession SPI; CBR typed feature values (`FeatureValue` sealed), field schema (`FeatureField` sealed), similarity specs (`SimilaritySpec` sealed); `CbrSimilarityScorer`; trend detection; plan adaptation; active memory (temporal decay) |
| `memory/` | `casehub-neocortex-memory` | CDI module | `NoOpCaseMemoryStore @DefaultBean`, `BlockingToReactiveBridge`, `CaseEnrichmentDecorator`; `MemoryEmitter` fire-and-forget wrapper; `TrendEnrichmentCbrCaseMemoryStore` (Decorator Priority 90); `TemporalDecayCbrCaseMemoryStore` (Decorator Priority 80) |
| `memory-inmem/` | `casehub-neocortex-memory-inmem` | Backend | @Alternative @Priority(1) volatile ConcurrentHashMap — test-scope for isolation; compile for ephemeral |
| `memory-jpa/` | `casehub-neocortex-memory-jpa` | Backend | @ApplicationScoped JPA/PostgreSQL + Flyway + FTS via websearch_to_tsquery |
| `memory-sqlite/` | `casehub-neocortex-memory-sqlite` | Backend | @Alternative @Priority(1) SQLite + HikariCP WAL + FTS5 |
| `memory-mem0/` | `casehub-neocortex-memory-mem0` | Backend | @Alternative @Priority(1) Mem0 REST adapter — vector embeddings + semantic search |
| `memory-graphiti/` | `casehub-neocortex-memory-graphiti` | Backend | @Alternative @Priority(2) Graphiti REST GraphCaseMemoryStore — temporal knowledge graph |
| `memory-qdrant/` | `casehub-neocortex-memory-qdrant` | Backend | Qdrant vector store backend; `CbrReconciliationService` — three-phase reconciliation with Micrometer metrics |
| `memory-cbr-inmem/` | `casehub-neocortex-memory-cbr-inmem` | Backend | In-memory CBR case memory store |
| `memory-cbr-embedding/` | `casehub-neocortex-memory-cbr-embedding` | Backend | `EmbeddingTextSimilarity` — LangChain4j `EmbeddingModel`-based semantic text similarity for CBR feature fields. Caches embeddings, computes cosine similarity between `StringVal` values |
| `memory-cbr-crossencoder/` | `casehub-neocortex-memory-cbr-crossencoder` | Backend | `RerankingCbrCaseMemoryStore` (Decorator Priority 75) — cross-encoder reranking for CBR retrieval. Sigmoid normalization. Config-gated: `casehub.cbr.reranking.enabled` |
| `memory-cbr-tracking/` | `casehub-neocortex-memory-cbr-tracking` | Backend | SQLite-backed CBR retrieval tracking. `TrackingCbrCaseMemoryStore` (Decorator Priority 50). `TrackingPlanAdapter` for plan adaptation traces. CDI events: `CbrRetrievalRecorded`, `CbrAdaptationRecorded` |
| `memory-cbr-jpa/` | `casehub-neocortex-memory-cbr-jpa` | Backend | JPA/PostgreSQL CBR store (@Alternative Priority 3). `CbrCaseEntity` with JSONB features, plan traces, outcome tracking, supersession metadata |
| `memory-testing/` | `casehub-neocortex-memory-testing` | Test library | Test stubs for memory SPIs |

### Examples

| Module | Type | Purpose |
|--------|------|---------|
| `examples/example-text-analysis` | Standalone Java | NLI, zero-shot classification, scoring, reranking, SPLADE demos (no Quarkus) |
| `examples/example-rag-pipeline` | Quarkus demos | Corpus ingestion, hybrid search with RRF fusion, cross-encoder reranking. Maven profiles: `-Pexamples-smoke` (in-memory stubs), `-Pexamples` (real ONNX models + Testcontainers Qdrant) |
| `examples/example-cbr` | Quarkus demos | Six-domain CBR demo: AML investigation, clinical adverse events, PR code review, life insurance contractor assessment, IoT situations, game battle strategy |

---

## Internal Architecture

### DenseQuantization

Enum in `rag/` with values `NONE`, `BINARY`, `SCALAR`. Configures Qdrant quantization on the **dense vector params** at collection creation time — applied to `denseParamsBuilder` specifically, not to the entire collection (sparse vectors are not quantized). `BINARY` applies `BinaryQuantization`; `SCALAR` applies `ScalarQuantization` with `Int8` type. Both respect `casehub.rag.quantization.always-ram` (default `true`). Config: `casehub.rag.quantization.type` (default `NONE`).

Named `DenseQuantization` rather than `QuantizationType` because the Qdrant client already defines `io.qdrant.client.grpc.Collections.QuantizationType` — both enums appear in `ensureCollection()` / `buildCreateRequest()` and sharing the name would create ambiguous unqualified usage. See [ARC42STORIES.MD section 8](https://github.com/casehubio/neocortex/blob/main/ARC42STORIES.MD#8-crosscutting-concepts).

### HybridCaseRetriever Oversampling

`HybridCaseRetriever` (and `ReactiveHybridCaseRetriever`) accept `DenseQuantization` type and optional oversampling. When quantization is active (`DenseQuantization != NONE`) and `casehub.rag.quantization.oversampling` is set, the dense prefetch leg applies `QuantizationSearchParams` with the configured oversampling factor + `rescore=true`. Compensates for quantization precision loss by fetching more candidates from the quantized index before rescoring against full-precision vectors. Sparse prefetch is unaffected — sparse vectors are not quantized. See [ARC42STORIES.MD section 6](https://github.com/casehubio/neocortex/blob/main/ARC42STORIES.MD#6-runtime-view) for the oversampling design rationale.

### Per-Leg Embedding Separation

Dense leg uses `RetrievalQuery.searchText()` (optimized for search), sparse and ColBERT legs use `text()` (full original query). Enables query reformulation for dense retrieval while preserving term-level signals for sparse matching. Introduced in neocortex#113.

### BM25 as Third Retrieval Leg

`BM25Index` — thread-safe in-memory inverted index with `CodeDomainTokenizer` for camelCase/code-aware tokenization. Standard BM25 scoring (k1=1.2, b=0.75). `BM25IndexRegistry` manages per-corpus indexes. Three-leg hybrid search: dense + sparse + BM25, fused via configurable `FusionStrategy`. Default CC weights: dense=0.5, sparse=0.3, bm25=0.2. Config: `casehub.rag.bm25.enabled` (default true).

### Cross-Encoder Reranking and Corrective RAG

Replaces the former `rag-crag` module. Two CDI decorator chains in `rag-crossencoder/`:

- **Corrective retrieval** (`CorrectiveCaseRetriever`, Priority 100) — grades chunks as correct/ambiguous/incorrect, filters before LLM injection
- **Reranking** (`RerankingCaseRetriever`, Priority 75) — cross-encoder score re-ordering

Both config-gated. Shared `CrossEncoderBeanProducer`.

### Retrieval Tracking

SQLite-backed retrieval tracking in `rag-tracking/` with frequency, outcome, and feedback measurement. `TrackingCaseRetriever` (Decorator Priority 50) stamps chunks with retrieval ID. `SqliteRetrievalTracker` with HikariCP + Flyway migrations. `RetentionScheduler` for trace purging. CDI events: `RetrievalRecorded`.

### CBR Typed Feature Values and Similarity

**Feature values:** `FeatureValue` sealed interface with seven value types: `StringVal`, `NumberVal`, `RangeVal`, `StringListVal`, `NumberListVal`, `StructVal`, `StructListVal`. Booleans coerced to `StringVal` via `FeatureValue.of(Object)`.

**Feature field schema:** `FeatureField` sealed interface with nine permits: `Categorical`, `Numeric`, `Text`, `CategoricalList`, `NumericList` (with min/max bounds), `NestedObject`, `ObjectList`, `TimeSeries` (compound with inner fields, timestamp, optional `DtwSpec` + `TrendSpec`), `DiscreteSequence` (ordered categorical sequences with `EditDistanceSpec`).

**Similarity specs:** `SimilaritySpec` sealed interface replaces lambdas on records:
- `CategoricalTable` — lookup table with auto-mirroring and `CategoricalTableBuilder`
- `GaussianDecay(sigma)`, `StepDecay(tolerance)`, `ExponentialDecay(decayRate)` — for Numeric fields
- `DtwSpec(WarpingConstraint)` — Dynamic Time Warping for TimeSeries fields
- `EditDistanceSpec(substitutions, insertCost, deleteCost)` — for DiscreteSequence fields

`CbrSimilarityScorer` in `memory-api/cbr/` — pure-Java per-field similarity with configurable per-field weights. `CbrQuery` carries `weights`, `vectorWeight`, and per-field `SimilaritySpec` overrides. Exhaustive switches at all dispatch sites.

### CBR Plan Adaptation

`PlanAdapter` SPI — transforms retrieved plans for new case contexts. `adapt(ScoredCbrCase<PlanCbrCase>, Map<String, FeatureValue>)` returns `AdaptedPlan` (wrapping `List<AdaptedStep>`). `AdaptationTrace` record for tracking. Decoratable via `TrackingPlanAdapter` in `memory-cbr-tracking`.

### CBR Active Memory Management

**Temporal decay:** `TemporalDecay` sealed interface with three implementations: `HalfLife(Duration)` (exponential), `Linear(Duration zeroAt)`, `Step(Duration cutoff, double afterCutoff)`. `TemporalDecayCbrCaseMemoryStore` (Decorator Priority 80) applies decay to retrieval scores based on case `storedAt`.

**Supersession:** `CbrCaseMemoryStore` includes `supersede(caseId, tenantId, supersedingCaseId, reason)` and `reinstate(caseId, tenantId)`. JPA store filters `WHERE supersededAt IS NULL` on retrieval. All decorator stores delegate supersession calls.

### Trend Detection

`TrendSpec` — record holding `Set<TrendType>` and `ChronoUnit`, attached optionally to `FeatureField.TimeSeries`. `TrendType` enum: SLOPE, DELTA, VOLATILITY, ACCELERATION, CHANGE_POINTS, DURATION, OBSERVATION_COUNT. `TrendAnalyzer` — static utility computing trend metrics (linear regression, CUSUM change-point detection). `TrendEnrichmentCbrCaseMemoryStore` (Decorator Priority 90) auto-enriches features with trend metrics on store/retrieve.

### CBR Reconciliation

`CbrReconciliationService` in `memory-qdrant/` — `@ApplicationScoped` three-phase reconciliation:
1. Paginated SCAN of delegate store
2. Orphan cleanup + consistency marking in Qdrant
3. Batch reindex of missing entries (pages of 100) + vector enrichment backfill (SPLADE/BM25 vectors on existing points)

Supports `reconcile(caseType, tenantId)`, `reconcileAll(caseType)`, `discoverTenants(caseType)`. Micrometer metrics for orphans/reindexed/enriched/errors.

### OnnxInferenceModel Input Name Alias Resolution

Static alias table + `ModelConfig` overrides for input tensor names in `inference-runtime/`. Handles models with non-standard input names transparently. Introduced in neocortex#104.

### SparseEmbedder Rank-3 Max-Pool Reduction

Rank-3 output tensors from SPLADE models are reduced via max-pool across the sequence dimension before log-saturation in `inference-splade/`. Handles models that output per-token weights instead of per-vocab weights. Introduced in neocortex#104.

---

## Dependencies

### Depends On

| Repo / Library | Module | How |
|---|---|---|
| `casehub-platform-api` | `rag` | `CurrentPrincipal`, `TenancyConstants` — tenant isolation |
| LangChain4j (full artifact) | `rag`, `memory-cbr-embedding` | RAG pipeline, `OnnxEmbeddingModel`, Qdrant `EmbeddingStore`, `DocumentSplitters`, `EmbeddingModel` for CBR semantic text similarity |
| `io.qdrant:client` | `rag`, `memory-qdrant` | Qdrant REST client for hybrid search + CBR reconciliation |
| `quarkus-scheduler` | `rag`, `rag-tracking`, `memory-cbr-tracking` | `@Scheduled` polling and retention scheduling |
| `casehub-neocortex-corpus` | `rag` | `CorpusBindingProducer` only (design debt — extract when second consumer appears) |
| HikariCP | `rag-tracking`, `memory-cbr-tracking` | SQLite connection pooling for tracking stores |
| Flyway | `memory-jpa`, `memory-cbr-jpa`, `rag-tracking`, `memory-cbr-tracking` | Schema migrations |
| ONNX Runtime JVM | `inference-runtime` | Model session management |
| HuggingFace Tokenizers JNI | `inference-runtime` | Tokenization |

### Depended On By

| Repo | Module | How |
|---|---|---|
| `casehub-eidos` | `runtime` | `ScalarRegressor` for dynamic epistemic confidence |
| `casehub-openclaw` | `casehub` | `TextClassifier` for `ActionRiskClassifier` SPI |
| `casehub-engine` | `runtime` | `NliClassifier` for hallucination detection (#154) |
| `casehub-engine` | `runtime` | `CaseRetriever` for fact space prompt compilation |

---

## Current State

All inference, RAG, and CBR modules shipped. Active development on CBR memory management and retrieval optimization.

| Area | What shipped |
|------|-------------|
| Inference Foundation | `InferenceModel` SPI, ONNX runtime, task adapters (NLI, classification, regression, reranking), SPLADE sparse embeddings, BGE-M3 multi-modal embeddings (dense+sparse+ColBERT), Quarkus CDI extension |
| RAG Pipeline | Three-leg hybrid search (dense + sparse + BM25) with configurable fusion (RRF/DBSF/CC); `MatryoshkaEmbeddingModel` truncating decorator; `DenseQuantization` (binary/scalar for dense and ColBERT); ColBERT multi-vector scalar quantization; per-leg embedding separation; corrective RAG + cross-encoder reranking (`rag-crossencoder`); query expansion (LLM, template, step-back); retrieval tracking (`rag-tracking`) |
| CBR | Typed feature values (9 field types, 7 value types); `SimilaritySpec` sealed interface (6 similarity functions incl. DTW + edit distance); weighted per-field similarity scoring; plan adaptation SPI; temporal decay (3 strategies); supersession SPI; trend detection + enrichment; cross-encoder reranking for CBR; embedding-based text similarity; reconciliation with Qdrant; JPA/PostgreSQL backend; retrieval tracking |
| Agent Memory | Five backends (in-memory, JPA, SQLite, Mem0, Graphiti, Qdrant); `MemoryEmitter` fire-and-forget wrapper; permission-aware queries |
| Corpus | Append-only zip archives, flat filesystem, composite multi-backend; change tracking; compaction; integrity checks |
| Score Fusion | `fusion-api` tier-1 module — RRF + CC algorithms, `CamelCaseExpander` for BM25 preprocessing |

Native image gate passed. Service deploys in JVM mode by design — long-running workloads benefit from HotSpot JIT over AOT. Reachability metadata retained for downstream native consumers.

---

## Design Documents

- [casehubio/parent#158](https://github.com/casehubio/parent/issues/158) — casehubio/neocortex tracking issue
- [casehubio/parent#164](https://github.com/casehubio/parent/issues/164) — casehub-neocortex-rag tracking issue
- [Hortora/spec#15](https://github.com/Hortora/spec/issues/15) — Hortora alignment
- [casehubio/neocortex ARC42STORIES.MD](https://github.com/casehubio/neocortex/blob/main/ARC42STORIES.MD) — authoritative architecture record (Matryoshka section 4, oversampling section 6, dimension consistency section 7, naming section 8)
- Design specs: `docs/specs/2026-06-03-ai-fusion-hybrid-fact-space.md`, `docs/specs/2026-06-03-standalone-rag-retrieval-brief.md`
- Authoritative inference design: `Hortora/spec: docs/superpowers/specs/2026-06-03-onnx-inference-module-design.md`
