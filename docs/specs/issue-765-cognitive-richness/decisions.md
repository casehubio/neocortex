# Decisions — #825 Cognitive Search Budget

## D1: Parameter dimensionality

**Choice:** 4 orthogonal parameters — breadth, laterality, depth, convergence pressure
**Alternatives:**
- 2 dimensions (exploration/exploitation) — loses the ability to independently modulate within-dimension aspects (e.g., many threads vs. far threads)
- 3 parameters (breadth, depth, convergence) — laterality genuinely orthogonal to breadth per Guilford's fluency/flexibility distinction
**Rationale:** Guilford (1967) measures fluency (breadth) and flexibility (laterality) as independent cognitive operations. De Bono's lateral thinking is about distance, not count. Depth and convergence are independent — fixation has depth=∞, convergence=never, which is impossible if they're the same dimension. Personality drives them through fewer traits, but mood, motivation source, and task type distribute the budget differently across them.
**Trade-offs:** 4 parameters means more complex personality→budget mapping, but the extra resolution is real (not arbitrary). Each parameter has distinct literature grounding.
**Sources:** Guilford (1967) Structure of Intellect; De Bono (1970) Lateral Thinking; Grajzel et al. (2023) Big Five and DT meta-analysis; Davis & Nolen-Hoeksema WCST perseveration; issue #825 pathological boundary table
**Exploration:** deep-analysis
**Status:** captured

## D2: Module placement

**Choice:** Split — SearchBudget record in cognition-api (zero deps, type only); `deriveSearchBudget()` derivation pathway in CognitiveDerivationEngine (cognitive-index); tick-time dynamic modulation in SearchBudgetTickParticipant (cognition/)
**Alternatives:**
- All in cognition-api — type is zero-dep but derivation needs PersonalityFacets and DispositionAxes from cognitive-index, so derivation cannot live in cognition-api
- All in cognitive-index — type accessible to CDE but adds dependency from cognition→cognitive-index for prompt rendering
- Type in cognition-api, derivation in tick participant (cognition/) — bypasses the established CDE pattern; every other personality→operational-parameter derivation (personality, mood baseline, curiosity, graph structure, appraisal weights, habituation, expression styles, etc.) lives in CDE, not in tick participants
**Rationale:** Follows the established split pattern exactly. SearchBudget record is a data type — belongs in cognition-api alongside DriveProfile, ActionTendency, BlockTag. Baseline derivation is a personality→operational-parameter mapping — belongs in CDE alongside the 12 existing derivation pathways. Tick participant reads crystallized baseline and applies dynamic modulation (mood, drive intensity, goal urgency) — same as DrivePromptSection reading from DriveOrchestrator, which reads from consolidation-derived DriveProfile. Consumers (DriveOrchestrator, AppraisalTickParticipant, GoalProposalOrchestrator — all in cognition/, not cognition-api) access the type via their cognition-api dependency.
**Trade-offs:** Split across three modules. Acceptable — this is the established pattern for every cognitive parameter.
**Sources:** CognitiveDerivationEngine (12 existing pathways); DrivePromptSection → DriveOrchestrator → DriveProfile pattern; DriveProfileDerivationPhase (consolidation-time derivation)
**Exploration:** quick
**Status:** revised — derivation moved from tick participant to CDE following established pattern (R1-01)

## D3: Multiple motivation composition

**Choice:** No custom composition — read already-composed subsystem outputs
**Alternatives:**
- Dominant motivation wins — oversimplifies; Frijda's control precedence says one leads but others modulate, not that others are suppressed
- Weighted vector composition — no literature support for linear summation of motivations; CAPS model uses constraint-satisfaction settling, not arithmetic
- Layered (intensity from max, character from all) — closer to the literature but still imposes an artificial separation that the subsystems already handle
**Rationale:** Three convergent literatures: (1) CAPS connectionist resolution (Mischel & Shoda, 1995; Shoda & LeeTiernan, 2002) — recurrent network settles to satisfy multiple simultaneous constraints, (2) Frijda's control precedence (1986, 2007) — highest urgency claims priority, others reinforce/attenuate, (3) Schmidt & DeShon (2007) — resources shift toward largest discrepancy, modulated by incentive asymmetry. The existing subsystems (DriveOrchestrator → DriveProfile with compositeMotivation + dominantDrive; AppraisalTickParticipant → ActionTendency list; GoalPrioritizationPhase → per-goal priority) already perform principled composition internally. The SearchBudgetParticipant reads their outputs.
**Trade-offs:** Couples the search budget to the quality of upstream composition. If drive composition is wrong, the budget inherits the error. This is acceptable — fixing composition should happen at the source, not by adding a parallel mechanism.
**Sources:** Mischel & Shoda (1995) Psychological Review 102(4); Shoda & LeeTiernan (2002) PSPR 6(4); Frijda (1986) The Emotions; Frijda (2007) The Laws of Emotion; Schmidt & DeShon (2007) JAP 92(4) 928-941; Louro, Pieters & Zeelenberg (2007) JPSP 93(2) 174-193
**Exploration:** deep-analysis
**Status:** captured

