# Decisions — #417 OCC Emotion to CAPS Reinforcement

## D1: Emotion type persistence mechanism

**Choice:** (REVISED) ExperienceEvent-based — GoalAffectPhase fires ExperienceEvent.Observation with emotion-type/intensity attributes. Standard experience graduation pipeline (domain="experience") carries it to COGNITIVE subgraph nodes. BehavioralSynthesisPhase reads emotion metadata from graduated node properties.
**Alternatives:**
- Enrich affect pathway (REJECTED) — affect-domain memories are never graduated by ExperienceConsolidationPhase, which only scans domain="experience". The affect pathway is a dead end for data that needs to reach BehavioralSynthesisPhase via graduation.
- Direct memory query — BehavioralSynthesisPhase queries CaseMemoryStore directly; simpler but breaks the graduated-node consumption pattern
**Rationale:** Investigation revealed ExperienceConsolidationPhase only graduates domain="experience" memories. The ExperienceEvent approach uses the existing graduation pipeline with no architectural changes. Small modification to DefaultGraduationClassifier to forward emotion properties through GraduationResult.properties(). No AffectTrajectoryDecorator changes needed.
**Trade-offs:** GoalAffectPhase now stores both a PAD node update (existing) and an ExperienceEvent (new). Two writes per emotion, but they serve different purposes: PAD update drives affect trajectory tracking, ExperienceEvent drives CAPS reinforcement via graduation.
**Sources:** ExperienceConsolidationPhase.java (domain filter at line 146), research spec §2.4 timing semantics, GoalAffectPhase.java
**Exploration:** quick → revised after code investigation
**Status:** captured

## D2: CAPS pathway targeting mechanism

**Choice:** Tag-filtered weight updates — map EmotionType to topology connection tags (bas, bis, threat, fight_assert, etc.); look up connections with those tags; identify their source nodes; construct inputActivations from those source nodes; call updateWeights() with those filtered activations.
**Alternatives:**
- Multiple targeted updateWeights calls — separate call per pathway; more granular but multiplies API calls
- Pathway-specific input node injection — treat emotions as synthetic experiences via input node activation + settle; conflates reinforcement signals with experiential input
**Rationale:** The topology already tags connections by pathway (bas, bis, threat, etc.). Using these tags to filter which input nodes to activate works with the existing updateWeights() API — no SPI changes needed. λ sign (outcomeValence) determines reinforcement direction.
**Trade-offs:** Requires a one-time scan of the topology to build a tag→source-nodes index. The mapping is indirect (tag → connections → source nodes → activations) rather than direct tag filtering on the API.
**Sources:** CAPS topology YAML (connection tags), CapsWeightUpdater.java (Rescorla-Wagner equation), CapsEngine.updateWeights() SPI
**Exploration:** quick
**Status:** captured

## D3: Module location for mapping utility

**Choice:** mindmap-intelligence — pure static utility alongside BehavioralSynthesisPhase (the sole consumer).
**Alternatives:**
- caps-api — would create new dependency edge from API-tier module into emotion type hierarchy
- New bridging module — clean separation but heavyweight for a single static mapping table
**Rationale:** mindmap-intelligence already has both dependencies (emotion types from GoalAffectPhase, CAPS types from BehavioralSynthesisPhase). The coupling is inherent in the feature — putting the mapping here doesn't create any new dependency edges.
**Trade-offs:** If a second consumer of this mapping emerges, it would need to depend on mindmap-intelligence or the mapping would need to move. Unlikely given the CAPS architecture.
**Sources:** Module dependency analysis, BehavioralSynthesisPhase.java, GoalAffectPhase.java
**Exploration:** quick
**Status:** captured
