## D1: Module location — REVISED after decision review

**Choice:** Cross-repo split: trajectory grouping utility in neocortex `memory-core`, LLM-backed synthesizer in blocks
**Original choice:** New neocortex module `memory-reflection-llm`
**Revision trigger:** Decision review identified contradiction with issue-345 SPI-inversion pattern: neocortex = cognitive substrate (mechanical, storage, retrieval), blocks = cognitive orchestration (LLM access). MindMapExtractor/CommunitySummaryPhase predate this boundary; they are legacy exceptions, not precedent.
**Rationale:** Neocortex stays LLM-free. TrajectoryGrouper is pure Java infrastructure (grouping, ordering, classification) — belongs in neocortex. LlmReflectionSynthesizer uses AgentProvider for LLM access — belongs in blocks per the established SPI-inversion pattern.
**Trade-offs:** Cross-repo coordination; blocks depends on neocortex's TrajectoryGrouper
**Sources:** Issue-345 SPI-inversion pattern, contributor guide, decision review R1-01
**Exploration:** quick → revised after review
**Status:** revised

## D2: Failure vs success classification

**Choice:** Pre-classify using `outcome-status` attribute when present, default to NEUTRAL when absent. Failure trajectories rendered first in prompt. LLM instruction to prioritise failure-derived heuristics provides secondary classification from descriptions.
**Alternatives:**
- Let the LLM classify from natural language descriptions — simpler code but less reliable, wastes tokens on classification the system already did
**Rationale:** outcome-status is caller-provided metadata, not automatically set by ExperienceEvents. When present, it enables reliable pre-classification. When absent, the trajectory defaults to NEUTRAL but the LLM prompt still instructs failure-pattern analysis — effectively a hybrid approach.
**Trade-offs:** Classification quality varies based on how consistently callers set outcome-status
**Sources:** ExperienceAttributeKeys.OUTCOME_STATUS, ExperienceEvents.toMemoryInput(), arXiv:2603.24639, decision review R1-02
**Exploration:** quick
**Status:** captured

## D3: Trajectory grouping strategy

**Choice:** Group by `caseId`, order by timestamp, sub-sequence by turn-id
**Alternatives:**
- Group by `subject` attribute — cross-task patterns but harder cause→effect chains
- Group by `capability` attribute — capability-specific heuristics but loses task context
**Rationale:** Each case represents a coherent task/conversation. Grouping by case gives the LLM a complete narrative: what was observed, what actions were taken, what outcomes resulted. The data model already supports this naturally.
**Trade-offs:** Misses cross-case patterns (e.g. "every time we interact with X, Y happens") — could be addressed at level 2+ reflections. Sparse per-case data possible when maxSourceMemories is low relative to active cases — caller controls this via the limit parameter.
**Sources:** Memory.caseId(), ExperienceQuery.forAgent()
**Exploration:** quick
**Status:** captured

## D4: LLM output structure

**Choice:** Conditional rules — "if X then Y" heuristics
**Alternatives:**
- Free-form insights — more flexible but harder to retrieve and apply
- Hybrid (structured + free-form context) — more complex prompt, context field adds noise not signal for the consuming LLM
**Rationale:** The consuming LLM applies conditional rules effectively without needing context/reasoning. Retrieval precision is better with focused text. The +14.3% improvement from arXiv:2603.24639 comes from this format specifically. sourceMemoryIds provides the audit trail. Higher-level meta-observations are a level 2+ concern.
**Trade-offs:** Forces all insights into if/then structure; some patterns may not fit neatly — acceptable for level 1
**Sources:** arXiv:2603.24639 (conditional heuristic format + failure prioritisation), Generative Agents (Park et al. — general reflection architecture, uses free-form not conditional)
**Exploration:** deep-analysis
**Status:** captured

## D5: Prompt strategy

**Choice:** Single-pass extraction — one LLM call per reflect() invocation
**Alternatives:**
- Two-pass (summarise then extract) — better for large memory sets but doubles latency/cost; maxSourceMemories already bounds input
- One call per case — eliminates multi-case prompt complexity but multiplies API calls
**Rationale:** Simple, fast, cost-effective. Trajectory grouping and failure-prioritisation happen in Java before the call. maxSourceMemories parameter bounds input size.
**Trade-offs:** May produce lower quality heuristics if source memory count is very large — mitigated by the existing limit parameter. Multi-case single-pass prompt complexity increases with case count.
**Sources:** ReflectionOrchestratorCore.reflect() maxSourceMemories parameter
**Exploration:** quick
**Status:** captured

## D6: Dedup against existing reflections

**Choice:** Pass existing reflections as context to the LLM prompt so it avoids generating duplicates
**Alternatives:**
- Post-synthesis semantic dedup at the orchestrator level — more reliable but requires embedding model
- No dedup, rely on `since` parameter — simplest but produces growing corpus of near-duplicates on scheduled calls
**Rationale:** The LLM is already processing context — adding a "previously generated insights" section to the prompt is low-cost and effective. The `since` parameter limits source memories but doesn't prevent the same patterns from generating the same insights across invocations.
**Trade-offs:** LLM-based dedup is best-effort, not guaranteed. Prompt grows with existing reflection count — bounded by a configurable max.
**Sources:** ReflectionQuery.forAgent(), decision review R1-08
**Exploration:** quick
**Status:** captured
