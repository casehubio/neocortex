# casehub-neocortex Examples

Eleven example modules demonstrate neural inference, RAG, CBR, knowledge modeling, cognition and corpus management. The [capability-to-test index](../docs/guides/examples-index.md) maps APIs to named tests and lists example coverage gaps.

## Quick Start

Use Java 21 or a compatible JDK.

```bash
# Smoke tests — no models, no Docker, seconds
mvn clean test -Pexamples-smoke

# Full tests — downloads real ONNX models, runs Testcontainers Qdrant
mvn clean test -Pexamples
```

## Modules

### example-text-analysis

Standalone Java — no Quarkus, no infrastructure. Load ONNX models and run inference.

| Demo | What it does |
|------|-------------|
| `NliDemo` | Premise + hypothesis → entailment / contradiction / neutral scores |
| `ZeroShotClassificationDemo` | NLI-based zero-shot text classification against candidate labels |
| `ScoringDemo` | Sentiment and toxicity scoring with `TextClassifier` |
| `RerankingDemo` | Cross-encoder reranking of 10 multi-domain search candidates |
| `SparseEmbeddingDemo` | SPLADE sparse embedding with top-weighted vocabulary terms |

### example-rag-pipeline

Quarkus + Testcontainers Qdrant — end-to-end corpus ingestion and hybrid search.

| Demo | What it does |
|------|-------------|
| `FlatCorpusIngestDemo` | Flat storage → `CorpusIngestionBinding` → `processBinding()` → incremental ingestion |
| `ZipCorpusIngestDemo` | ZIP storage → tombstones → rollover → `TOMBSTONES_ONLY` compaction |
| `HybridSearchDemo` | Dense + SPLADE sparse → RRF fusion → cross-encoder rerank → top-5 results |

### example-cbr

Six domain examples: AML investigations, clinical safety, PR reviews, IoT situations, contractor work and battle plans. Smoke tests use in-memory CBR; integration tests exercise Qdrant retrieval and plan persistence.

### example-knowledge-model

An ordered [knowledge-model walkthrough](example-knowledge-model/README.md) covers the Thing lifecycle, typed entities, trait emergence, `is()` / `as()`, dynamic types, TypeRegistry schemas and graph queries.

### Additional walkthroughs

| Module | Consumer workflow |
|---|---|
| [`example-cognitive-index`](example-cognitive-index) | Per-agent overlays, perspectival resolution and social comparison |
| [`example-agent-memory`](example-agent-memory) | Experiences, mood, engagement and personality-weighted memory |
| [`example-cbr-advanced`](example-cbr-advanced) | Temporal similarity, decay, hierarchical scope, outcomes and supersession |
| [`example-rag-analytics`](example-rag-analytics) | Retrieval statistics, query clusters, fusion and calibration |
| [`example-mindmap-intelligence`](example-mindmap-intelligence) | Graph analysis, communities, staleness and recurring thoughts |
| [`example-goal-cognition`](example-goal-cognition) | Goal graphs, expansion, pruning, affect and prioritization |
| [`example-corpus-management`](example-corpus-management) | Domain corpus lifecycles and federated search; enabled by `examples-smoke` |

## Capability Coverage

The [capability-to-test index](../docs/guides/examples-index.md) is the cross-module coverage matrix. Each row links an API or consumer workflow to a named test method. Demo-only coverage and capabilities without an example are listed separately there.

## Testing Strategy

Five test categories across two Maven profiles:

| Category | Tag | Profile | What it proves |
|---|---|---|---|
| Unit tests | `smoke` | `examples-smoke` | Example code wiring and logic |
| Edge cases | `smoke` | `examples-smoke` | Graceful handling of bad input |
| Happy path | `integration` | `examples` | Real models produce expected results |
| Correctness | `integration` | `examples` | Reranking reorders, SPLADE expands terms, metadata round-trips |
| Cross-domain | `integration` | `examples` | Same pipeline works for tech, news, and legal content |

## Models (full profile)

| Purpose | Model | Size |
|---|---|---|
| NLI + zero-shot classification | `Xenova/nli-deberta-v3-xsmall` | ~87 MB |
| Cross-encoder reranking | `Xenova/ms-marco-MiniLM-L-6-v2` | ~23 MB |
| Sentiment scoring | `Xenova/distilbert-base-uncased-finetuned-sst-2-english` | ~68 MB |
| SPLADE sparse embeddings | `onnx-models/Splade_PP_en_v1-onnx` | ~436 MB |
| Dense embeddings (RAG) | `Xenova/all-MiniLM-L6-v2` | ~80 MB |

## Architecture Justification

See [`docs/architecture-justification.md`](../docs/architecture-justification.md) for evidence-based rationale for each architectural layer.
