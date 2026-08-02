# casehub-neocortex — Consumer Guide

> Neural text inference, RAG integration, and agent memory for the casehub platform.

**GitHub:** [casehubio/neocortex](https://github.com/casehubio/neocortex)
**Tier:** Foundation

---

## Purpose

Three related capabilities in one repo:

**Neural Text Inference** — a standalone, general-purpose ONNX inference layer for JVM projects. Zero casehub domain dependencies. Shared with Hortora. Fills the gap LangChain4j leaves: NLI, classification, regression, SPLADE sparse embeddings, cross-encoder reranking.

**RAG Integration** — casehub-specific LangChain4j RAG pipeline wiring. Tenancy-isolated Qdrant corpus storage, hybrid dense+sparse+BM25 search via configurable fusion (RRF, DBSF, CC). Exposes `EmbeddingIngestor` and `CaseRetriever` SPIs for use by engine case steps and the typed fact space.

**Agent Memory** — queryable, permission-aware, persistent agent memory. `CaseMemoryStore` SPI with multiple backends (in-memory, JPA/PostgreSQL, SQLite, Mem0, Graphiti, Qdrant). `CbrCaseMemoryStore` SPI for case-based reasoning with typed feature-vector similarity search over prior cases.

---

## Modules to Depend On

### Inference

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `inference-api` | `casehub-neocortex-inference-api` | `InferenceModel` SPI, `MultiModalEmbedder` interface, `EmbeddingMode` enum — pure Java, zero deps |
| `inference-tasks` | `casehub-neocortex-inference-tasks` | `NliClassifier`, `TextClassifier`, `ScalarRegressor`, `CrossEncoderReranker` |
| `inference-splade` | `casehub-neocortex-inference-splade` | SPLADE sparse embeddings — `SparseEmbedder.embed()` returns `Map<Integer, Float>` |
| `inference-bge-m3` | `casehub-neocortex-inference-bge-m3` | `BgeM3Embedder` — dense + sparse + ColBERT from a single ONNX model run |
| `inference-quarkus` | `casehub-neocortex-inference-quarkus` | CDI wiring, `@InferenceModel` qualifier, Dev Services, `@QuarkusTest` support |

### RAG

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `rag-api` | `casehub-neocortex-rag-api` | `EmbeddingIngestor`, `CaseRetriever`, `RetrievalTracker` SPIs — pure Java + Mutiny |
| `rag` | `casehub-neocortex-rag` | LangChain4j pipeline, Qdrant, three-leg hybrid search, `MatryoshkaEmbeddingModel`, `DenseQuantization` |
| `rag-testing` | `casehub-neocortex-rag-testing` | In-memory stubs: `EmbeddingIngestor`, `CaseRetriever`, `CursorStore`, `RetrievalTracker` for `@QuarkusTest` |

### Memory

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `memory-api` | `casehub-neocortex-memory-api` | `CaseMemoryStore`, `CbrCaseMemoryStore`, `GraphCaseMemoryStore` SPIs, typed feature values |
| `memory` | `casehub-neocortex-memory` | CDI wiring, `MemoryEmitter` fire-and-forget wrapper, decorator chain |
| `memory-inmem` | `casehub-neocortex-memory-inmem` | In-memory volatile backend — test + ephemeral |
| `memory-jpa` | `casehub-neocortex-memory-jpa` | PostgreSQL + Flyway + FTS via `websearch_to_tsquery` |
| `memory-sqlite` | `casehub-neocortex-memory-sqlite` | SQLite + HikariCP WAL + FTS5 |
| `memory-mem0` | `casehub-neocortex-memory-mem0` | Mem0 REST adapter — vector embeddings + semantic search |
| `memory-graphiti` | `casehub-neocortex-memory-graphiti` | Graphiti REST adapter — temporal knowledge graph |
| `memory-qdrant` | `casehub-neocortex-memory-qdrant` | Qdrant vector store backend + `CbrReconciliationService` |
| `memory-testing` | `casehub-neocortex-memory-testing` | Test stubs for memory SPIs |

### Corpus and Fusion

| Module | artifactId | What you get |
|--------|-----------|-------------|
| `corpus-api` | `casehub-neocortex-corpus-api` | `CorpusStore`, `CorpusReader`, `ChangeSource` SPIs — pure Java, zero deps |
| `corpus` | `casehub-neocortex-corpus` | Zip, flat filesystem, and composite implementations |
| `fusion-api` | `casehub-neocortex-fusion-api` | `FusionStrategy` enum, `ScoreFusion` utility (RRF + CC), `CamelCaseExpander` — pure Java, zero deps |

---

## Key Abstractions

### InferenceModel / Task Adapters

`InferenceModel` SPI runs any ONNX model: `run(InferenceInput)` / `runBatch(List<InferenceInput>)`. Callers work through typed task adapters in `inference-tasks`, never raw tensors.

| Adapter | Model type | Use case |
|---------|-----------|----------|
| `NliClassifier` | NLI | Hallucination detection — scores LLM output faithfulness against facts |
| `TextClassifier` | Classification | Action risk classification in casehub-openclaw |
| `ScalarRegressor` | Regression | Epistemic domain confidence estimation in casehub-eidos |
| `CrossEncoderReranker` | Cross-encoder | Precision-mode reranking — top-N from top-K candidates |

### SparseEmbedder (inference-splade)

`SparseEmbedder.embed(String text)` returns `Map<Integer, Float>` — sparse term weights after log-saturation (`log(1 + relu(weight))`) and threshold filtering. Output is suitable for direct Qdrant named vector space upsert. Forms the sparse leg of hybrid search.

### EmbeddingIngestor / CaseRetriever (rag-api)

`EmbeddingIngestor` — ingest pre-chunked text into vector store (embedding + storage). Tenancy-scoped via `CorpusRef` (tenant ID + corpus name).

`CaseRetriever` — retrieval entry point for case steps and the fact space. `retrieve(query, CorpusRef)` returns `List<RetrievedChunk>`. Hybrid search: dense + sparse + BM25 fused via configurable `FusionStrategy`. Reactive variant: `ReactiveCaseRetriever` returns `Uni<List<RetrievedChunk>>`.

### MatryoshkaEmbeddingModel (rag)

Truncating `EmbeddingModel` decorator. Takes a delegate model and `targetDimension`, truncates to the first N dimensions and L2-renormalizes. Config-driven: active when `casehub.rag.matryoshka.dimension` is set. `dimension()` returns the truncated size, flowing transparently to `ensureCollection()`.

### Configurable Fusion Strategy (fusion-api, rag)

`FusionStrategy` enum — `RRF` (Reciprocal Rank Fusion), `DBSF` (Distribution-Based Score Fusion), `CC` (Convex Combination). `ScoreFusion` utility implements RRF and CC algorithms with `ScoredLeg`/`FusedResult` records. Config: `casehub.rag.retrieval.fusion-strategy` (default `RRF`).

### MultiModalEmbedder / BgeM3 (inference-api, inference-bge-m3)

`MultiModalEmbedder` interface produces all three embedding modes (dense, sparse, ColBERT) from a single model. `BgeM3Embedder` implements this for BGE-M3 ONNX models. `SeparateModelEmbedder` in `rag/` bridges LangChain4j `EmbeddingModel` + optional `SparseEmbedder` into the same contract — `@DefaultBean` displaced by BgeM3 when configured.

### CaseMemoryStore / CbrCaseMemoryStore SPIs (memory-api)

`CaseMemoryStore` — queryable, permission-aware, persistent memory. Multiple backends coexist via CDI priority ladder. `MemoryEmitter` in `memory/` provides fire-and-forget wrapper that swallows non-security exceptions.

`CbrCaseMemoryStore` — structured feature-vector similarity search. Typed `FeatureValue` system (7 value types), `FeatureField` schema (9 field types), weighted per-field similarity scoring via `CbrSimilarityScorer`. Supports supersession, temporal decay, and trend detection.

### Corpus Ingestion Bridge (rag)

Config-driven polling bridge that populates a RAG corpus from external sources. `CorpusIngestionService` orchestrates scheduled polling with reconciliation. `MetadataExtractor` SPI extracts body + metadata from document content. `CursorStore` SPI provides pluggable cursor persistence for incremental polling.

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
| Cross-encoder reranking | `inference-tasks` + `rag-crossencoder` (this module) |
| Score fusion algorithms (RRF, CC) | `fusion-api` (this module) — pure Java, zero deps |
| BM25 text retrieval | `rag` (this module) — in-memory inverted index, third retrieval leg |
| casehub-specific RAG wiring + tenancy | `rag` / `rag-api` (this module) |
| Matryoshka dimension reduction + L2 renorm | `rag` (this module) — decorator above LangChain4j `EmbeddingModel` |
| Dense + ColBERT vector quantization + oversampling | `rag` (this module) — Qdrant collection config + search params |
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
| `casehub.rag.retrieval.cc-weights.*` | — | Per-leg weights for Convex Combination fusion |
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

### CBR Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `casehub.cbr.reranking.enabled` | — | Enable cross-encoder reranking for CBR |
| `casehub.cbr.tracking.enabled` | — | Enable CBR retrieval tracking |
| `casehub.cbr.tracking.retention.days` | `90` | CBR tracking trace retention period |
| `casehub.cbr.outcome-weighting.enabled` | — | Enable outcome-based score modulation |
| `casehub.cbr.outcome-weighting.influence` | `0.3` | Outcome weighting influence factor |
| `casehub.cbr.trust-weighting.enabled` | — | Enable trust-based score modulation |
| `casehub.cbr.trust-weighting.influence` | `0.3` | Trust weighting influence factor |
| `casehub.cbr.adaptation-tracking.enabled` | — | Enable plan adaptation tracking |
| `casehub.cbr.ensemble-tracking.enabled` | — | Enable ensemble analysis tracking |
