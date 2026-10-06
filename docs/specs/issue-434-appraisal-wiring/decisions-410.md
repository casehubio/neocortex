# Decisions — #410 BehavioralPromptSection

## D1: Data enrichment scope

**Choice:** Enrich BehavioralSynthesisPhase to persist source-count and strength-history metadata on attractor nodes, so the prompt section has meaningful data to render.
**Alternatives:**
- Render available data only — use current properties (strength, category, caps-node-id, PAD). Simpler but output would be thin without trend or depth context.
- Stub the fields — add fields to the prompt section but render placeholder text until synthesis catches up. Gets wiring done but defers real value.
**Rationale:** The issue specifically calls for trend direction (stable/strengthening/fading) and source experience count. Without enrichment, the rendering is just "category (strength)" which provides no behavioral guidance to the LLM.
**Trade-offs:** Small scope increase — BehavioralSynthesisPhase needs two additional properties per attractor node.
**Sources:** GitHub issue #410 (rendering requirements), BehavioralSynthesisPhase.java (current storage)
**Exploration:** quick
**Status:** captured

## D2: Trend detection mechanism

**Choice:** Previous-strength property — store `previous-strength` alongside `strength` on each EMA update. Trend = sign(current - previous). Sufficient for stable/strengthening/fading classification.
**Alternatives:**
- Timestamped strength history — richer trend analysis but unbounded property growth and complex management
- Separate affect memories — store snapshots in CaseMemoryStore domain="behavioral". Clean separation but heavyweight for a simple trend signal
**Rationale:** A single extra property captures enough for the three-state classification the prompt section needs. The EMA already smooths noise — comparing current to previous EMA gives a reliable trend direction.
**Trade-offs:** Cannot detect rate of change or reversals. Sufficient for prompt rendering; if richer trend analysis is needed later, can upgrade without changing the prompt section.
**Sources:** BehavioralSynthesisPhase.java:220 (EMA update logic)
**Exploration:** quick
**Status:** captured

## D3: Module location

**Choice:** cognition module — alongside all other prompt sections (MoodPromptSection, AppraisalPromptSection, etc.).
**Alternatives:**
- mindmap-intelligence — co-located with BehavioralSynthesisPhase (data producer). Breaks the pattern — no other prompt section lives outside cognition.
**Rationale:** All CognitionPromptRenderer implementations live in the cognition module. The section is a consumer of MindMap data, not a producer. Following the established pattern.
**Trade-offs:** None significant — cognition already depends on mindmap-api for CharacterDrivePromptSection.
**Sources:** cognition/prompt/ package (all existing sections), CharacterDrivePromptSection.java, NeedsPyramidPromptSection.java
**Exploration:** quick
**Status:** captured

## D4: Data access pattern

**Choice:** Direct MindMapStore injection — query BEHAVIORAL subgraph filtered by agent-id. Same pattern as CharacterDrivePromptSection and NeedsPyramidPromptSection.
**Alternatives:**
- New BehavioralOrchestrator SPI — abstracts the MindMap query behind an orchestrator interface. Consistent with MoodOrchestrator/DriveOrchestrator but adds an SPI for a simple query with no orchestration logic.
**Rationale:** The data access is a simple subgraph query with property filtering. No orchestration, no state management, no cross-store coordination. An SPI would be ceremony without substance.
**Trade-offs:** If behavioral rendering later needs complex orchestration (e.g., combining attractors with gut signals), an orchestrator would need to be introduced. For now, YAGNI.
**Sources:** CharacterDrivePromptSection.java (MindMapStore injection pattern), NeedsPyramidPromptSection.java
**Exploration:** quick
**Status:** captured

## D5: Causal context rendering

**Choice:** Source node names only — render the names of source graduated nodes that fed the attractor. Provides context without LLM overhead.
**Alternatives:**
- LLM-generated causal summary — AgentProvider sub-LLM call per attractor per render. Richer output but adds latency and cost.
- No causal notes — render strength, trend, source count only. Simplest but the LLM has no "why" context for behavioral patterns.
**Rationale:** The LLM consumer needs to know *when* a pattern fires. Source node names provide situational activation context ("rooted in task abandonment and deadline pressure") without an expensive sub-LLM call on every render.
**Trade-offs:** Source node names may not always produce readable causal context. The prompt section should gracefully handle nodes without clear names.
**Depends on:** D1 (source-count enrichment provides the node linkage)
**Sources:** BehavioralSynthesisPhase.java:105-113 (input node → attractor mapping), MindMapQuery search API
**Exploration:** quick
**Status:** captured

## D6: Config flag

**Choice:** New `behavioralEnabled` flag in CognitionConfig — independent toggle for the behavioral prompt section.
**Alternatives:**
- Piggyback on consolidationEnabled — BEHAVIORAL subgraph is produced by consolidation. Simpler but couples rendering to consolidation toggle.
**Rationale:** Every other prompt section has its own config flag. Behavioral rendering is independently valuable — you might want attractors rendered without consolidation insights, or vice versa.
**Trade-offs:** Adds one more field to the already-large CognitionConfig record (23rd field). Manageable.
**Sources:** CognitionConfig.java (22 existing fields), CognitionCore.promptSections() (per-section gating pattern)
**Exploration:** quick
**Status:** captured

## D7: Rendering format

**Choice:** Evocative natural language, second person, no raw numbers. Match AppraisalPromptSection's style. Qualitative strength and trend baked into word choice.
**Alternatives:**
- Structured quantified list — "Strong completionist drive (0.82, strengthening, 14 sources)". Data-dense but reads as diagnostic readout, not cognitive state.
- Hybrid — natural language lead with parenthetical data. Longer.
**Rationale:** The consumer is an LLM agent. LLMs respond to natural language descriptions of state better than raw floats. "0.82" doesn't mean anything to a language model; "deeply ingrained" does. Trend as "strengthening" vs "fading" gives behavioral guidance. Source context as natural language ("rooted in X and Y") tells the LLM when to activate the pattern. Consistent with how all other cognitive prompt sections address the agent in second person.
**Trade-offs:** Less precise than numbers. The LLM can't compare "strong" to "moderate" as precisely as 0.82 to 0.45. But the rendering purpose is behavioral guidance, not quantitative analysis.
**Sources:** AppraisalPromptSection.java (evocative rendering pattern), DirectiveSection.java (second-person framing), MoodPromptSection.java (qualitative PAD rendering)
**Exploration:** quick
**Status:** captured

## D8: Gut signal scope

**Choice:** Scope #410 strictly to crystallized attractors. #409 will add transient gut signal rendering when it lands.
**Alternatives:**
- Include a gut signal slot — design the section with two zones (crystallized + transient) now, render only crystallized. Ensures #409 doesn't restructure the section later.
**Rationale:** Crystallized attractors and transient gut signals are fundamentally different data: different sources (BEHAVIORAL subgraph vs memory probe), different semantics (established personality vs momentary resonance), different rendering weight. Clean separation — #409 may extend this section or create its own.
**Trade-offs:** #409 might need to modify BehavioralPromptSection's structure. Acceptable — the section is simple enough that adding a second rendering zone is not a refactor risk.
**Sources:** GitHub issue #409 (gut feeling design), .plan queue (#410 before #409)
**Exploration:** quick
**Status:** captured