## D4: Derivation chain — subsystem output → budget parameter

**Choice:** Literature-grounded mapping from existing subsystem outputs to 4 budget parameters, using PID-5 facets and DispositionAxes directly (not via synthetic Big Five scores)
**Derivation (operational — maps to codebase constructs):**
- **Breadth** ← `GraphStructureDefaults.connectiveBias` (high connectiveBias → broad associative search) + `dominantDrive==CURIOSITY` (Berlyne 1960) + positive mood valence (CABM 2021). PID-5 facets: low Risk Taking + low Impulsivity → conservative breadth; high Risk Taking → expansive. Theoretical grounding: Openness/Intellect (DeYoung 2015 CB5T, Grajzel et al. 2023 meta-analysis r=0.20 DT).
- **Laterality** ← PID-5 facets: low Withdrawal + low Intimacy Avoidance → open to distant associations; high Suspiciousness → constrained. `dominantDrive==CURIOSITY` + low threat appraisal (from ActionTendency list). High threat → collapse laterality to zero (Frijda approach/avoidance). Theoretical grounding: Openness aesthetic/perceptual sensitivity, "leaky cognitive filtering" (DeYoung 2015). Same CDE pattern as `deriveExpressionStyles` which operates directly on PID-5 facets.
- **Depth** ← `DispositionAxes.ruleFollowing` (high → systematic, deep search) + `dominantDrive==COMPETENCE` (Deci & Ryan 2000 SDT). PID-5 facets: low Impulsivity + low Risk Taking → thorough; high Impulsivity → shallow-and-move-on. Theoretical grounding: Conscientiousness ↔ low Disinhibition (Krueger et al. 2012 PID-5 dimensional alignment).
- **Convergence** ← `DispositionAxes.ruleFollowing` (high → goal-directed convergence) modulated by PID-5 facets: high Anxiousness + high Emotional Lability → convergence failure risk. Dynamic: MVT patch-leaving (Charnov 1976, Pirolli & Card 1999) + goal proximity × emotion (Louro et al. 2007). Pathological: high Anxiousness + HabituationState failure → convergence=0 (fixation/rumination). Theoretical grounding: Conscientiousness × inverse Neuroticism (Davis & Nolen-Hoeksema perseveration).
**Rationale:** Each mapping traces to published findings AND maps to specific codebase constructs (PID-5 facets in PersonalityFacet, axes in DispositionAxes, graph defaults in GraphStructureDefaults). Big Five trait names serve as theoretical grounding — the operational pathway works directly with PID-5 facets and DispositionAxes without constructing synthetic Big Five scores. This follows the pattern established by `CognitiveDerivationEngine.deriveExpressionStyles()`, which derives expression styles directly from PID-5 facet interactions.
**Trade-offs:** Extraversion (r=0.09 DT) and Agreeableness (context-dependent) are excluded — insufficient evidence for reliable mapping. If future research strengthens these, they can be added.
**Sources:** See individual citations per parameter above; Krueger et al. (2012) PID-5 to Big Five dimensional alignment (theoretical bridge, not computational step); CognitiveDerivationEngine.deriveExpressionStyles() (pattern for direct PID-5 facet derivation)
**Exploration:** deep-analysis
**Status:** revised — derivation chain made operationally precise using PID-5 facets + DispositionAxes directly (R1-02)

## D5: Intensity→budget function shape

