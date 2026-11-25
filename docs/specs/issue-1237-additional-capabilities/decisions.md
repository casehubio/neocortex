## D1: NeedsCapability data source

**Choice:** Query MindMap COGNITIVE subgraph for need-satisfaction and drive-intensity nodes
**Alternatives:**
- Static derivation from CognitiveDerivationEngine — CDE has no deriveNeeds() method; would require new API surface for a constant-per-character result
- Drive memory query (CaseMemoryStore domain="drive") — requires cognition tick loop; duplicates DrivePromptSection
**Rationale:** Same pattern as GoalCapability — query persisted graph state. The cognition module's NeedsPyramidPromptSection reads these nodes; they persist after being written by the drive system, so cognitive-runtime can read them without the tick loop running. Gracefully returns null when no needs data has been persisted yet.
**Trade-offs:** Requires the cognition tick to have run at least once to populate the nodes. In a pure standalone deployment with no prior tick history, renders nothing.
**Sources:** NeedsPyramidPromptSection (cognition), MindMapStore.nodesIn() (mindmap-api), GoalCapability (cognitive-runtime)
**Exploration:** quick
**Status:** revised (R1-01: CDE has no needs derivation path)

## D2: PracticalAppraisalCapability data source

**Choice:** Query MindMap COGNITIVE subgraph for mood state nodes (PAD emotional state)
**Alternatives:**
- Render stored affect memories from CaseMemoryStore — bootstrapping gap: standalone cognitive-runtime has no component writing affect memories
- Accept AppraisalStrategy via Instance<T> — SPI is accessible (cognition-api), but CognitionRenderContext has no observation field for live appraisal
**Rationale:** Mood state persists in MindMap after being written by the mood orchestrator. Reading persisted mood gives current emotional context without invoking the full appraisal pipeline. Same "read persisted state" pattern as other capabilities.
**Trade-offs:** No live appraisal — only reads last-persisted mood. Requires mood orchestrator to have run at least once.
**Depends on:** D1 (same philosophy — read persisted state from MindMap)
**Sources:** MoodOrchestrator (cognition), MoodState/MoodEvents (memory-api), AffectTrajectoryDecorator (mindmap)
**Exploration:** quick
**Status:** revised (R1-02: affect memories don't exist in standalone; mood nodes do)

## D3: BlockTag assignments for new capabilities

**Choice:** Use existing BlockTag values — NeedsCapability→NEEDS(230), PracticalAppraisalCapability→APPRAISAL(220), InnerThoughtsCapability→REFLECTION(510), ConsolidationCapability→CONSOLIDATION(520)
**Alternatives:**
- Create new BlockTag values (NEEDS_STATIC, APPRAISAL_STORED) — adds enum values for capabilities that are semantically equivalent to their cognition counterparts
**Rationale:** Each tag was already defined for this semantic purpose. Dual-layer collision is handled by D5 (mutual exclusion via isActive).
**Trade-offs:** Requires D5 mutual exclusion to prevent duplicate blocks when cognition is also active.
**Depends on:** D5 (mutual exclusion mechanism)
**Sources:** BlockTag enum (cognition-api), existing prompt section BlockTag assignments
**Exploration:** quick
**Status:** revised (R1-03: acknowledged collision, resolved by D5)

## D4: ConsolidationCapability query strategy

**Choice:** Single query on COGNITIVE subgraph, group results by trait for structured rendering
**Alternatives:**
- Accept List<ConsolidationArtifact> — artifacts are produced by scheduler (mindmap-intelligence) and drained by cognition; no accessible source from cognitive-runtime
- Separate per-trait queries — unnecessary since traits are on nodes from a single query
**Rationale:** Graduated experience nodes and curiosity description nodes persist in COGNITIVE subgraph with known traits. One query, then group by trait for rendering. Exact trait names to be verified against codebase in spec.
**Trade-offs:** Couples to consolidation phase trait conventions (string literals). If a phase renames a trait, the renderer breaks silently. Mitigated by extracting trait constants to a shared location.
**Sources:** MindMapStore.nodesIn() (mindmap-api), SubThoughtConsolidationPhase (mindmap-intelligence), CuriosityDescriptionGenerationPhase (mindmap-intelligence)
**Exploration:** quick
**Status:** revised (R1-05: "Summary" trait to be verified)

## D5: Mutual exclusion between cognitive-runtime and cognition prompt sections

**Choice:** CognitiveAvatarProducer passes a boolean flag at construction time indicating whether cognition is active; each new capability's isActive() checks this flag
**Alternatives:**
- Distinct BlockTag names per layer — requires new enum values, semantic duplication
- Merge at aggregation point — requires aggregator to know precedence, breaks independence principle
- Instance<CognitionCore> check per capability — requires each capability to inject Instance<T>, more complex
**Rationale:** Simplest approach. CognitiveAvatarProducer already uses Instance<T>.isResolvable() for infrastructure deps. It already knows whether cognition-layer SPIs are available. Passing a single boolean to the new capabilities avoids each capability needing its own Instance injection.
**Trade-offs:** A boolean is coarse — it disables all 4 capabilities together. If a future deployment wants some lightweight capabilities active alongside cognition, this needs refinement. Acceptable for now since the 4 new capabilities are all degraded versions of cognition sections.
**Sources:** CognitiveAvatarProducer (cognitive-runtime), CognitionCore (cognition), Instance<T> pattern
**Exploration:** quick
**Status:** captured (R1-03/R1-07: dual-layer collision)

## D6: InnerThoughtsCapability data source

**Choice:** CaseMemoryStore.scan() with domain="reflection" — same pattern as MemoryCapability
**Alternatives:**
- Accept ReflectionSynthesizer via Instance<T> and invoke at render time — heavy for a prompt section, belongs in the tick loop
**Rationale:** Reflection memories are stored with domain="reflection" by ReflectionService. Scanning them is identical to how MemoryCapability scans domain="experience". Zero new dependencies.
**Trade-offs:** Only renders previously stored reflections. In standalone without cognition tick, renders nothing until something writes reflection memories.
**Sources:** CaseMemoryStore.scan() (memory-api), ReflectionQuery (memory-api), MemoryCapability (cognitive-runtime)
**Exploration:** quick
**Status:** captured (R1-04: data source was undecided)
