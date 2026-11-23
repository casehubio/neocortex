## D1: Dissonance scope

**Choice:** Belief-belief + belief-behavior, with self-concept tension as an emergent property of CAPS settling
**Alternatives:**
- Belief-belief only — misses the most psychologically potent cases (Aronson 1969 self-consistency)
- Full explicit self-concept modeling — unnecessary; self-concept is already represented through two complementary mechanisms: CAPS mediating nodes (self_worth, self_efficacy) capture dimensional self-regard, while Belieflike nodes ("I am loyal", "I am moral") capture narrative identity-beliefs (Aronson 1969)
**Rationale:** Festinger (1957) and Shultz & Lepper (1996) treat all cognitions uniformly in the constraint network. Self-concept tension emerges through two mechanisms: (1) contradictory beliefs activate competing CAPS mediating nodes (dimensional self-regard), and (2) Belieflike identity-beliefs are contradicted by behavior or other beliefs (narrative self-concept per Aronson 1969). Both contribute to dissonance but through different pathways — CAPS captures whether the character values themselves, Belieflike nodes capture whether specific identity-beliefs are violated. Limiting scope to belief-belief only would miss the strongest dissonance signals.
**Trade-offs:** Belief-behavior detection requires text representation of behavioral attractors for LLM comparison
**Sources:** Festinger 1957, Aronson 1969, Shultz & Lepper 1996, Cooper & Fazio 1984, caps-topology.yaml mediating nodes
**Exploration:** deep-analysis
**Status:** revised
**Revised in:** R1 — qualified self-concept claim to distinguish dimensional self-regard (CAPS mediating) from narrative identity-beliefs (Belieflike nodes) (R1-11)

## D2: Detection mechanism

**Choice:** LLM-based detection producing structured output
**Alternatives:**
- NLI classifier (ONNX) — fast and deterministic but misses subtle semantic contradictions ("I value loyalty" vs "I would betray anyone for power"); false positives harm character coherence
- Tiered NLI + LLM escalation — adds complexity without clear benefit in first iteration
- Structural + NLI — catches edge-type contradictions but not semantic ones
**Rationale:** Neocortex design principle: semantic judgment uses LLMs; mechanical pipelines consume the structured values LLMs produce. "Do these two beliefs contradict?" is fundamentally a semantic judgment. The existing pattern (LlmSituationClassifier, LlmAppraisalStrategy, BeliefRevisionPhase) consistently uses LLMs for this class of problem.
**Trade-offs:** LLM call cost during consolidation; non-deterministic. Mitigated: consolidation is periodic, not per-turn.
**Sources:** LlmSituationClassifier pattern, BeliefRevisionPhase pattern, NliClassifier in inference-tasks
**Exploration:** deep-analysis
**Status:** captured

## D3: Pipeline placement

**Choice:** New consolidation phase at @Priority(24), after SelfStandardDerivationPhase (23)
**Alternatives:**
- @Priority(20-21) — slots occupied by RelationalSchemaEvolutionPhase (20) and DriveProfileDerivationPhase (21)
- Extend BeliefRevisionPhase (@Priority(14)) — conflates evidence-driven revision with dissonance-driven tension; runs before BehavioralSynthesisPhase so no fresh attractors
- CognitionTickParticipant — per-interaction cost too high; detection is periodic not per-turn
**Rationale:** At @Priority(24), the phase has access to: fresh behavioral attractors from BehavioralSynthesisPhase (19), relational schema state from RelationalSchemaEvolutionPhase (20), drive profiles from DriveProfileDerivationPhase (21), and self-standards from SelfStandardDerivationPhase (23). Self-standards are directly relevant to Aronson-style self-concept dissonance ("I see myself as moral" contradicted by behavior). Shares priority with MergeDetectionPhase (24), which has no data dependency in either direction. Clear contract with BeliefRevisionPhase (14): evidence→belief contradictions are handled by BeliefRevision (revises beliefs), belief→belief and belief→behavior contradictions are handled by the dissonance phase (creates tension state).
**Trade-offs:** Dissonance detection is periodic rather than instant; a new belief won't trigger dissonance until the next consolidation tick
**Sources:** BehavioralSynthesisPhase @Priority(19), RelationalSchemaEvolutionPhase @Priority(20), DriveProfileDerivationPhase @Priority(21), SelfStandardDerivationPhase @Priority(23), MergeDetectionPhase @Priority(24), ConsolidationPhase SPI
**Exploration:** quick
**Status:** revised
**Revised in:** R1 — slots 20-21 occupied; moved to @Priority(24) for access to self-standards and all earlier outputs (R1-01)