**Choice:** Per-parameter activation functions — each budget parameter gets the function shape that matches its activation dynamics
**Functions:**
- **Breadth** — logistic (sigmoid), midpoint at drive-activation-threshold (0.5), steepness k tunable. Appetitive, smoothly graded: more curiosity/openness → proportionally more breadth.
- **Laterality** — logistic with threat-collapse floor: logistic above a threat threshold, clamps to zero below it. Models Frijda approach/avoidance collapse under high threat.
- **Depth** — logistic with lower steepness (k). Conscientiousness-driven systematic search may be more linearly graded with effort. Lower k produces near-linear behaviour in the middle range while preserving saturation bounds.
- **Convergence** — piecewise: logistic above a pathological threshold, floor at zero below it. Models the qualitative difference between normal convergence (graded) and pathological fixation/rumination (convergence collapses entirely when Anxiousness + HabituationState failure crosses the pathological threshold).
**Alternatives:**
- Single logistic for all parameters — cannot model both appetitive grading (breadth) and pathological collapse (convergence) with one k and midpoint
- Linear — no threshold effect, doesn't match human attention allocation
- Step function — too abrupt, no gradual transition
- Softplus (log(1 + exp(x))) — unbounded above; inappropriate because budget parameters are normalized [0,1], not unbounded resource quantities
**Rationale:** CapsSettler establishes the precedent: `Math.tanh()` for BIPOLAR nodes, `logistic()` for UNIPOLAR (CapsSettler.java lines 89-94). Different cognitive dynamics require different activation shapes. The search budget follows the same principle. Each parameter's function shape is grounded in its theoretical activation dynamics.
**Trade-offs:** More complex than a single function, but the complexity is real — it models genuinely different dynamics. Steepness k, midpoint, and pathological threshold are tunable per-agent via config; defaults must be calibrated empirically.
**Sources:** Mischel & Shoda (1995) Psychological Review 102(4); CapsSettler BIPOLAR/UNIPOLAR precedent; Davis & Nolen-Hoeksema perseveration (pathological convergence dynamics)
**Exploration:** quick
**Status:** revised — per-parameter activation functions replacing single logistic (R1-03)

## D6: Rendering architecture — consolidation + tick two-layer

**Choice:** Two-layer pattern: consolidation phase generates search style prose via LLM, crystallizes to MindMap; tick-time participant reads crystallized baseline and modulates with transient state; prompt section renders from modulated result.
**Alternatives:**
- Per-tick sub-LLM call — adds latency to every interaction, generates prose for content that changes slowly (personality baseline)
- Programmatic construction — combinatorial explosion across 4 parameters × motivation source × personality; too rigid for contextual variation
- Template library — explicitly rejected; impossible to cover all variations, becomes briefing rather than cognitive state
**Rationale:** Follows established pattern: CommunitySummaryPhase crystallizes LLM-generated summaries, DriveDescriptionGenerationPhase crystallizes LLM-generated drive prose, SelfStandardDerivationPhase crystallizes LLM-derived self-standards. Prompt sections read crystallized content at tick time. Search budget personality baseline changes at consolidation cadence, not per-tick. Dynamic modulation (drive intensity, mood, goal urgency) adjusts emphasis at tick time without regenerating prose. Rendering style grounded in Anthropic "Functional Emotions" paper (April 2026): cognitive state descriptions causally influence LLM behavior via internal representations — not metaphorical, empirically measured.
**Phase ordering:** SearchBudgetDescriptionGenerationPhase at `@Priority(28)` — runs after DriveDescriptionGenerationPhase (`@Priority(22)`, ensures drive profile is available) and after PrioritySignatureDerivationPhase (`@Priority(27)`, priority signatures inform convergence parameter), before CommunitySummaryPhase (`@Priority(30)`). Slots 23-27 are occupied (SelfStandard=23, MergeDetection=24, OtherModel=25, SchemaDiscovery=26, PrioritySignature=27); 28 is the first open slot after the drive/priority dependency chain. Consolidation pipeline currently has 6 LLM-calling phases identified by SessionPipeline field + ParallelLlmExecutor usage: BeliefRevisionPhase(14), DriveDescriptionGenerationPhase(22, 2 call sets: description + discrimination), SelfStandardDerivationPhase(23), OtherModelDerivationPhase(25), PrioritySignatureDerivationPhase(27), CommunitySummaryPhase(30). Adding SearchBudgetDescriptionGenerationPhase makes 7 LLM-calling phases (8 call sets). Pipeline runs asynchronously on a daemon thread (ConsolidationScheduler, default 5-minute interval), so linear latency accumulation does not block the tick loop.
**Trade-offs:** Crystallized prose may be stale if personality evolves between consolidation runs. Acceptable — personality changes slowly, and the dynamic tick-time modulation handles transient state. Under pathological states (rumination, fixation), the crystallized baseline may describe a normal search style while tick-time modulation narrows to pathological parameters. This is correct behaviour: the tick-time layer can override emphasis entirely (convergence→0 for fixation), and the staleness is bounded by the consolidation interval (configurable, default 5 minutes). Pathological states are transient by definition — the next consolidation run crystallizes the updated state.
**Sources:** Anthropic (2026) "Emotion Concepts and their Function in a Large Language Model"; existing consolidation phase pattern in mindmap-intelligence; LlmAppraisalStrategy pattern in cognition/; ConsolidationScheduler @Priority ordering
**Exploration:** deep-analysis
**Status:** revised — phase ordering corrected to @Priority(28) avoiding collision, LLM caller list corrected (R1-06, R2-01)

