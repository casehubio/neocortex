# Edge Extraction Scaling — Design Specification

**Issue:** casehubio/neocortex#547
**Date:** 2026-11-20
**Parent:** casehubio/neocortex#538 (goal & motivation taxonomy extension)
**Status:** Design

---

## 1. Problem

Edge extraction is not scaling with entity growth. As entity extraction improved, the edge/node ratio dropped:

| Metric | Old corpus | New corpus |
|--------|-----------|------------|
| Nodes | 168 | 276 |
| Edges | 202 | 209 |
| Ratio | 1.20 | 0.76 |

Entities increased by 64% but edges only increased by 3%. The graph is getting sparser — relationships between entities are being missed.

**Root cause:** Both extraction pipelines suffer from isolation — edges can only be created between entities visible in the same extraction context:

- **Python biographical pipeline** (`biographies/scripts/extract_structure.py`): 3 LLM calls per passage. Edge extraction receives entity context from that passage only. Cross-passage edges depend on a weak convergence step that only processes generated memories, not the full entity set.

- **Java runtime pipeline** (`MindMapExtractor`): Single LLM call with up to 20 existing graph nodes as context (`MAX_CONTEXT_NODES = 20`). As the graph grows, 20 becomes an increasingly thin slice. No consolidation phase for edge inference.

The intra-passage entity context fix is already in place (entity context is passed to edge extraction in both pipelines). The remaining gap is **cross-passage / cross-turn edge inference**.

## 2. Solution

Add a hybrid cross-passage edge inference stage to both pipelines:

1. **Programmatic co-occurrence analysis** identifies candidate entity pairs — entities that appear together in memories or passages but have no direct edge
2. **Focused LLM call** generates edge types for top candidates, given their shared context

This is a one-off cost per entity pair in both pipelines (biographical extraction runs once; consolidation phase persists inferred edges permanently), so optimizing for quality over cost.

## 3. Component 1: Python Pipeline — Cross-Passage Edge Inference

### 3.1 Location

New function `infer_cross_passage_edges()` in `biographies/scripts/extract_structure.py`, called after all per-passage extraction completes and before the final output write.

### 3.2 Algorithm

**Step 1 — Build co-occurrence matrix:**

From `all_memories`: group by `related_entities` — entities that share a memory are co-occurring. From `all_entities`: entities from the same passage (`source_passages` overlap) are co-occurring.

Score each pair: `co_occurrence_count / (1 + existing_edge_count_between_them)`.

**Step 2 — Filter to candidates:**

Exclude pairs that already have a direct edge in `edge_dedup`. Rank by co-occurrence score. Take top-N (configurable via `--edge-inference-top-n`, default 50).

**Step 3 — LLM edge inference:**

New system prompt `CROSS_PASSAGE_EDGE_SYSTEM`. Given candidate pairs and their shared context (memory texts where they co-occur), the LLM infers edge type or "no relationship". Batch candidates into groups of ~20 pairs per LLM call. Uses existing `call_llm()` infrastructure.

**Step 4 — Merge results:**

Merge inferred edges into `edge_dedup` using existing dedup logic (lowercase source|target|type key). Tag with `extraction_pass: "edge-inference-cross-passage"`.

### 3.3 Prompt

```
You are a knowledge graph edge inference agent. Given pairs of entities
and the context where they co-occur, determine if a relationship exists.

For each pair, respond with:
- An edge: {"source": "...", "target": "...", "type": "relationship-type",
  "confidence_origin": "INFERRED", "rationale": "one sentence"}
- No edge: {"source": "...", "target": "...", "type": null,
  "rationale": "why no relationship"}

Relationship vocabulary:
  Kinship: child-of, sibling-of, parent-of
  Romantic: married-to, affair-with, divorced-from
  Professional: treated-by, taught-by, championed, collaborated-with
  Goal: pursues, enables, blocks, requires, contributes-to, decomposes-into
  Spatial: lives-at, located-in, at
  Creative: inspired-by, created-by, depicted-in
  Conceptual: embodies, exemplifies, contradicts, influenced-by
  Activity: participated, occasion
  Temporal: preceded-by, concurrent-with, succeeded-by
  Causal: caused-by, resulted-in, motivated-by

Respond with JSON only: {"edges": [...]}
```

### 3.4 Configuration

CLI argument `--edge-inference-top-n` (default 50) controls how many candidate pairs to send to the LLM per run.

### 3.5 Estimated size

~80 lines Python. No new files.

## 4. Component 2: Java Pipeline — EdgeInferencePhase

### 4.1 Location

New consolidation phase: `mindmap-intelligence/src/main/java/io/casehub/neocortex/mindmap/intelligence/consolidation/EdgeInferencePhase.java`

### 4.2 Priority

`@Priority(22)` — after ExperienceConsolidationPhase (15) which creates the experience nodes that feed co-occurrence, and before MergeDetectionPhase (24) which merges duplicate nodes.

### 4.3 Algorithm

