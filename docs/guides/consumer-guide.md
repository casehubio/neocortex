# casehub-neocortex — Consumer Guide

> Neural text inference, RAG integration, CBR memory, and agent memory for the casehub platform.

**GitHub:** [casehubio/neocortex](https://github.com/casehubio/neocortex)
**Tier:** Foundation

---

## Purpose

Four related capabilities in one repo:

**Neural Text Inference** — a standalone, general-purpose ONNX inference layer for JVM projects. Zero casehub domain dependencies. Shared with Hortora. Fills the gap LangChain4j leaves: NLI, classification, regression, SPLADE sparse embeddings, cross-encoder reranking, and raw tensor classification.

**RAG Integration** — casehub-specific LangChain4j RAG pipeline wiring. Tenancy-isolated Qdrant corpus storage, hybrid dense+sparse+BM25 search via configurable fusion (RRF, DBSF, CC). Exposes `EmbeddingIngestor` and `CaseRetriever` SPIs for use by engine case steps and the typed fact space. Pre-ingestion dedup gate, retrieval tracking, corrective RAG, cross-encoder reranking, and query expansion.

**CBR Memory** — case-based reasoning with typed feature-vector similarity search over prior cases. `CbrCaseMemoryStore` SPI with multiple backends (in-memory, JPA/PostgreSQL, Qdrant). Typed feature values (7 value types, 9 field types), weighted similarity scoring, plan adaptation, ensemble analysis, temporal decay, trust-weighted retrieval, hierarchical scoping, and outcome feedback loops.

**Agent Memory** — queryable, permission-aware, persistent agent memory. `CaseMemoryStore` SPI with multiple backends (in-memory, JPA/PostgreSQL, SQLite, Mem0, Graphiti). Salience-based ranking, confidence-aware retention, fire-and-forget emission via `MemoryEmitter`.

---

## Modules to Depend On

### Inference

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `inference-api` | `casehub-neocortex-inference-api` | `InferenceModel` SPI, `InferenceInput` sealed interface (Text + Tensor variants), `MultiModalEmbedder` interface, `EmbeddingMode` enum — pure Java, zero deps |
| `inference-tasks` | `casehub-neocortex-inference-tasks` | `NliClassifier`, `TextClassifier`, `TensorClassifier`, `ScalarRegressor`, `CrossEncoderReranker` |
| `inference-splade` | `casehub-neocortex-inference-splade` | SPLADE sparse embeddings — `SparseEmbedder.embed()` returns `Map<Integer, Float>` |
| `inference-bge-m3` | `casehub-neocortex-inference-bge-m3` | `BgeM3Embedder` — dense + sparse + ColBERT from a single ONNX model run |
| `inference-quarkus` | `casehub-neocortex-inference-quarkus` | CDI wiring, `@InferenceModel` qualifier, Dev Services, `@QuarkusTest` support |
| `inference-inmem` | `casehub-neocortex-inference-inmem` | Deterministic `InferenceModel` stubs — no JNI, safe in all test contexts |

### RAG

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `rag-api` | `casehub-neocortex-rag-api` | `EmbeddingIngestor`, `CaseRetriever`, `RetrievalTracker`, `RelevanceEvaluator`, `QueryExpander`, `RetrievalAnalyzer` SPIs — pure Java |
| `rag` | `casehub-neocortex-rag` | LangChain4j pipeline, Qdrant, three-leg hybrid search, `MatryoshkaEmbeddingModel`, `DenseQuantization`, `DedupEmbeddingIngestor`, `PayloadBoostCaseRetriever` |
| `rag-tika` | `casehub-neocortex-rag-tika` | Apache Tika document parser — extracts text + metadata from binary documents (PDF, DOCX) for RAG ingestion |
| `rag-crossencoder` | `casehub-neocortex-rag-crossencoder` | Corrective RAG quality-gating + cross-encoder reranking. Config-gated decorators |
| `rag-expansion` | `casehub-neocortex-rag-expansion` | Query expansion — HyDE, step-back, multi-query fan-out with RRF fusion. Config-gated decorator |
| `rag-tracking` | `casehub-neocortex-rag-tracking` | SQLite-backed retrieval tracking with retention scheduling. Config-gated decorator |
| `rag-testing` | `casehub-neocortex-rag-testing` | In-memory stubs: `EmbeddingIngestor`, `CaseRetriever`, `CursorStore`, `RetrievalTracker`, `RelevanceEvaluator` for `@QuarkusTest` |

### Fusion

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `fusion-api` | `casehub-neocortex-fusion-api` | `FusionStrategy` enum, `ScoreFusion` utility (RRF + CC), `CamelCaseExpander` — pure Java, zero deps. Shared by RAG and CBR |

### Agent Memory

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `memory-api` | `casehub-neocortex-memory-api` | `CaseMemoryStore`, `GraphCaseMemoryStore` SPIs, `MemoryOrder` (CHRONOLOGICAL, RELEVANCE, SALIENCE), `MemoryInput` with confidence, `MemoryRetentionPolicy`, `MemoryScanRequest` — pure Java |
| `memory` | `casehub-neocortex-memory` | CDI wiring, `MemoryEmitter` fire-and-forget wrapper, `CaseEnrichmentDecorator`, `MemoryRetentionScheduler` |
| `memory-inmem` | `casehub-neocortex-memory-inmem` | In-memory volatile backend — test + ephemeral |
| `memory-jpa` | `casehub-neocortex-memory-jpa` | PostgreSQL + Flyway + FTS via `websearch_to_tsquery` |
| `memory-sqlite` | `casehub-neocortex-memory-sqlite` | SQLite + HikariCP WAL + FTS5 |
| `memory-mem0` | `casehub-neocortex-memory-mem0` | Mem0 REST adapter — vector embeddings + semantic search |
| `memory-graphiti` | `casehub-neocortex-memory-graphiti` | Graphiti REST adapter — temporal knowledge graph |
| `memory-testing` | `casehub-neocortex-memory-testing` | Test stubs for memory SPIs |

### CBR Memory

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `memory-api` | `casehub-neocortex-memory-api` | `CbrCaseMemoryStore` SPI, typed feature values, field schema, similarity specs, `PlanAdapter`, `PlanEnsembleAnalyzer`, `AgentTrustProvider`, `CbrRetrievalTracker`, `PersonalityTransitionSchema` — pure Java |
| `memory` | `casehub-neocortex-memory` | CBR CDI decorator chain — outcome weighting, trust-weighted retrieval, scope decay, temporal decay, trend enrichment, erasure notification. `CbrRetentionScheduler`, `TrustRetentionService`, `CbrOutcomeConsumer` |
| `memory-cbr-inmem` | `casehub-neocortex-memory-cbr-inmem` | In-memory CBR case store for tests |
| `memory-cbr-jpa` | `casehub-neocortex-memory-cbr-jpa` | JPA/PostgreSQL CBR store with JSONB features, plan traces, outcome tracking |
| `memory-qdrant` | `casehub-neocortex-memory-qdrant` | Qdrant vector store backend + multi-leg hybrid fusion + `CbrReconciliationService` |
| `memory-cbr-embedding` | `casehub-neocortex-memory-cbr-embedding` | `EmbeddingTextSimilarity` — LangChain4j `EmbeddingModel`-based semantic text similarity for CBR fields |
| `memory-cbr-crossencoder` | `casehub-neocortex-memory-cbr-crossencoder` | Cross-encoder reranking for CBR retrieval. Config-gated decorator |
| `memory-cbr-tracking` | `casehub-neocortex-memory-cbr-tracking` | SQLite-backed CBR retrieval tracking + plan adaptation tracking + ensemble tracking |

### Corpus

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `corpus-api` | `casehub-neocortex-corpus-api` | `CorpusStore`, `CorpusReader`, `ChangeSource` SPIs — pure Java, zero deps |
| `corpus` | `casehub-neocortex-corpus` | Zip, flat filesystem, and composite implementations |

---

## Key Abstractions

### InferenceModel / Task Adapters

`InferenceModel` SPI runs any ONNX model. `InferenceInput` is a sealed interface with two variants:
- `InferenceInput.Text` — tokenized text input (single text or text pair)
- `InferenceInput.Tensor` — raw named float tensors (bypasses tokenization)

Callers work through typed task adapters in `inference-tasks`, never raw tensors.

| Adapter | Input type | Model type | Use case |
|---------|-----------|-----------|----------|
| `NliClassifier` | Text pair | NLI | Hallucination detection — scores LLM output faithfulness against facts |
| `TextClassifier` | Text | Classification | Action risk classification in casehub-openclaw |
| `TensorClassifier` | Tensor | Classification | Multi-dimensional tensor classification with softmax + configurable labels. Used by strategy classifier (#76) |
| `ScalarRegressor` | Text pair | Regression | Epistemic domain confidence estimation in casehub-eidos |
| `CrossEncoderReranker` | Text pair | Cross-encoder | Precision-mode reranking — top-N from top-K candidates |

### SparseEmbedder (inference-splade)

`SparseEmbedder.embed(String text)` returns `Map<Integer, Float>` — sparse term weights after log-saturation (`log(1 + relu(weight))`) and threshold filtering. Output is suitable for direct Qdrant named vector space upsert. Forms the sparse leg of hybrid search.

### EmbeddingIngestor / CaseRetriever (rag-api)

`EmbeddingIngestor` — ingest pre-chunked text into vector store (embedding + storage). Tenancy-scoped via `CorpusRef` (tenant ID + corpus name).

`CaseRetriever` — retrieval entry point for case steps and the fact space. `retrieve(query, CorpusRef)` returns `List<RetrievedChunk>`. Hybrid search: dense + sparse + BM25 fused via configurable `FusionStrategy`.

### Pre-Ingestion Dedup Gate (rag)

`DedupEmbeddingIngestor` — CDI Decorator (Priority 50) on `EmbeddingIngestor`. Before indexing, embeds each chunk and queries Qdrant for existing near-duplicates via cosine similarity. Chunks exceeding the threshold (default 0.95) are skipped. Config: `casehub.rag.ingestion.dedup.enabled` (default `true`), `casehub.rag.ingestion.dedup.threshold` (default `0.95`).

### RetrievalQuery and Per-Query Weight Multipliers (rag-api)

`RetrievalQuery` carries `text`, optional `expandedText` (from query expansion), and `weightMultipliers` — a `Map<String, Double>` of per-leg weight overrides for this specific query. `searchText()` returns `expandedText` when present, `text` otherwise. Dense leg uses `searchText()`; sparse and BM25 legs use `text()`.

Convenience: `withBm25Boost(double)` sets the BM25 multiplier, `withWeightMultiplier(leg, multiplier)` sets any leg. `HybridCaseRetriever.effectiveWeight()` combines global `FusionWeightsConfig` with per-query multipliers.

### MatryoshkaEmbeddingModel (rag)

Truncating `EmbeddingModel` decorator. Takes a delegate model and `targetDimension`, truncates to the first N dimensions and L2-renormalizes. Config-driven: active when `casehub.rag.matryoshka.dimension` is set. `dimension()` returns the truncated size, flowing transparently to `ensureCollection()`.

### Configurable Fusion Strategy (fusion-api, rag)

`FusionStrategy` enum — `RRF` (Reciprocal Rank Fusion), `DBSF` (Distribution-Based Score Fusion), `CC` (Convex Combination). `ScoreFusion` utility implements RRF and CC algorithms with `ScoredLeg`/`FusedResult` records. Config: `casehub.rag.retrieval.fusion-strategy` (default `RRF`).

`FusionWeightsConfig` — unified per-leg weight configuration replacing the former `CcWeightsConfig`. Covers `dense`, `sparse`, `bm25`, and `quality` weights (default 1.0 each). CC uses these directly; weighted RRF auto-falls back to client-side when weights are non-equal. `PayloadBoostCaseRetriever` (Decorator Priority 60) applies the quality weight as post-fusion rescore for RRF/DBSF (CC integrates quality as a fusion leg natively).

### RelevanceEvaluator / ColBERT Relevance (rag-api, rag-crossencoder)

`RelevanceEvaluator` SPI — `evaluateChunks(query, chunks)` returns `List<ScoredGrade>` mapping each chunk to a `RelevanceGrade` (CORRECT, AMBIGUOUS, INCORRECT) with a score.

Two implementations:
- `CrossEncoderRelevanceEvaluator` in `rag-crossencoder` — uses ONNX cross-encoder model
- `ColBertRelevanceEvaluator` in `rag-api` — pure Java score-threshold mapper reading `relevanceScore` from chunks. `calibrate()` factory derives thresholds from sample score distributions at configurable percentiles

### RetrievalAnalyzer (rag-api)

Static utility for analytics over retrieval tracking data. Pure computation — no I/O:

- **Document-level:** `documentStats()` — retrieval count, average score, outcome distribution per document
- **Query-level:** `lowRelevanceQueries()`, `zeroHitQueries()`, `queryFrequency()`
- **Quality signals:** `qualitySignals()` — identifies underperforming documents via configurable thresholds
- **Correlation:** `correlationGraph()` — bipartite query-to-document graph with `EdgeStats` (co-occurrence, average score, outcome distribution). `queryClusters()` — single-linkage Jaccard clustering (MinHash LSH for n > 50 queries, brute-force below). `documentImpact()` — centrality ranking with outcome aggregation

### MultiModalEmbedder / BgeM3 (inference-api, inference-bge-m3)

`MultiModalEmbedder` interface produces all three embedding modes (dense, sparse, ColBERT) from a single model. `embed(String text)`, `embed(Map<EmbeddingMode, String>)`, and `embedSeparate(Map<EmbeddingMode, String>)` for per-leg embedding with different texts. `BgeM3Embedder` implements this for BGE-M3 ONNX models. `SeparateModelEmbedder` in `rag/` bridges LangChain4j `EmbeddingModel` + optional `SparseEmbedder` into the same contract — `@DefaultBean` displaced by BgeM3 when configured.

### CaseMemoryStore (memory-api)

Queryable, permission-aware, persistent memory. Key operations:
- `store(MemoryInput)` — store with optional `Confidence` field (origin + value [0.0-1.0])
- `query(MemoryQuery)` — retrieve with `MemoryOrder` ranking (CHRONOLOGICAL, RELEVANCE, SALIENCE)
- `erase(EraseRequest)`, `eraseEntity()`, `eraseById()`, `eraseEntityAcrossTenants()` — GDPR-compliant deletion
- `scan(MemoryScanRequest)` — paginated admin scan
- `purge(MemoryRetentionPolicy)` — confidence-based retention purge
- `discoverTenants()` — cross-tenant admin operation

`MemoryOrder.SALIENCE` — recency x confidence query-time scoring. Non-semantic adapters compute salience from `createdAt` and `confidence`; semantic adapters fall back to RELEVANCE.

`MemoryRetentionScheduler` — scheduled confidence-based purge across discovered tenants. Config-driven: `casehub.memory.retention.enabled`, `casehub.memory.retention.min-confidence`, `casehub.memory.retention.max-age-days`.

### CbrCaseMemoryStore (memory-api)

Structured feature-vector similarity search over past cases. Open `CbrCase` type hierarchy with `cbrType()` discriminator: `TextualCbrCase`, `FeatureVectorCbrCase`, `PlanCbrCase`.

**Typed feature values:** `FeatureValue` sealed interface with seven value types: `StringVal`, `NumberVal`, `RangeVal`, `StringListVal`, `NumberListVal`, `StructVal`, `StructListVal`. Booleans coerced via `FeatureValue.of(Object)`.

**Feature field schema:** `FeatureField` sealed interface with nine permits: `Categorical`, `Numeric`, `Text` (with `semantic` flag), `CategoricalList`, `NumericList`, `NestedObject`, `ObjectList`, `TimeSeries`, `DiscreteSequence`.

**Similarity scoring:** `CbrSimilarityScorer` — pure-Java weighted composite scoring with three-level precedence: caller override, field `SimilaritySpec`, type default. `SimilaritySpec` sealed interface: `CategoricalTable`, `GaussianDecay`, `StepDecay`, `ExponentialDecay`, `DtwSpec`, `EditDistanceSpec`.

**Retrieval modes:** `CbrQuery.RetrievalMode` — `FEATURE_ONLY`, `SEMANTIC_ONLY`, `HYBRID`. `FusionStrategy` from `fusion-api` for result merging.

**Hierarchical scoping:** `CbrQuery.scope` (required `Path`) for hierarchical visibility. `ScopeDecay` sealed interface (Exponential, Linear, Step) for scope-distance score decay.

**Temporal decay:** `TemporalDecay` sealed interface (HalfLife, Linear, Step) for smooth recency decay applied post-scoring.

**Filters:** `CbrFilter` sealed interface — `Contains`, `ContainsAll`, `ContainsAny`, `NotContains`, `NotContainsAny`, `ContainsRange`, `HasMatch`, `AllOf`.

**Supersession:** `supersede(caseId, tenantId, supersedingCaseId, reason)` and `reinstate(caseId, tenantId)`. `getSupersessionStatus()` and `findSupersededCases()` for audit.

**Outcome feedback:** `recordOutcome(CbrOutcome)` — CBR Revise feedback loop with EMA confidence adjustment.

**Retention:** `purge(CbrRetentionPolicy)` — age + count + trust-based purge. `CbrRetentionScheduler` for scheduled purging. `TrustRetentionService` — evaluates agent trust trajectories via `AgentTrustProvider` and purges cases below `minCurrentTrust`.

**Scan:** `scan(CbrScanRequest)` — paginated scan with tenant/domain/caseType filtering. Returns `List<CbrCaseSummary>` (caseId, entityId, caseType, producerAgentId, trustScore, storedAt).

### PlanAdapter / PlanEnsembleAnalyzer (memory-api)

`PlanAdapter` SPI — transforms retrieved plans for new case contexts. `adapt(caseType, ScoredCbrCase<PlanCbrCase>, features)` returns `AdaptedPlan` with `AdaptedStep` entries tagged by `AdaptationAction` (RETAINED, SUBSTITUTED, BOOSTED, SUPPRESSED, ADDED, REMOVED). `PlanTrace` records audit data with optional `variantId`.

`PlanEnsembleAnalyzer` SPI — cross-plan structural analysis. After per-plan adaptation, examines multiple adapted plans for consensus/divergence and synthesizes an `EnsemblePlan`. `StepConsensus` classifies agreement as UNANIMOUS, CONSENSUS, CONTESTED, MINORITY, or UNIQUE.

### Trust-Weighted Retrieval (memory)

`TrustWeightedCbrCaseMemoryStore` (Decorator Priority 60) — modulates retrieval scores by source trust authority + optional trust trajectory via `AgentTrustProvider` SPI. `TrustWeightingFunction` SPI for pluggable score modulation. Default: linear interpolation `score*(1-alpha+alpha*trustScore)` with declining trajectory penalty.

Config-gated: `casehub.cbr.trust-weighting.enabled`, `casehub.cbr.trust-weighting.influence` (default 0.3).

### PersonalityTransitionSchema (memory-api)

Built-in CBR schema for personality evolution memory. Records when an agent's cognitive function profile shifts (e.g. dominant Ti to Fe after JPAF reflection). Case type: `personality-transition`. Features: `agent_id`, `old_dominant`, `new_dominant`, `old_auxiliary`, `new_auxiliary`, `trigger_type`, `outcome`.

### Corpus Ingestion Bridge (rag)

Config-driven bridge that populates a RAG corpus from external sources. `CorpusIngestionService` orchestrates both event-driven ingestion (directory-watcher for filesystem corpora) and scheduled polling (for ZIP-based corpora). `MetadataExtractor` SPI extracts body + metadata from document content. `CursorStore` SPI provides pluggable cursor persistence for incremental polling.

---

## Relationship to LangChain4j

This module sits **below** LangChain4j for inference, and **above** LangChain4j for RAG:

| Capability | Where it lives |
|---|---|
| Dense float-vector embeddings | LangChain4j `OnnxEmbeddingModel` |
| RAG pipeline, chunking, vector stores | LangChain4j |
| Sparse embeddings (SPLADE) | `inference-splade` (this module) |
| Multi-modal embeddings (dense+sparse+ColBERT) | `inference-bge-m3` (this module) |
| NLI, classification, regression | `inference-tasks` (this module) |
| Tensor classification (softmax + labels) | `inference-tasks` (this module) |
| Cross-encoder reranking | `inference-tasks` + `rag-crossencoder` (this module) |
| Score fusion algorithms (RRF, CC) | `fusion-api` (this module) — pure Java, zero deps |
| BM25 text retrieval | `rag` (this module) — in-memory inverted index, third retrieval leg |
| casehub-specific RAG wiring + tenancy | `rag` / `rag-api` (this module) |
| Matryoshka dimension reduction + L2 renorm | `rag` (this module) — decorator above LangChain4j `EmbeddingModel` |
| Dense + ColBERT vector quantization + oversampling | `rag` (this module) — Qdrant collection config + search params |
| Pre-ingestion dedup gate | `rag` (this module) — cosine similarity check before indexing |
| Retrieval analytics (document stats, query clusters, correlation) | `rag-api` (this module) — pure computation over tracker data |
| CBR typed feature similarity (DTW, edit distance, decay) | `memory-api` (this module) |
| Retrieval tracking + feedback measurement | `rag-tracking` + `memory-cbr-tracking` (this module) |

---

## Shared with Hortora

`inference-api`, `inference-runtime`, `inference-tasks`, `inference-splade`, `inference-inmem` have zero casehub/Quarkus/LangChain4j dependencies. Hortora depends on these directly and wires them into their own stack.

`rag-api`, `rag`, and `rag-testing` are also consumed by Hortora — Hortora's garden retrieval engine uses these modules for Qdrant/ingestion. Tenancy enforcement is optional: active when `CurrentPrincipal` is on the classpath, no-ops when absent via `TenantGuard`.

ArchUnit enforced from day one: zero-domain-dep constraint on all `inference-*` modules.

---

## Native Image — JVM Mode by Design

The inference service is long-running — native image's fast startup provides no benefit, and HotSpot's JIT optimisation outperforms AOT for sustained workloads. `inference-*` modules operate in JVM mode.

The C2 native image gate passed (ONNX Runtime JNI + HuggingFace Tokenizers JNI both work in Quarkus native image on macOS ARM). Reachability metadata ships in `inference-quarkus` for downstream consumers that distribute as native binaries.

---

## Configuration

### RAG Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `casehub.rag.retrieval.fusion-strategy` | `RRF` | Fusion strategy: RRF, DBSF, or CC |
| `casehub.rag.retrieval.weights.dense` | `1.0` | Dense leg weight for fusion |
| `casehub.rag.retrieval.weights.sparse` | `1.0` | Sparse leg weight for fusion |
| `casehub.rag.retrieval.weights.bm25` | `1.0` | BM25 leg weight for fusion |
| `casehub.rag.retrieval.weights.quality` | `1.0` | Quality/payload boost weight |
| `casehub.rag.matryoshka.dimension` | — | Matryoshka truncation dimension (disabled if unset) |
| `casehub.rag.quantization.type` | `NONE` | Dense quantization: NONE, BINARY, SCALAR |
| `casehub.rag.quantization.always-ram` | `true` | Keep quantized vectors in RAM |
| `casehub.rag.quantization.oversampling` | — | Oversampling factor for quantized search |
| `casehub.rag.bm25.enabled` | `true` | Enable BM25 as third retrieval leg |
| `casehub.rag.crag.enabled` | — | Enable corrective RAG quality-gating |
| `casehub.rag.reranking.enabled` | — | Enable cross-encoder reranking |
| `casehub.rag.tracking.enabled` | — | Enable retrieval tracking |
| `casehub.rag.tracking.retention.days` | `90` | Tracking trace retention period |
| `casehub.rag.expansion.mode` | — | Query expansion mode: llm, step-back, template |
| `casehub.rag.ingestion.dedup.enabled` | `true` | Enable pre-ingestion dedup gate |
| `casehub.rag.ingestion.dedup.threshold` | `0.95` | Cosine similarity threshold for dedup |

### Agent Memory Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `casehub.memory.retention.enabled` | — | Enable scheduled confidence-based retention purge |
| `casehub.memory.retention.domain` | — | Memory domain for retention scheduling |
| `casehub.memory.retention.max-age-days` | — | Maximum age before purge eligibility |
| `casehub.memory.retention.min-confidence` | — | Minimum confidence to retain |

### CBR Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `casehub.cbr.reranking.enabled` | — | Enable cross-encoder reranking for CBR |
| `casehub.cbr.tracking.enabled` | — | Enable CBR retrieval tracking |
| `casehub.cbr.tracking.retention.days` | `90` | CBR tracking trace retention period |
| `casehub.cbr.adaptation-tracking.enabled` | — | Enable plan adaptation tracking |
| `casehub.cbr.ensemble-tracking.enabled` | — | Enable ensemble analysis tracking |
| `casehub.cbr.outcome-weighting.enabled` | — | Enable outcome-based score modulation |
| `casehub.cbr.outcome-weighting.influence` | `0.3` | Outcome weighting influence factor |
| `casehub.cbr.trust-weighting.enabled` | — | Enable trust-based score modulation |
| `casehub.cbr.trust-weighting.influence` | `0.3` | Trust weighting influence factor |
| `casehub.cbr.retention.enabled` | — | Enable scheduled CBR retention purge |
| `casehub.cbr.retention.domain` | — | CBR domain for retention scheduling |
| `casehub.cbr.retention.case-types` | — | Case types subject to retention |
| `casehub.cbr.retention.max-age-days` | — | Maximum case age before purge |
| `casehub.cbr.retention.max-cases-per-type` | — | Maximum cases per type per tenant |
| `casehub.cbr.retention.min-trust-score` | — | Minimum trust score to retain |
| `casehub.cbr.trust-retention.enabled` | — | Enable trust-trajectory-based purge |
| `casehub.cbr.trust-retention.min-current-trust` | — | Minimum current trust score threshold |
