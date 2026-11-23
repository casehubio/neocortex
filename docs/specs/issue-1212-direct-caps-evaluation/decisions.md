## D1: Architecture approach

**Choice:** Reorder DissonanceDetectionPhase before BehavioralSynthesisPhase and extend `mergeDriveIntensities()` to consume dissonance-derived CAPS activations
**Alternatives:**
- New DissonanceActivationPhase at @Priority(18) — clean separation but adds indirection and one-tick delay for newly detected tensions
- CapsActivationProvider SPI — cleanest abstraction for extensible activation injection, but YAGNI for a single new activation source
**Rationale:** `mergeDriveIntensities()` already handles external activation injection (drive→CAPS mappings via `DriveCompetitionDetector`). Dissonance activations follow the same pattern — they are CAPS input node activations that should be merged into the settling pass. The D6 constraint from #593 was scoped to that iteration; #1212 explicitly evaluates extending BehavioralSynthesisPhase.
**Trade-offs:** Couples DissonanceDetectionPhase and BehavioralSynthesisPhase via shared cache. Modifies the consolidation pipeline ordering (DissonanceDetectionPhase moves from @Priority(25) to @Priority(17)).
**Sources:** BehavioralSynthesisPhase.mergeDriveIntensities(), DriveCompetitionDetector.DRIVE_CAPS_MAPPINGS, D4/D6 from #593 decisions
**Exploration:** deep-analysis
**Status:** captured

## D2: Activation merge strategy

**Choice:** Inject both sides of each contradiction (capsActivationsA and capsActivationsB) into the CAPS activation map, letting the settler resolve the conflict through its constraint-satisfaction algorithm
**Alternatives:**
- Net activation (difference between competing maps) — loses the constraint-satisfaction dynamics that ARE the theoretical model of dissonance
- Strength-weighted strongest side — simpler but doesn't model the actual conflict; just biases toward one side
**Rationale:** Shultz & Lepper (1996) define dissonance as competing activations in a constraint network. The CAPS settler already handles competing inputs via excitatory/inhibitory connections — this is exactly what it was designed for. Injecting both sides lets the network settle to a resolution that reflects the full tension, not a pre-computed summary.
**Trade-offs:** More CAPS input nodes activated simultaneously may produce oscillation in marginal cases. Mitigated: CapsSettler has oscillation detection and converges within bounded iterations.
**Sources:** Shultz & Lepper 1996, CapsSettler.java (oscillation detection, convergence bounds), DissonanceDetectionPhase capsActivationsA/capsActivationsB fields
**Exploration:** quick
**Status:** captured

## D3: Pipeline priority slot

**Choice:** @Priority(17) for DissonanceDetectionPhase (moved from 25), shared with DriveAdaptationPhase
**Alternatives:**
- @Priority(18) — occupied by RelationshipStagePhase; sharing is equivalent
- Renumber existing phases to create a gap — more disruptive for no benefit when the phases have no data dependency
**Rationale:** Slots 17 (DriveAdaptationPhase) and 18 (RelationshipStagePhase) are both occupied. DissonanceDetectionPhase has no data dependency on DriveAdaptationPhase — they read independent inputs (beliefs/attractors vs drive state) and write independent outputs (tension nodes vs drive adaptation state). Arbitrary execution order between them is safe. Sharing priorities is established practice: #593 D3 originally shared @Priority(24) with MergeDetectionPhase.
**Trade-offs:** Dissonance detection now runs before BehavioralSynthesisPhase, so it no longer sees same-tick attractor changes. Behavioral attractors in the BEHAVIORAL subgraph are stable across ticks — they change only when BehavioralSynthesisPhase processes new graduated experiences. For contradiction detection, stale-by-one-tick attractors produce the same detection results as fresh ones, except in the rare case where a batch of emotionally-charged experiences shifts the attractor landscape within a single tick. In that edge case, the contradiction is detected one tick late — still much faster than the indirect pathway's 2+ tick delay.
**Depends on:** D1 (reorder approach requires priority change)
**Sources:** ConsolidationPhase priority assignments: ExperienceConsolidation(15), SubThought(16), DriveAdaptation(17), RelationshipStage(18), BehavioralSynthesis(19). MergeDetectionPhase shared @Priority(24) in #593
**Exploration:** quick → revised after decision review found priority collision (R1-01)
**Status:** revised