**Step 1 — Find under-connected nodes:**

Query `MindMapStore` for nodes with degree below a configurable threshold (default: 1 edge). These are orphans or isolated nodes.

**Step 2 — Build co-occurrence from memories:**

Query `CaseMemoryStore` for memories that mention each under-connected node (via `subject` or `related_entities` attributes in experience/relationship domains). Group memories by shared entity pairs to build co-occurrence counts.

**Step 3 — Score and filter:**

Score: `co_occurrence_count / (1 + existing_edge_count)`. Take top-K candidates per consolidation run (configurable, default 30).

**Step 4 — LLM edge inference:**

Use `AgentProvider` via `Instance<AgentProvider>` with graceful degradation when unavailable (no-op). Send candidate pairs with their shared memory context. Parse JSON response into `ParsedRelationship` records using `ExtractionJsonParser`.

**Step 5 — Persist edges:**

Create `EdgeInput` records with:
- Confidence origin: `INFERRED`
- Provenance: `"edge-inference-consolidation"`
- No temporal bounds, no PAD

Use `store.addEdges()` for batch persistence.

**Step 6 — Cursor-based progress:**

Track which nodes have been processed via a sentinel node in TYPE_SYSTEM subgraph with a `edge-inference-cursor` property storing the last-processed timestamp. Same pattern as `ExperienceConsolidationPhase`. Nodes created or updated after the cursor are eligible for re-scanning.

### 4.4 CDI Wiring

```java
@ApplicationScoped
@IfBuildProperty(name = "casehub.mindmap.consolidation.edge-inference.enabled",
                 stringValue = "true", enableIfMissing = false)
```

Dependencies (all via `Instance<T>` for graceful degradation):
- `MindMapStore` — node queries, edge persistence
- `CaseMemoryStore` — memory co-occurrence queries
- `AgentProvider` — LLM inference

### 4.5 Configuration

```properties
# cognitive-pipeline-defaults.properties
casehub.mindmap.consolidation.edge-inference.enabled=false
casehub.mindmap.consolidation.edge-inference.degree-threshold=1
casehub.mindmap.consolidation.edge-inference.max-candidates=30
```

Config record: `EdgeInferenceConfig` via `@ConfigMapping(prefix = "casehub.mindmap.consolidation.edge-inference")`.

### 4.6 Estimated size

~200 lines Java + ~150 lines test.

## 5. Testing

### 5.1 Python

Empirical verification — run the pipeline on the Frida corpus and measure edge/node ratio improvement. Target: ratio above 1.0 (from current 0.76).

### 5.2 Java — EdgeInferencePhaseTest

Unit test with `InMemoryMindMapStore` + `InMemoryMemoryStore`:

| Test case | Setup | Expected |
|-----------|-------|----------|
| Orphan nodes with shared memory | Two nodes, zero edges, one memory mentioning both | Edge inferred between them |
| No shared memories | Two orphan nodes, separate memories | No edge created |
| Above degree threshold | Node has 2 edges (threshold=1) | Node skipped |
| No AgentProvider | `Instance<AgentProvider>` unsatisfied | Graceful no-op, no errors |
| Cursor persistence | Run twice | Second run skips already-processed nodes |
| Edge dedup | Candidate pair already has edge | Pair excluded from candidates |

Pattern: same as `ExperienceConsolidationPhaseTest`, `MergeDetectionPhaseTest`.

## 6. Summary

| Dimension | Python Pipeline | Java Pipeline |
|-----------|----------------|---------------|
| Where | `extract_structure.py` new function | `EdgeInferencePhase.java` new file |
| Trigger | After per-passage extraction | Consolidation scheduler |
| Candidates | Memory `related_entities` + passage co-occurrence | Memory subjects + `MindMapStore` neighbor queries |
| LLM | `call_llm()` via Anthropic SDK | `AgentProvider.invoke()` via CDI |
| Confidence | `INFERRED` | `INFERRED` |
| Provenance | `edge-inference-cross-passage` | `edge-inference-consolidation` |
| Config | CLI arg `--edge-inference-top-n` | `@IfBuildProperty` + `@ConfigMapping` |
| New code | ~80 lines Python | ~200 lines Java + ~150 lines test |

## References

- `biographies/scripts/extract_structure.py` — Python biographical extraction pipeline
- `mindmap-intelligence/.../MindMapExtractor.java` — Java runtime extraction
- `mindmap-intelligence/.../consolidation/ExperienceConsolidationPhase.java` — cursor pattern reference
- `mindmap-intelligence/.../consolidation/MergeDetectionPhase.java` — co-occurrence scoring reference
- `mindmap-intelligence/.../consolidation/CommunitySummaryPhase.java` — LLM-in-consolidation pattern reference
- `specs/2026-10-11-goal-motivation-model-extension-design.md` — parent epic design
- casehubio/neocortex#547 — edge extraction not scaling with entity growth
- casehubio/neocortex#538 — parent epic: goal & motivation taxonomy model extension
