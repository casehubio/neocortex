# Decisions — #399 Deductive Goal Formation

## D1: Holistic SPI vs per-axis enrichment

**Choice:** New holistic SPI — `DeductiveGoalFormationStrategy` called once per tick with full cognitive context
**Alternatives:**
- Enrich existing per-axis SPI — simpler change but per-axis framing doesn't fit holistic reasoning
- Replace existing SPI — clean but high blast radius, breaks existing per-axis callers
**Rationale:** Deductive formation reasons across the intersection of personality + beliefs + drives + mood. This is fundamentally different from per-axis drive mapping. A separate SPI keeps both patterns clean and composable.
**Trade-offs:** Two formation SPIs to maintain; orchestrator must merge results from both
**Sources:** GoalProposalOrchestrator.evaluateMappers(), DriveGoalFormationStrategy SPI, issue #399 description
**Exploration:** quick
**Status:** captured

## D2: Cognitive context scope

**Choice:** Full cognitive snapshot — orchestrator assembles context
**Alternatives:**
- Drive + descriptor only — can't reason from beliefs/memories
- Prompt-section reuse — zero new assembly but couples to prompt rendering
**Rationale:** Deductive reasoning requires beliefs (MindMap), memories (CaseMemoryStore), personality (DispositionAxes), drives, mood, and existing goals. The orchestrator already has Instance<> patterns for optional deps.
**Trade-offs:** Store queries add latency to the tick cycle; need to bound retrieval scope
**Sources:** CognitionCore.promptSections(), CognitiveProfile, MindMapStore, CaseMemoryStore
**Exploration:** quick
**Depends on:** D1 (holistic SPI needs holistic context)
**Status:** captured

## D3: LLM response format

**Choice:** Multi-goal JSON array per tick — one LLM call returns all deductive proposals
**Alternatives:**
- Single goal per call — simpler parsing, more LLM calls
- Free-form text + parser — flexible reasoning, unreliable parsing
**Rationale:** One LLM call per tick keeps latency bounded. JSON array maps directly to List<DeductiveGoalProposal>. Matches the LlmAppraisalStrategy pattern.
**Trade-offs:** JSON constraint may limit reasoning depth vs free-form
**Sources:** LlmAppraisalStrategy pattern (AgentProvider + JSON parse)
**Exploration:** quick
**Depends on:** D1 (SPI return type)
**Status:** captured

## D4: Axis binding for deductive goals

**Choice:** Multi-axis with primary — DeductiveGoalProposal carries Map<DriveAxis, Double> of contributing drives, strongest used as primary for pipeline compatibility
**Alternatives:**
- Best-fit single axis — reuse DriveGoalProposal, lossy
- Axis-free proposal type — requires downstream changes to GoalProposalTick
**Rationale:** Deductive goals emerge from multiple drive/personality intersections. Carrying the contribution map preserves the full signal for downstream evaluation (escalation, relevance checks) while the primary axis ensures pipeline compatibility.
**Trade-offs:** New proposal type to convert to DriveGoalProposal for the orchestrator pipeline
**Sources:** DriveGoalProposal record, GoalProposalTick pipeline
**Exploration:** quick
**Depends on:** D1 (separate SPI), D3 (JSON response maps to this type)
**Status:** captured

## D5: Execution ordering

**Choice:** Deductive formation runs before per-axis mappers
**Alternatives:**
- After per-axis — deductive may duplicate per-axis proposals
- Parallel then merge — most flexible but needs dedup logic
**Rationale:** Deductive goals reason from richer context and should take priority. Per-axis mappers see deductive results in existingGoals via reduced remainingCapacity, naturally avoiding duplicates.
**Trade-offs:** Per-axis mappers can't complement deductive proposals (only fill remaining capacity)
**Sources:** GoalProposalOrchestrator.doTick() flow
**Exploration:** quick
**Depends on:** D1 (separate call point in orchestrator)
**Status:** captured