## D4: Activation coupling mechanism

**Choice:** SPI in cognition-api — `DissonanceActivationSource` interface with `activeTensions(String cacheKey) → List<TensionActivation>`. Implemented by DissonanceDetectionPhase in cognition. BehavioralSynthesisPhase (mindmap-intelligence) injects `Instance<DissonanceActivationSource>`.
**Alternatives:**
- Direct cache read via `Instance<DissonanceDetectionPhase>` — creates circular dependency: mindmap-intelligence → cognition → mindmap-intelligence
- MindMap node convention (cognitiveKind=dissonance-activation nodes) — durable and observable but unnecessary MindMap round-trip; activation maps are already on the tension nodes
- CDI event — decoupled but fragile ordering within same consolidation tick
- Move DissonanceDetectionPhase to mindmap-intelligence — avoids the SPI but puts semantic LLM-based detection in a graph-structural module, breaking module conventions
**Rationale:** Module dependency direction is `cognition → mindmap-intelligence → cognition-api`. Direct injection of `DissonanceDetectionPhase` from BehavioralSynthesisPhase would create a circular dependency. A small SPI in cognition-api respects the existing dependency graph. `TensionActivation` record carries `capsActivationsA`, `capsActivationsB`, and `strength` — the minimum needed for the merge.
**Trade-offs:** Adds one interface and one record to cognition-api. Minimal surface; the SPI is a single method.
**Depends on:** D1 (reorder ensures implementation runs first), D3 (priority ordering guarantees cache is populated)
**Sources:** mindmap-intelligence/pom.xml (depends on cognition-api, not cognition), cognition/pom.xml (depends on mindmap-intelligence), DissonanceDetectionPhase.activeTensions()
**Exploration:** quick → revised after self-review caught circular dependency
**Status:** revised

## D5: Activation scaling

**Choice:** Scale dissonance-derived CAPS activations by tension strength: `effectiveActivation = llmConfidence * tensionStrength`
**Alternatives:**
- Raw LLM confidences — ignores tension lifecycle; a decaying tension at strength 0.2 would produce the same CAPS input as a fresh strength-1.0 tension
**Rationale:** Tension strength already models the lifecycle of a contradiction (fresh=1.0, decaying toward 0.15 resolution threshold). Scaling activations by strength couples the CAPS behavioural effects to the same lifecycle. As a tension fades, its influence on CAPS settling fades proportionally. This prevents decaying tensions from producing disproportionate behavioural effects.
**Trade-offs:** Very weak tensions (strength near 0.15) produce near-zero activations; they're functionally invisible to CAPS. Acceptable: they're about to be resolved anyway.
**Depends on:** D2 (both sides injected; scaling applies to both)
**Sources:** DissonanceDetectionPhase tension strength lifecycle, additive decay model from #593
**Exploration:** quick
**Status:** captured

## D6: Prerequisite bypass — implement without production observation

**Choice:** Proceed with implementation based on theoretical analysis rather than waiting for production observation of #593's indirect pathway
**Alternatives:**
- Wait for production observation — deploy #593, observe indirect pathway behaviour, then decide whether direct pathway adds value. Preserves the issue's stated prerequisite.
**Rationale:** The issue stated "#593 must be implemented and observable in production first" as a prerequisite. #593 is now implemented and landed on main, but has not been observed in production. The user chose to proceed with implementation because: (1) the theoretical case is clear — Shultz & Lepper (1996) constraint satisfaction is literally what the CAPS settler implements, and the indirect path approximates it poorly by flattening competing activations into derivative experience classifications; (2) the implementation is small (1 SPI, 1 record, 1 method, 1 priority change) and low-risk; (3) both paths coexist, so the direct path can be observed alongside the indirect path in production.
**Trade-offs:** No production data confirms the indirect path is insufficient. The direct path may prove unnecessary if the indirect path produces rich enough behaviour in practice.
**Sources:** Issue #1212 body (prerequisite statement), user decision during brainstorming
**Exploration:** surfaced by decision review (R1-06)
**Status:** captured