## D4: Effect path

**Choice:** Two-layer same-tick effect: PAD adjustment + DissonancePromptSection + persistent tension state; CAPS behavioral effects are indirect, mediated through the prompt section's influence on conversation behaviour
**Alternatives:**
- Three-layer with direct CAPS settling — requires a second `capsEngine.settle()` after BehavioralSynthesisPhase has already settled and saved state; creates double write-cycle, invalidates cached `lastSettlingResults`/`lastInputActivations`, and produces undefined interaction between experience-derived and dissonance-derived attractors
- PAD + prompt only — simpler but dissonance has no durable representation; tension disappears between ticks
- CAPS settling with new output nodes (cognitive_change, denial, suppression) — these nodes do not exist in the CAPS topology; they are EMA coping strategies (Marsella & Gratch 2009), not CAPS behavioral outputs; extending the topology to add non-behavioral nodes would be a category error
- Tension nodes with `source-memory-id` compatibility — would fake memory graduation provenance to enter BehavioralSynthesisPhase's pipeline; a category violation (tension is not a graduated memory)
- Extend `mergeDriveIntensities` with a dissonance-activation pathway — modifies BehavioralSynthesisPhase's structure, violating D6's constraint that existing phase structures remain unmodified
**Rationale:** Cooper & Fazio (1984) showed dissonance produces measurable arousal — PAD arousal increase captures felt tension. DissonancePromptSection renders tension narratively so the character can articulate what they feel. Persistent tension state (MindMap node with dissonance properties) provides durable representation consumed by the coping system (#595) during active conversation. CAPS behavioural effects are indirect: DissonancePromptSection influences character behaviour during conversation → behaviour creates new experiences → experiences are recorded and graduate via ExperienceConsolidationPhase (with `source-memory-id`) → BehavioralSynthesisPhase classifies and settles on a subsequent tick (2+ ticks, not next-tick). This indirect pathway is psychologically faithful — Festinger's theory describes dissonance reduction as a gradual process influenced by ongoing behaviour and social interaction, not an instant behavioural reconfiguration. `BehavioralSynthesisPhase.findUnprocessedGraduated()` filters for `source-memory-id` (from memory graduation) and `mergeDriveIntensities()` filters for `cognitiveKind: "drive-intensity"` — tension nodes would match neither, so direct CAPS entry is not available without modifying BehavioralSynthesisPhase.
**Trade-offs:** CAPS behavioural effects are delayed by 2+ consolidation ticks; PAD and the prompt section provide immediate felt and narrative effects within the same tick
**Sources:** Festinger 1957, Cooper & Fazio 1984, AppraisalTickParticipant PAD integration pattern, BehavioralSynthesisPhase.findUnprocessedGraduated() and mergeDriveIntensities() filter contracts, caps-topology.yaml output categories
**Exploration:** quick
**Status:** revised
**Revised in:** R1 — removed nonexistent CAPS output nodes (R1-02), eliminated double CAPS settling (R1-05). R2 — clarified actual temporal model for CAPS effects: indirect via prompt→conversation→experience→graduation, not direct next-tick entry (R2-01)

## D5: Resolution mechanism

**Choice:** Mechanical tension decay as interim resolution; designed for coping-driven resolution when #595 lands
**Alternatives:**
- Extend LlmAppraisalStrategy per-turn call with dissonance resolution assessment — creates parallel resolution system that duplicates EMA coping strategies (cognitive_change, denial, suppression are literally EMA coping strategies from #595); modifies a critical existing phase; couples periodic detection to per-turn appraisal
- Mechanical-only resolution (tick counting, threshold heuristics) — lacks semantic context, risks premature or inappropriate resolution
- Narrative-only resolution (LLM handles it in dialogue, no mechanical feedback) — dissonance state never actually updates; mechanical and narrative layers diverge
**Rationale:** Festinger's dissonance reduction strategies map directly to EMA coping strategies: "change a cognition" ≈ positive reinterpretation, "deny contradicting information" ≈ denial, "reduce importance" ≈ suppression/acceptance. Dissonance IS a stressor; coping IS how agents handle stressors. Building a parallel resolution system would create two independent strategy selectors for overlapping territory. The right design: dissonance produces persistent tension state → tension is a coping trigger for the EMA micro-loop (#595) → coping strategy effects naturally resolve the contradiction → next consolidation tick detects reduced tension. Until #595 is implemented, configurable time-based decay provides a temporary resolution pathway (tension strength decreases per consolidation tick at a configurable rate, resolved when below threshold).
**Trade-offs:** Creates a dependency on #595 for full resolution fidelity; mechanical decay is a placeholder. Independent implementability preserved — the dissonance spec is complete without coping, but designed for integration.
**Sources:** Festinger 1957 (dissonance reduction strategies), Marsella & Gratch 2009 (EMA coping taxonomy), issue #595 D1/D2/D5 (EMA strategies including denial, positive reinterpretation, suppression)
**Exploration:** deep-analysis
**Status:** revised
**Revised in:** R1 — eliminated parallel resolution system in favour of coping integration; removed LlmAppraisalStrategy extension (R1-03, R1-06, R1-10)

## D6: Architecture approach

**Choice:** Approach A — Explicit Detection → CAPS Injection
**Alternatives:**
- Approach B: Belief injection into existing CAPS flow — elegant emergence but attribution problem: when CAPS oscillates from multiple inputs simultaneously, you can't tell which beliefs caused it without LLM judgment anyway
- Approach C: Bayesian belief consistency network — doesn't leverage CAPS; adds parallel inference engine; issue explicitly says "maps directly to CAPS attractor dynamics"
**Rationale:** Follows established neocortex patterns (LLM→structured values→mechanical consumption). Directly implements Shultz & Lepper (beliefs as CAPS input activations, settling produces dissonance signal). Full attribution: you know exactly which beliefs are in tension. New consolidation phase + DissonancePromptSection; existing phase structures are unmodified (the revised D5 no longer extends LlmAppraisalStrategy).
**Trade-offs:** Extra LLM call during consolidation (periodic, not per-turn). Belief→CAPS activation mapping is a new capability requiring prompt engineering.
**Sources:** LlmSituationClassifier, BehavioralSynthesisPhase, Shultz & Lepper 1996
**Exploration:** deep-analysis
**Status:** revised
**Revised in:** R1 — corrected rationale: D5 no longer extends LlmAppraisalStrategy, so "doesn't modify existing critical phases" is now accurate (R1-07)

## D7: Iteration scope

**Choice:** Detection + tension + rendering + mechanical decay in one iteration; coping-driven resolution as follow-on integration with #595
**Alternatives:**
- Full loop including coping-driven resolution — creates hard dependency on #595; resolution signal format would be designed speculatively without observing actual dissonance patterns in production
- Detection + tension + rendering only (no decay) — tension persists indefinitely without any resolution pathway; characters accumulate dissonance with no release
**Rationale:** The iteration delivers a complete, independently testable system: LLM detects contradictions, tension state is created and rendered, PAD is adjusted, and mechanical decay provides a baseline resolution pathway. The team can observe actual dissonance patterns in generated text before specifying coping-driven resolution signals. When #595 lands, dissonance tension becomes a coping trigger and mechanical decay is supplemented by strategy-driven resolution.
**Trade-offs:** Mechanical decay is psychologically simplistic compared to coping-driven resolution; the full Festinger cycle requires #595 integration
**Sources:** Festinger 1957, issue #595 (coping strategies as resolution mechanism)
**Exploration:** quick
**Status:** revised
**Revised in:** R1 — narrowed scope; resolution loop deferred to coping integration rather than designed speculatively (R1-08, R1-03)

## D8: Module placement

**Choice:** Dissonance consolidation phase in `cognition` module
**Alternatives:**
- `mindmap-intelligence` — houses graph-structural operations (ExperienceConsolidation, SubThought, BehavioralSynthesis, MergeDetection, EdgeInference); dissonance detection is semantic judgment, not graph-structural
- New dedicated module — unnecessary; the phase follows the same pattern as existing cognition consolidation phases
**Rationale:** The `cognition` module houses semantic/psychological consolidation phases that use LLM judgment: BeliefRevisionPhase (belief contradiction detection), DriveAdaptationPhase (drive intensity adjustment), RelationshipStagePhase (relationship evolution), RelationalSchemaEvolutionPhase (relational schema changes). Dissonance detection is a semantic judgment operation (LLM evaluates whether beliefs contradict each other or contradict behavior) that fits this pattern. The revised D4 requires no direct CapsEngine access — PAD adjustment uses the cognition-api's established mechanisms, and persistent tension state is written to MindMap (available through the standard MindMapStore dependency).
**Trade-offs:** None significant; follows established module boundaries
**Sources:** cognition module phases (BeliefRevisionPhase, DriveAdaptationPhase, RelationshipStagePhase, RelationalSchemaEvolutionPhase), mindmap-intelligence module phases (ExperienceConsolidationPhase, BehavioralSynthesisPhase, MergeDetectionPhase)
**Exploration:** surfaced by review
**Status:** captured

## D9: Coping integration strategy

**Choice:** Dissonance tension as a coping trigger consumed by EMA coping model (#595); mechanical decay as interim resolution until coping lands
**Alternatives:**
- Parallel resolution system — dissonance phase implements its own resolution detection (cognitive_change, denial, suppression) independently of coping. Creates dual strategy selectors for overlapping territory; resolution strategies ARE EMA coping strategies
- Full deferral to coping — dissonance spec does not include any resolution mechanism, waiting entirely for #595. Leaves characters with permanently accumulating tension until coping is implemented
- Dissonance subsumes coping — dissonance phase handles all coping behavior. Incorrect scope expansion; most coping handles stressors unrelated to dissonance
**Rationale:** Festinger's dissonance reduction strategies map directly to EMA coping strategies: "change a cognition" ≈ positive reinterpretation (COGNITIVE_CHANGE in Gross), "deny contradicting information" ≈ denial (direct narrative transformation in EMA), "reduce importance" ≈ suppression/acceptance (RESPONSE_MODULATION in Gross). Dissonance is a stressor; EMA coping is how agents handle stressors. The integration: dissonance phase writes persistent tension state → tension is consumed by EMA micro-loop (#595) as a coping trigger → coping strategy effects naturally resolve the contradiction (reinterpretation changes the belief, denial refuses to acknowledge, avoidance withdraws from contradiction-triggering situations) → next consolidation tick detects reduced belief tension. Mechanical decay (configurable rate per consolidation tick) ensures dissonance doesn't persist indefinitely before #595 is implemented.
**Trade-offs:** Full resolution fidelity requires #595; mechanical decay is a placeholder. The dissonance spec must document the integration contract (tension state format, coping trigger protocol) so #595 can consume it.
**Depends on:** D4 (persistent tension state), D5 (resolution mechanism)
**Sources:** Festinger 1957 (dissonance reduction = motivated coping), Marsella & Gratch 2009 (EMA coping taxonomy), issue #595 decisions D1/D2/D5
**Exploration:** surfaced by review
**Status:** captured
