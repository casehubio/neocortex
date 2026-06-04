# casehub-neural-text

[![casehub-neural-text](https://github.com/casehubio/neural-text/actions/workflows/publish.yml/badge.svg?branch=main)](https://github.com/casehubio/neural-text/actions/workflows/publish.yml)

ONNX neural text inference and LangChain4j RAG integration for the [casehubio](https://github.com/casehubio) platform.

---

## Why this exists

The casehub platform orchestrates multi-agent AI workflows using LLM agents, rule engines (Drools), workflow engines (QuarkusFlow), and human task gates. Effective orchestration requires more than just routing and sequencing — it requires the platform itself to reason about what agents are doing, whether they can be trusted, and what knowledge they need.

**LangChain4j covers dense embeddings and the RAG pipeline.** What it does not cover is running arbitrary pre-trained neural network models for scoring, classification, and sparse retrieval — capabilities that the platform needs to operate intelligently without making external API calls for every decision.

`casehub-neural-text` fills that gap, and also wires up the LangChain4j RAG pipeline with casehub-specific tenancy isolation and hybrid search.

---

## What the inference-* modules do

These modules provide a **local ONNX model execution layer** for the JVM. Any ONNX-format model can be loaded and run in-process — no API call, no Python, no network dependency. This is essential for regulated deployments (clinical, AML, financial) where data cannot leave the tenant's infrastructure.

The modules have **zero casehub domain dependencies** — they are also used by [Hortora](https://github.com/Hortora/spec) independently of casehub. ArchUnit enforces this constraint from day one.

### Use cases inside casehub

#### Hallucination detection — `NliClassifier` in `inference-tasks`

LLM agents can assert things that aren't supported by the facts they were given. Before an LLM worker's output is asserted into the typed fact space (the shared epistemic workspace used by the AI Fusion architecture), a Natural Language Inference model scores the output against the input facts for faithfulness — `entailment`, `neutral`, or `contradiction`. Outputs that contradict the input facts are flagged before they propagate downstream.

**Where it lands:** `casehub-engine` observability module.

#### Action risk classification — `TextClassifier` in `inference-tasks`

`casehub-openclaw` provisions OpenClaw agents as casehub workers. Before an agent's output is dispatched to the work channel, the `ActionRiskClassifier` SPI decides whether it can proceed autonomously or must be routed to human oversight. The default implementation is a stub (always AUTONOMOUS). A real implementation is a per-deployment ONNX classifier trained on the organisation's own escalation decisions — no API call, deterministic, fast.

**Where it lands:** `casehub-openclaw` — replaces the always-AUTONOMOUS stub.

#### Epistemic confidence estimation — `ScalarRegressor` in `inference-tasks`

`casehub-eidos` allows agents to declare their confidence in specific domains: `{"java": 0.95, "rust": 0.42}`. These values are currently static — declared at registration time and never updated. A regression model trained on agent output history can estimate actual domain confidence dynamically. That estimate feeds back into `CapabilityHealth.probe()`, making the engine's agent routing decisions more accurate over time.

**Where it lands:** `casehub-eidos` — dynamic epistemic domain confidence.

#### SPLADE sparse embeddings — `SparseEmbedder` in `inference-splade`

Dense vector embeddings (what LangChain4j's `OnnxEmbeddingModel` produces) capture semantic similarity well but dilute the weight of specific regulatory or clinical terms. SPLADE produces sparse term weight maps — every dimension corresponds to a vocabulary token, and only relevant tokens have non-zero weight. This gives lexical precision that dense embeddings sacrifice.

Used in the sparse leg of hybrid RAG search: regulatory text, SAR typologies, clinical protocol language, and AML classification criteria all benefit from SPLADE's term-level precision. The sparse vectors are stored alongside dense vectors in Qdrant named vector spaces and fused at retrieval time using Reciprocal Rank Fusion (RRF).

**Where it lands:** `casehub-rag` — the sparse leg of hybrid search. Also used directly by Hortora.

#### Cross-encoder reranking — `CrossEncoderReranker` in `inference-tasks`

After hybrid retrieval returns a candidate set (top-20), a cross-encoder reranker jointly encodes each query+candidate pair and produces a more accurate relevance score than the initial vector similarity. The top-N candidates after reranking are what actually get injected into the LLM prompt. This precision mode is optional (adds latency) — enabled when retrieval accuracy matters more than speed.

**Where it lands:** `casehub-rag` — precision-mode reranking before prompt injection.

---

## What the rag-* modules do

These modules wire up LangChain4j's RAG pipeline for casehub — with casehub-specific tenancy isolation, hybrid search, and SPIs that integrate with the engine and the typed fact space.

LangChain4j already provides document parsing (via Apache Tika), chunking, dense ONNX embeddings, Qdrant storage, and retrieval. The `rag-*` modules configure and expose these through casehub-appropriate SPIs rather than reimplementing them.

### CorpusStore — document ingestion

Application repos (aml, clinical, devtown) manage named document corpora. A corpus is a tenancy-scoped collection of documents — SAR typologies for AML, trial protocols for clinical, coding standards for devtown. `CorpusStore` handles ingest (Apache Tika extracts text from any format), chunking, dual embedding (dense via LangChain4j + sparse via SPLADE), and Qdrant storage.

Every `CorpusStore` operation requires a `CorpusRef` carrying the tenant ID. Cross-tenant access is blocked at the SPI boundary.

### CaseRetriever — retrieval for case steps and the fact space

`CaseRetriever` is the retrieval entry point for running cases. A case step can call `retrieve(query, corpusRef)` to fetch relevant chunks from a corpus — the retrieved context is what gets injected into LLM worker prompts before dispatch.

More specifically, `casehub-engine`'s fact space prompt compiler uses `CaseRetriever` to ground LLM workers in organisational knowledge: when a clinical trial case reaches a step requiring protocol interpretation, the LLM worker receives not just the case context but also the relevant protocol excerpts retrieved from the clinical corpus. The LLM reasons about actual documents, not just its training data.

**Hybrid search:** every query runs both a dense search (semantic similarity via LangChain4j's `OnnxEmbeddingModel`) and a sparse search (lexical precision via `SparseEmbedder`). Results are fused with RRF. For regulated domains where a specific term (`Art. 22 GDPR`, `ICH E6`, `FinCEN SAR-F`) must appear rather than just be semantically related, the sparse leg ensures it does.

---

## Module structure

| Module | Artifact | Purpose |
|--------|----------|---------|
| `inference-api/` | `casehub-inference-api` | `InferenceModel` SPI, `InferenceInput`, `InferenceOutput` — pure Java, zero deps |
| `inference-runtime/` | `casehub-inference-runtime` | ONNX Runtime JVM + HuggingFace Tokenizers JNI — session management and tokenization |
| `inference-tasks/` | `casehub-inference-tasks` | `NliClassifier`, `TextClassifier`, `ScalarRegressor`, `CrossEncoderReranker` |
| `inference-splade/` | `casehub-inference-splade` | `SparseEmbedder` — log-saturation SPLADE output as `Map<Integer, Float>` |
| `inference-inmem/` | `casehub-inference-inmem` | Deterministic stubs — no JNI, safe in all test contexts |
| `inference-quarkus/` | `casehub-inference-quarkus` | `@InferenceModel` qualifier, CDI wiring, model lifecycle management |
| `rag-api/` | `casehub-rag-api` | `CorpusStore` SPI, `CaseRetriever` SPI — pure Java, zero deps |
| `rag/` | `casehub-rag` | LangChain4j + Qdrant wiring, hybrid RRF fusion, tenancy isolation |
| `rag-testing/` | `casehub-rag-testing` | In-memory stubs for `@QuarkusTest` — no Qdrant required |

---

## Relationship to LangChain4j

This module sits **below** LangChain4j for inference and **above** it for RAG:

| Capability | Where it lives |
|---|---|
| Dense embeddings, document parsing, chunking, RAG pipeline, vector stores | **LangChain4j** |
| Sparse embeddings (SPLADE) | `inference-splade` — this module |
| NLI, classification, regression, cross-encoder reranking | `inference-tasks` — this module |
| casehub RAG wiring with tenancy and hybrid search | `rag` — this module |

---

## Shared with Hortora

`inference-api`, `inference-runtime`, `inference-tasks`, `inference-splade`, and `inference-inmem` have zero casehub, Quarkus, Spring, or LangChain4j dependencies. [Hortora](https://github.com/Hortora/spec) uses these modules directly for SPLADE hybrid search and cross-encoder reranking in their own stack.

The `rag-*` modules are casehub-specific and are not shared. Both projects wire LangChain4j RAG independently for their own runtime and domain model.

---

## Status

Scaffold — no source code yet. Design agreed between casehub and Hortora. Pending prototype validation: ONNX Runtime JNI + HuggingFace Tokenizers JNI in a Quarkus native image binary on macOS ARM. This gates the Quarkus native deployment path for both projects.

Epics: [#1 Scaffold](https://github.com/casehubio/neural-text/issues/1) ✅ · [#2 Native Image Gate](https://github.com/casehubio/neural-text/issues/2) · [#3 SPI + Runtime](https://github.com/casehubio/neural-text/issues/3) · [#4 Task Adapters](https://github.com/casehubio/neural-text/issues/4) · [#5 Quarkus](https://github.com/casehubio/neural-text/issues/5) · [#6 SPLADE](https://github.com/casehubio/neural-text/issues/6) · [#7 RAG](https://github.com/casehubio/neural-text/issues/7)

---

## Documentation

- [Platform Architecture](https://github.com/casehubio/parent/blob/main/docs/PLATFORM.md)
- [Deep Dive](https://github.com/casehubio/parent/blob/main/docs/repos/casehub-neural-text.md)
- [Architecture & Delivery Plan (ARC42STORIES)](https://github.com/casehubio/neural-text/blob/main/ARC42STORIES.MD)
- [AI Fusion Brief](https://github.com/casehubio/parent/blob/main/docs/specs/2026-06-03-ai-fusion-hybrid-fact-space.md)
- [ONNX Inference Brief](https://github.com/casehubio/parent/blob/main/docs/specs/2026-06-03-standalone-rag-retrieval-brief.md)

## Tracking

- [casehubio/parent#158](https://github.com/casehubio/parent/issues/158) — casehubio/neural-text (onnx inference)
- [casehubio/parent#164](https://github.com/casehubio/parent/issues/164) — casehub-rag (LangChain4j RAG integration)
- [Hortora/spec#15](https://github.com/Hortora/spec/issues/15) — Hortora alignment
