# Decisions — #547 Edge Extraction Scaling

## D1: Scope — both pipelines

**Choice:** Fix both the Python biographical pipeline and the Java runtime pipeline
**Alternatives:**
- Python only — faster delivery but leaves the runtime graph with the same sparsity problem
- Java only — fixes runtime but doesn't improve corpus quality for biographical extraction
**Rationale:** Same structural problem (cross-passage/cross-turn isolation) manifests in both pipelines. Same fix pattern applies at different layers.
**Trade-offs:** More code surface than single-pipeline fix, but the two implementations share the same conceptual design.
**Sources:** `biographies/scripts/extract_structure.py` (Python pipeline), `mindmap-intelligence/.../MindMapExtractor.java` (Java pipeline), issue #547 metrics (209 edges / 276 nodes = 0.76 ratio)
**Exploration:** quick
**Status:** captured

## D2: Inference method — hybrid (heuristic candidates → LLM edge typing)

**Choice:** Programmatic co-occurrence analysis identifies candidate entity pairs, then a focused LLM call generates edge types for top candidates
**Alternatives:**
- LLM only — highest quality but O(n²) in entity count, too slow for 276+ entities without pre-filtering
- Heuristic only — fast but can't infer implicit relationship types, limited to detectable co-occurrence patterns
**Rationale:** Biographical extraction is a one-off cost, so quality matters most. Hybrid gives the same quality as pure LLM but faster — heuristics filter out the ~90% of pairs with no plausible relationship, letting the LLM focus on promising candidates.
**Trade-offs:** Misses relationships with zero co-occurrence signal. Acceptable because convergence extraction already handles synthesis-level inference for non-co-occurring entities.
**Sources:** Issue #547 investigation questions, `extract_structure.py:build_entity_context()`, `MindMapExtractor.java:retrieveContext()`
**Exploration:** quick
**Status:** captured

## D3: Java pipeline layer — consolidation phase

**Choice:** New `EdgeInferencePhase` in the consolidation scheduler, not enhancement to MindMapExtractor
**Alternatives:**
- Enhanced MindMapExtractor — increase MAX_CONTEXT_NODES, smarter retrieval. Synchronous but narrower context window.
- Both — better extraction-time context AND consolidation catch-up. Most thorough but most code.
**Rationale:** Consolidation phase sees the full graph, not a 20-node window. Edge inference is effectively one-off per entity pair — once inferred and stored, it doesn't need re-inferring. Fits existing consolidation pattern (ExperienceConsolidation, MergeDetection, etc.).
**Trade-offs:** Edges aren't available until consolidation runs (async, periodic). Acceptable because edge inference is a background quality improvement, not a latency-critical path.
**Sources:** `mindmap-intelligence/.../consolidation/` (43 existing phases), `MindMapExtractor.java:MAX_CONTEXT_NODES=20`
**Exploration:** quick
**Status:** captured

## D4: Candidate identification strategy — co-occurrence

**Choice:** Co-occurrence in memories/passages as the primary candidate signal. Entities that appear together in the same memories or passages but have no direct edge are candidates.
**Alternatives:**
- Embedding similarity — cosine similarity between entity descriptions. Catches conceptual relationships but requires embedding model, more expensive.
- Full pairwise LLM scan — no pre-filtering. Highest quality but O(n²) is too slow for 276+ entities.
**Rationale:** Co-occurrence is the strongest signal for biographical data — entities mentioned together in the same memories almost always have a relationship. Fast, deterministic, and the LLM call is focused on a small candidate set.
**Trade-offs:** Misses conceptually related but non-co-occurring entities. Convergence extraction already handles that case.
**Sources:** `extract_structure.py:MEMORY_SYSTEM` (related_entities field), `MindMapExtractor.java:retrieveContext()`
**Exploration:** quick
**Status:** captured