## D7: Prompt ordering — BlockTag placement and tier assignment

**Choice:** New BlockTag SEARCH_BUDGET(415) in Strategy tier (Tier 4), between STRATEGY(410) and ATTENTION(420). Section calibration tier: **Contextual** (not Supplementary).
**Alternatives:**
- Supplementary tier (like Strategy neighbour) — under high arousal, section would be suppressed entirely. Wrong: the agent needs narrowed search guidance under high arousal (Frijda approach/avoidance collapse → collapse laterality, increase convergence pressure), not no guidance at all
- Core tier — search budget is not somatic/identity-level; it's situational cognitive guidance
**Rationale:** The search budget shapes how the agent approaches problem-solving — this is Strategy tier (400s). It follows strategy (what you're trying to do) and precedes attention (what you're attending to), which is the correct causal order: search style shapes what you attend to. Contextual tier ensures the section is present under high arousal, where its content already reflects arousal-driven narrowing via the tick-time modulation from D6. The section is absent only when there is no active search context (e.g., idle agent), not when arousal is high.
**Trade-offs:** Adding a new BlockTag increases the prompt length. Mitigated by the instruction complexity cliff finding (TianPan 2026): the search budget renders as 1-2 sentences, well within the ~3 constraint reliable compliance window. With 25 CognitionPromptRenderer implementations, cumulative prompt complexity is a real concern — but each individual section's marginal cost is small, and the cognitive value of search guidance under high arousal outweighs the marginal compliance risk.
**Sources:** BlockTag ordering in cognition-api; Instruction Complexity Cliff (TianPan 2026); Section calibration spec (#400, D3) tier model (Core/Contextual/Supplementary)
**Exploration:** quick
**Status:** revised — added Contextual tier assignment (R1-04)

## D8: Convergence pressure / attention threshold interaction

**Choice:** Attention threshold crossing (progressive attention model, #381 D4) takes priority over convergence pressure. When an attention push interrupts a task with active convergence state, convergence pressure is checkpointed (not reset). The pushed attention items are evaluated through the search budget's breadth/depth parameters. If the original task remains active after attention processing, convergence pressure resumes from checkpoint.
**Alternatives:**
- Convergence pressure modulates the attention threshold — low convergence (keep exploring) raises the attention threshold, making it harder for external signals to interrupt. Risk: agent becomes deaf to urgent signals while deep in exploration
- Attention push respects convergence state — suppresses attention push if convergence pressure is low. Same risk: urgent signals blocked by task-level cognitive state
- Independent mechanisms with no interaction — current implicit state. Risk: conflicting directives (keep exploring vs. attend to new signal) confuse the LLM
**Rationale:** Attention threshold crossing represents new information that the within-task convergence mechanism cannot evaluate. The convergence pressure was computed without knowledge of the attention signal. The correct cognitive model is interruption with checkpoint: the agent processes the attention signal, then decides whether to resume the interrupted task (convergence resumes from checkpoint) or pivot. This matches Frijda's control precedence: the most urgent signal claims priority, but prior commitments are not erased.
**Trade-offs:** Checkpointing convergence state adds complexity to SearchBudgetTickParticipant. The convergence checkpoint must survive across tick boundaries and be associated with the interrupted task context. If the agent processes multiple attention pushes, convergence checkpoints may accumulate.
**Sources:** Progressive attention model (#381, D4) — adaptive percentile threshold; Frijda (1986, 2007) control precedence; D1 convergence pressure definition
**Exploration:** surfaced-by-review
**Status:** captured