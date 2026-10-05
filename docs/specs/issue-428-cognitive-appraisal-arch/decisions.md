# Decisions — #428 Cognitive Appraisal Architecture

## D1: OCC-Scherer relationship

**Choice:** Scherer's SECs provide the appraisal process pipeline; OCC's taxonomy classifies the emotional output. The four SECs (Relevance, Implication, Coping, Normative) structure the sequential appraisal computation. OCC's event/agent/object branches and emotion types provide the classification vocabulary for mapping SEC output patterns to named emotions. The existing GoalAppraisal/ActionAppraisal SPIs in mindmap-api remain as-is — a SchererAppraisalStrategy implementation in cognition may delegate to them internally (cognition already depends on mindmap-api), but they are not moved or subsumed.
**Alternatives:**
- Replace OCC with Scherer — supersede #383 entirely, losing the structured event/agent/object taxonomy and the validated heuristic scoring
- Parallel paths — independent systems merged at mood level, risking incoherent emotional state
- Lazarus over Scherer — two-phase model (primary/secondary appraisal) maps naturally to LLM reasoning (issue #428 body notes this). Simpler than Scherer's four SECs, stronger empirical support for specific emotions via Core Relational Themes. Rejected because: the four-SEC decomposition provides finer-grained pipeline stages for mixing computational and LLM-backed implementations per stage (D3). Lazarus's two phases are coarser — the research (D7) may revisit this if LLM agents don't benefit from per-SEC granularity
- Scherer "wraps" OCC (original D1 framing) — incorrect theoretical relationship. Scherer and OCC offer alternative decompositions (process vs. stimulus), not nesting layers. OCC's goal-desirability check spans Scherer's Relevance AND Implication checks — forced containment doesn't exist in the literature
**Rationale:** Scherer decomposes the appraisal *process* (sequential checks); OCC decomposes the appraisal *stimulus* (events/actions/objects) and provides emotion categories. These are complementary axes, not nesting layers. Using Scherer for process and OCC for output classification preserves #383's investment (GoalAppraisal/ActionAppraisal SPIs and implementations stay in mindmap-api/mindmap-intelligence) while adding a higher-level processing pipeline.
**Trade-offs:** Implementations must understand both frameworks. The GoalAppraisal/ActionAppraisal SPIs may need adapter logic to participate in the Scherer pipeline.
**Sources:** issue #383 (GoalAppraisal/ActionAppraisal SPIs in mindmap-api), issue #428 body (Scherer SECs, OCC taxonomy, Lazarus two-phase), Scherer 2001, OCC 1988, Lazarus 1991
**Exploration:** quick
**Status:** revised — corrected "wraps" framing to process/output relationship; clarified that GoalAppraisal/ActionAppraisal remain in mindmap-api; added Lazarus alternative with rationale for Scherer preference

## D2: Emotion knowledge storage

**Choice:** No separate knowledge store — the appraisal process queries existing CaseMemoryStore/CbrRecordStore through the same retrieval paths everything else uses. Lazarus/OCC/Frijda frameworks inform the appraisal logic, not a separate store. This explicitly rejects the issue #428 body's proposed "Emotion knowledge base (RAG, in neocortex) — vocabulary and patterns from the psychology literature" component.
**Alternatives:**
- RAG corpus in Qdrant (issue #428 body proposal) — semantic retrieval during appraisal, adds Qdrant dependency and latency. The emotion vocabulary (OCC types, Lazarus CRTs, Frijda action tendencies, Scherer SEC parameters) is well-structured reference data, not unstructured knowledge that benefits from semantic search
- MindMap subgraph — emotions as graph nodes, queryable but adds graph complexity
**Rationale:** The emotion knowledge is small, well-structured, and enumerable. OCC defines 22 emotion types; Lazarus defines 15 core relational themes; Frijda lists ~16 action tendencies. These belong as enums and configuration in the appraisal strategy's logic. Memories that inform appraisal (experiences, relationships, moods) are already in the memory stores.
**Trade-offs:** Less flexible for extending the emotion vocabulary dynamically. If the research phase (D7) reveals that Chain-of-Emotion structured knowledge patterns or other unstructured emotional knowledge is needed, this decision should be revisited.
**Depends on:** D7 (research may surface need for knowledge retrieval)
**Sources:** existing CaseMemoryStore SPI, CbrRecordStore SPI, memory retrieval infrastructure, issue #428 body (emotion knowledge base proposal), Chain-of-Emotion paper (PMC11086867)
**Exploration:** quick
**Status:** revised — explicitly acknowledges rejecting issue #428's RAG proposal; added D7 dependency for potential revisit

## D3: Pipeline execution model

**Choice:** Pre-computed context injection via CognitionTickParticipant registered in the DERIVED phase (after DriveOrchestrator ticks, before GoalProposalOrchestrator in TERMINAL). Uses the existing custom participant extension point in CognitionCore — no new phase enum value needed. The participant implementation runs the appraisal pipeline and stores results for prompt section rendering.
**Alternatives:**
- New APPRAISAL phase enum value — adds a sixth phase to CognitionPhase. Unnecessary when the existing CognitionTickParticipant SPI provides the extension point at the right position
- In-prompt guided appraisal — LLM performs appraisal during response generation, risks analytical-mode trap
- Post-interaction hook — keeps current pattern but with full Scherer pipeline, delays emotional awareness to after response
- TERMINAL phase placement — appraisal after goals. Rejected: goals may benefit from appraisal output (emotional state can influence goal urgency)
**Rationale:** Running appraisal before response generation means the LLM receives pre-processed emotional context (evocative), not analytical instructions. DERIVED phase placement satisfies ordering constraints: mood state is available (ticks in FOUNDATION), drive state is available (DriveOrchestrator ticks in DERIVED before custom participants run). SOURCE_PER_SUBJECT does not accept custom participants (CognitionCore throws IllegalArgumentException). The SPI abstraction allows both computational and LLM-backed implementations per stage, with research determining which.
**Trade-offs:** LLM-backed stages add latency. The boundary between "computed" and "LLM-reasoned" stages is a research question. Appraisal runs after per-subject processing but the current implementation is not per-subject — a future iteration may need per-subject appraisal, which would require either relaxing the SOURCE_PER_SUBJECT restriction or running subject-specific appraisal within DERIVED with explicit subject iteration.
**Sources:** CognitionCore tick lifecycle (FOUNDATION→SOURCE→SOURCE_PER_SUBJECT→DERIVED→TERMINAL), CognitionTickParticipant SPI in cognition-api, wacky-manor Principle 13 (identity activation), Chain-of-Emotion paper (PMC11086867)
**Exploration:** quick
**Status:** revised — specified DERIVED phase placement via CognitionTickParticipant; corrected phase enum to include SOURCE_PER_SUBJECT; documented ordering constraints

## D4: Salience model

**Choice:** Perceived situation — SalienceStrategy takes external observation text + internal state (drives, mood, concerns, memories) and produces a textual, subjective construction of what the character is aware of. Two input channels fused into one subjective experience. This perceived situation becomes the input to Scherer's appraisal SECs.
**Alternatives:**
- Memory-based only — score/rank structured data, misses raw environmental perception
- Text-level only — rewrite environment text, misses internal state dimension (fears, hopes, anticipation)
- Two independent passes — loses the insight that perception is construction, not filtering
**Rationale:** Perception is not filtering — it's construction. The biased competition model shows top-down signals (drives, mood) actively shape what reaches conscious awareness. External and internal channels fuse into a single subjective experience. We perceive through what we observe AND what we're thinking/feeling.
**Trade-offs:** More complex than pure structured retrieval; requires textualizing internal state.
**Sources:** RAS/salience network (issue #428 body), biased competition model (Liu et al.), Frijda's control precedence
**Exploration:** deep-analysis
**Status:** captured

## D5: Appraisal output scope

**Choice:** Emotional state + action tendencies — AppraisalResult includes both emotional state (PAD + discrete emotions) and action readiness (approach/avoidance/attend/reject etc from Frijda). Neocortex computes the urge; the LLM decides whether to act on it. These are situational (momentary) action tendencies, distinct from CAPS behavioral attractors which are dispositional (stable tendencies from accumulated experience).
**Alternatives:**
- Emotional state only — simpler but loses Frijda's insight that emotions ARE action readiness states
- Full action bias — mechanical action selection, more deterministic but less emergent
**Rationale:** Emotions without action tendencies are inert descriptions. Frijda's core insight: emotions are states of action readiness. The appraisal should produce "I feel anxious AND I'm ready to flee/protect" — the LLM then decides whether to act on or resist the urge.
**CAPS coordination:** CAPS (#407) produces dispositional approach/avoidance via BIS/BAS activation — stable behavioral attractors from accumulated experience. Appraisal action tendencies are situational — momentary readiness states from the current event. The trait/state interaction is: CAPS dispositional tendencies modulate the *threshold* for situational action tendencies (e.g., high BIS_activation → lower threshold for avoidance tendencies during appraisal, mediated through AppraisalWeights). The LLM receives both signals at different temporal scales — behavioral attractors as part of the behavioral profile, action tendencies as part of appraisal output — and they reinforce rather than conflict.
**Trade-offs:** Action tendencies in the prompt could over-constrain LLM behavior if poorly calibrated. Must calibrate so situational tendencies and dispositional attractors don't produce contradictory behavioral signals.
**Sources:** Frijda — The Emotions (1986), action tendency taxonomy, wacky-manor action system, #407 §2.4 (CAPS × OCC trait/state distinction)
**Exploration:** quick
**Status:** revised — added CAPS coordination note defining trait/state distinction with behavioral attractors

## D6: SEC granularity

**Choice:** Single AppraisalStrategy SPI with one appraise(PerceivedSituation) → AppraisalResult method. This is a new SPI at a HIGHER abstraction level than the existing GoalAppraisal/ActionAppraisal SPIs from #383. The existing #383 SPIs remain in mindmap-api and are not deprecated — they continue to serve OCC-level appraisal (goal prospects, action standards). AppraisalStrategy operates at the Scherer level, receiving a unified PerceivedSituation (D4) and producing a full emotional appraisal with action tendencies (D5).
**Relationship to #383 D1:** #383 D1 chose "Additive SPI over unified framework" — rejecting a generic `CognitiveAppraisal<C>` because the inputs differ fundamentally (MindMapNode goal vs ActionContext) and generics complicate CDI injection. AppraisalStrategy does NOT reverse this. The inputs still differ at the OCC level. AppraisalStrategy operates at a different abstraction level: it receives PerceivedSituation (a unified subjective construction), not raw goal nodes or action contexts. A SchererAppraisalStrategy implementation may delegate to GoalAppraisal/ActionAppraisal internally, preserving their specialised inputs.
**Alternatives:**
- Per-SEC SPIs (RelevanceCheck, ImplicationCheck, CopingCheck, NormativeCheck) — maximum flexibility but couples architecture to Scherer's specific model
- Extend #383's additive pattern (add a third SPI alongside GoalAppraisal/ActionAppraisal) — conflates the OCC-level and Scherer-level abstractions
**Rationale:** The research may show that some SECs collapse naturally for LLM agents, or that a different decomposition works better. A single SPI keeps options open while the Scherer structure guides the default implementation. Implementations can internally structure around Scherer's 4 SECs but aren't forced to, allowing non-Scherer implementations.
**Trade-offs:** Less compositional than per-SEC SPIs; harder to swap individual checks. Two abstraction levels of appraisal SPIs (OCC-level in mindmap-api, Scherer-level in cognition-api) may confuse consumers — documentation must clarify the relationship.
**Sources:** Scherer — Component Process Model (2001), AppraisalWeights record from #383/CognitiveDerivationEngine, #383 D1 (additive SPI decision)
**Exploration:** quick
**Status:** revised — explicitly documents relationship to #383 D1 and clarifies that AppraisalStrategy operates at a different abstraction level, not reversing the additive SPI choice

## D7: Research phase

**Choice:** Research document first — Stage 1 produces a standalone synthesis document analysing each model's applicability to LLM agents before any code. Separates "what should we build" from "how should we build it."
**Alternatives:**
- Interleaved — research captured inline with design decisions, faster but risk of cherry-picking
**Rationale:** Six psychological models need synthesis. Premature design decisions risk anchoring on one model. A standalone document becomes a reference for all subsequent architecture work and for future contributors.
**Trade-offs:** Adds a document phase before code.
**Sources:** Lazarus (1966/1991), Scherer (2001), Frijda (1986/2007), OCC (1988), Chain-of-Emotion (2024), RAS/Salience Network
**Exploration:** quick
**Status:** captured

## D8: Drive model evolution

**Choice:** Both DriveOrchestrator and CharacterDrivePromptSection change. Drives evolve from 4 fixed axes to dynamic per-character drives (named motivational concerns). The 4 baseline drive axes (3 inspired by SDT — autonomy, competence, affiliation — plus curiosity from information-gap theory/Kashdan) stay as computed baselines; per-character drives (protection, greed, etc.) layer on top from cognitive profiles. CharacterDrivePromptSection's monolithic descriptions are replaced by appraisal output.
**CAPS coordination:** This evolves DriveOrchestrator (real-time tick computation), not DriveAdaptationPhase (consolidation-time adaptation). #407 §2.3 plans for CAPS to subsume DriveAdaptationPhase — these are different systems at different lifecycle stages. DriveOrchestrator computes current drive intensities during the pre-response tick; DriveAdaptationPhase adjusts drive baselines over time during consolidation. The per-character drive model should be designed so CAPS behavioral attractors can eventually modulate drive baselines (replacing DriveAdaptationPhase's simpler reinforcement mechanism), while DriveOrchestrator continues to compute real-time intensities.
**Alternatives:**
- CharacterDrivePromptSection only — keep 4-axis DriveOrchestrator, change rendering. Too coarse for domain-specific character drives.
- Drives out of scope — focus #428 on salience + appraisal only. Misses the core insight that drives ARE the input to appraisal.
- Feed per-character drives into CAPS instead of DriveOrchestrator — CAPS produces personality-parameterized behavioral attractors from experience; drives could be CAPS input rather than DriveOrchestrator config. Rejected for now: CAPS operates at consolidation time and produces dispositional tendencies, while drives need real-time computation for each tick. CAPS can modulate drive parameters over time (replacing DriveAdaptationPhase) without replacing DriveOrchestrator's real-time function.
**Rationale:** Pre-release — we change the API. Character drives (protection, greed, discovery) don't map onto 4 universal axes. The appraisal architecture makes character drives meaningful by using them to bias salience and evaluate situations. Without per-character drives, appraisal loses its personality-specific input.
**Trade-offs:** Breaking API change to DriveOrchestrator; existing CuriosityDrive/CompetenceDrive/etc implementations need adaptation. The per-character drive model is complementary to CAPS, not competing — CAPS subsumes the consolidation-time adaptation path (DriveAdaptationPhase), not the real-time computation path (DriveOrchestrator).
**Sources:** wacky-manor character drives (Hartwell/protection, Foxworth/greed), Self-Determination Theory (3 basic needs: autonomy, competence, relatedness), Kashdan (curiosity), Berlyne (information-gap theory), DriveOrchestrator/DriveSource/DriveAxis in cognition-api, #407 §2.3 (DriveAdaptationPhase subsumption protocol)
**Exploration:** deep-analysis
**Status:** revised — corrected "4 SDT axes" to "3 SDT-inspired + curiosity"; added CAPS coordination distinguishing DriveOrchestrator (real-time) from DriveAdaptationPhase (consolidation-time, subsumed by CAPS)

## D9: Boredom/habituation as personality-parameterized appraisal

**Choice:** Boredom, impatience, and repetition fatigue are first-class concerns handled by novelty detection in salience (SEC 1) + expectation discrepancy in appraisal (SEC 2) + Frijda's habituation law. Habituation rate, novelty threshold, and repetition tolerance are personality-derived via CognitiveDerivationEngine, with per-drive domain modulation (repetition within an active drive's domain habituates slower).
**Alternatives:**
- Separate boredom system — standalone repetition tracker outside appraisal. Misses the insight that boredom IS an appraisal outcome.
- Uniform habituation — same rate for all characters. Misses personality × situation interaction.
**Rationale:** Appraisal theory says emotions arise from person × environment. The same repetition produces restlessness in high-openness characters and contentment in low-openness characters. Domain-specific interest modulates further — a botany enthusiast has infinite patience in the garden but not the library. Strong tests needed on personality × habituation combinations with observable, expectation-aligned outcomes.
**Trade-offs:** Adds complexity to appraisal parameterization; requires careful calibration and testing of personality-habituation curves.
**Sources:** Scherer SEC 1 (novelty), Frijda's Law of Habituation, Big Five openness/conscientiousness, CognitiveDerivationEngine disposition derivation pathways
**Exploration:** deep-analysis
**Status:** captured

## D10: Module structure

**Choice:** SPIs and value types in cognition-api, implementations in cognition. AppraisalStrategy, SalienceStrategy, AppraisalResult, PerceivedSituation, ActionTendency, HabituationConfig all go in cognition-api alongside existing SPI interfaces (DriveSource, CognitionTickParticipant, CognitionPromptRenderer) and value types (DriveAxis, CognitionPhase). DefaultSalienceStrategy, SchererAppraisalStrategy, AppraisalPromptSection go in cognition alongside existing orchestrators (MoodOrchestrator, DriveOrchestrator).
**Alternatives:**
- New appraisal-api + appraisal modules — cleaner boundaries but adds modules for a cognitive process
- Split across cognitive-api (value types) and cognition-api (SPIs) — follows existing Confidence/TemporalMark pattern but fragments the appraisal concept
**Rationale:** Appraisal is a cognitive process. The existing convention is: SPIs and value types in cognition-api (DriveSource, DriveAxis, CognitionPhase, CognitionTickParticipant), orchestrators and implementations in cognition (MoodOrchestrator, DriveOrchestrator). New appraisal types follow this established split.
**Trade-offs:** cognition-api grows; all appraisal types share cognition-api's dependency footprint.
**Sources:** existing cognition-api module structure — DriveSource/DriveAxis/CognitionPhase/CognitionTickParticipant in cognition-api; MoodOrchestrator/DriveOrchestrator in cognition
**Exploration:** quick
**Status:** revised — corrected factual error: MoodOrchestrator/DriveOrchestrator are in cognition (implementation module), not cognition-api. The convention is SPIs/value types in cognition-api, orchestrators in cognition

## D11: Testing strategy

**Choice:** New CognitiveAppraisalTest base class in a suitable test module (not caps-testing). Scenario builder seeds DispositionAxes + drive state + mood state, presents PerceivedSituation inputs, asserts on AppraisalResult (emotions + action tendencies). Supports repeated presentation for habituation testing (D9). May share some infrastructure with CognitiveEmergenceTest (e.g., DispositionAxes seeding) but has fundamentally different domain primitives.
**Alternatives:**
- Extend CognitiveEmergenceTest (original choice) — CognitiveEmergenceTest is deeply coupled to CAPS infrastructure (CapsTopology, CapsSettler, CapsWeightUpdater, DispositionWeightMapper, RuleBasedSituationClassifier, SettlingResult, BehavioralAttractor). Appraisal inputs (PerceivedSituation, drive state, mood) and outputs (AppraisalResult with emotions and action tendencies) don't map to CAPS test primitives. Extending it would force awkward adapter logic for no benefit.
- Contract test base class — parameterized profiles, more mechanical
- Property-based testing — statistical invariants over random profiles
**Rationale:** Appraisal testing needs a fundamentally different scenario structure than CAPS testing: seed personality → present observation + internal state → assert on emotional state + action tendencies. The only shared element is DispositionAxes seeding — not enough to justify extending a CAPS-specific framework. Tests like "high-openness character shows boredom after N repetitions; low-openness doesn't" are both specifications and regression guards.
**Trade-offs:** Scenario tests are more verbose than property tests; need enough scenarios to cover the personality space. A new test base class means maintaining two scenario frameworks (CognitiveEmergenceTest for CAPS, CognitiveAppraisalTest for appraisal) — but these have different concerns and different evolution trajectories.
**Depends on:** D9 (personality-parameterized habituation defines what to test)
**Sources:** CognitiveEmergenceTest in caps-testing (reference for scenario-builder pattern, not for extension), existing test patterns
**Exploration:** quick
**Status:** revised — changed from extending CognitiveEmergenceTest to new CognitiveAppraisalTest base class. CognitiveEmergenceTest is tied to CAPS primitives (CapsTopology, SettlingResult, BehavioralAttractor) that don't map to appraisal inputs/outputs

## D12: Research synthesis method

**Choice:** Multi-agent debate scoped to real-time LLM appraisal applicability — spawn advocates for key synthesis questions about how each model applies to pre-response emotional context computation for LLM agents. The debate builds on #407's existing synthesis (which covers consolidation-time mechanical encoding) and focuses on the distinct question: what works for real-time appraisal that the LLM receives as evocative context?
**Alternatives:**
- Deep analysis — single-pass structured analysis, faster but single perspective
- Defer to implementation — lighter synthesis, iterate through code. Risks anchoring on premature design.
- Re-do full synthesis from scratch — #407 (2026-10-02) already performed extensive synthesis of Lazarus, Scherer, Frijda, OCC with detailed comparisons (§2.2), integration architecture (§2.3, §2.4), and explicit decisions about which constructs to implement mechanically vs. delegate to LLM. Re-litigating those decisions wastes effort.
**Rationale:** Six psychological models with competing claims about what matters for emotional processing. #407 resolved the consolidation-time questions; #428 needs to resolve the real-time questions. Same models, different application context — LLM prompt construction requires evocative rather than precise output, real-time latency constraints apply, and the boundary between mechanical and LLM computation is different. Debate forces each model's strongest case for the real-time context to be articulated and challenged.
**Trade-offs:** Higher cost in time and tokens; debate output needs curation. Must take #407's decisions as given for consolidation-time concerns.
**Depends on:** D7 (research document first)
**Sources:** Lazarus, Scherer, Frijda, OCC, Chain-of-Emotion, RAS/Salience Network, #407 (2026-10-02-psychology-cause-effect-models-design.md — existing synthesis to build on)
**Exploration:** quick
**Status:** revised — scoped debate to real-time LLM applicability; explicitly builds on #407's consolidation-time synthesis rather than starting from scratch

## D13: CAPS × Scherer × OCC reconciliation

**Choice:** Three-system complementary architecture across two temporal scales. The systems are not overlapping — they operate at different lifecycle stages with defined interaction points:
- **OCC** (real-time + consolidation-time, mindmap-api): Emotion classification spanning both temporal scales. Real-time: ActionAppraisalObserver fires on ExperienceRecorded events. Consolidation-time: GoalAffectPhase (@Priority 37) appraises goal prospects during sleep. Both produce CognitiveEmotion instances that feed mood (real-time) and CAPS reinforcement (consolidation).
- **Scherer appraisal pipeline** (real-time, cognition-api): Higher-level process that computes emotional context before LLM response generation. May internally delegate to OCC-level SPIs. Produces AppraisalResult (emotional state + action tendencies) rendered as evocative prompt sections.
- **CAPS** (consolidation-time, caps-engine): Dispositional behavioral tendency encoding. Accumulates experience patterns into stable behavioral attractors during "sleep." Outputs to the BEHAVIORAL MindMap subgraph, rendered as a finished behavioral profile.

**Interaction points:**
1. CAPS disposition parameters (BIS/BAS activation, threat_sensitivity) modulate Scherer appraisal thresholds via AppraisalWeights (derived by CognitiveDerivationEngine at agent creation, potentially updated by CAPS over time)
2. OCC emotions feed into CAPS as reinforcement signals during consolidation (#407 §2.4 — EmotionType → λ sign mapping)
3. Appraisal action tendencies (situational) and CAPS behavioral attractors (dispositional) are rendered independently — the LLM receives trait-level tendencies and state-level readiness as distinct prompt sections
4. CAPS subsumes DriveAdaptationPhase (consolidation-time) while DriveOrchestrator continues to compute real-time drive state for appraisal input

**Alternatives:**
- Merge all three into one system — loses the temporal scale distinction that makes each system effective at its lifecycle stage
- CAPS subsumes real-time appraisal too — CAPS settling is too expensive for real-time (up to 100 iterations); the pre-computed behavioral profile IS the mechanism for making CAPS output available at runtime
- Ignore CAPS and design appraisal independently — risks conflicting behavioral signals and duplicated approach/avoidance modeling

**Rationale:** #407 §2.4 already documents the OCC × CAPS relationship as complementary (situational vs. dispositional). Adding Scherer as the real-time appraisal process does not create a third overlapping system — it provides the processing pipeline for what was previously ad-hoc emotion computation. The three systems form a clear hierarchy: CAPS (trait-level, consolidation) → Scherer pipeline (state-level, pre-response) → OCC taxonomy (emotion classification, both levels).

**Sources:** #407 §2.3 (DriveAdaptationPhase subsumption), #407 §2.4 (OCC × CAPS integration), Mischel & Shoda 1995, Scherer 2001, OCC 1988
**Exploration:** surfaced by review — implicit decision made explicit
**Status:** captured
