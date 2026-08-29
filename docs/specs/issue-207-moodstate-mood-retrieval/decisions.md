## D1: MoodState and engagement scoring belong in memory-api

**Choice:** Both MoodState (PAD model) and conversational engagement scoring live in neocortex memory-api
**Alternatives:**
- Eidos — has `AgentStateStore` for per-agent dynamic state (degradation health), but narrowly typed for `DegradationReason`; mood needs the full event log pattern and its primary consumer is the retrieval decorator in neocortex
- Blocks — patterns compose these types, but shouldn't own the foundation types they compose
**Rationale:** Dependency direction: mood modulates memory retrieval (`PersonalityWeightedRetrieval`) — the consumer is in neocortex. The existing domain event types (experience, relationship, reflection) already live in memory-api. MoodState follows this pattern as durable dynamic state that must survive process boundaries. While eidos has `AgentStateStore` for per-agent dynamic state, it is narrowly typed for degradation health (`record`/`query`/`clear` a `DegradationReason`); mood needs event log storage with trajectory analysis, temporal ordering, and domain-scoped queries — capabilities `CaseMemoryStore` already provides.
**Trade-offs:** Eidos consumers who want MoodState without the full memory dependency must go through memory-api
**Exploration:** quick
**Status:** revised — R1-06 corrected the factually inaccurate claim that eidos has no persistence story; revised to cite dependency direction as the primary rationale

## D2: Mood state stored as event log, not single overwritten record

**Choice:** Each mood shift is stored as an event via the memory SPI. Current mood is derived by reading the latest event(s).
**Alternatives:**
- Single current-state record, overwritten per update — simpler but no history, no trajectory analysis
**Rationale:** Mood trajectory is needed for emotional continuity (preventing whiplash), trend analysis, and correlation with engagement outcomes. Follows the existing experience event pattern — events in, derived state out. Persistence is behind SPIs; backends handle durability.
**Trade-offs:** Deriving current mood requires a query rather than a simple read; slightly more storage
**Exploration:** quick
**Status:** captured

## D3: Mood shifts use own domain via CaseMemoryStore, not ExperienceEvent hierarchy

**Choice:** Mood shifts stored via `CaseMemoryStore` with `MemoryDomain("mood")` and structured attributes. ExperienceEvent sealed hierarchy unchanged.
**Alternatives:**
- New `Appraisal` variant in ExperienceEvent sealed hierarchy — reopens sealed type for every new state kind, conflates internal state change with agent experience
- Outcome with mood metadata — overloads Outcome semantics, mood isn't an experience outcome
**Rationale:** Mood appraisal is derived internal state, not an experience event. Own domain keeps the experience SPI focused (observations, actions, outcomes of what the agent does) and gives mood its own query namespace. Follows the pattern of relationship and reflection already having separate domains.
**Trade-offs:** Consumers querying "everything about this agent" need to query multiple domains rather than one sealed type
**Exploration:** quick
**Status:** captured

## D4: Engagement scoring as standalone event type with own domain

**Choice:** `EngagementEvent` record following the RelationshipEvent pattern — standalone type, own converter (`EngagementEvents`), own domain (`MemoryDomain("engagement")`), own attribute keys. Not part of ExperienceEvent sealed hierarchy.
**Alternatives:**
- Metadata on existing Outcome — temporally decoupled (engagement data arrives after Outcome is recorded), conflates "what happened" with "how well did it work"
- New ExperienceEvent sealed variant — breaks exhaustive switches, engagement isn't an experience (it's an evaluation of one)
- Value type attached to events — loses own domain, own query namespace, own persistence path
**Rationale:** Three first-principles arguments: (1) engagement signals are temporally decoupled from the action they evaluate — you can't record them at action time; (2) the existing pattern separates concerns into domains (experience, relationship, reflection) — engagement is a fourth concern (interaction efficacy measurement); (3) typed fields with constructor validation at the API boundary follow RelationshipEvent precedent (QualitySignal enum, self-referential rejection).
**Trade-offs:** Another event type and converter to maintain; consumers querying engagement need to know the domain name
**Depends on:** D1 (placement in memory-api)
**Exploration:** deep-analysis
**Status:** captured

## D5: MoodState carries current PAD values only, baseline is external

**Choice:** `MoodState` carries only current pleasure, arousal, dominance values. Baseline PAD is an explicit per-agent configuration (see D8), not derived from `AgentDisposition`.
**Alternatives:**
- Self-contained with baseline — duplicates personality configuration into every mood event, creates sync problem when configuration changes
- Derived from AgentDisposition — rejected: `AgentDisposition` axes are vocabulary-registrar terms (`List<DispositionValue>` with `String term, double weight`), not PAD floats; no mapping function exists and no correct mapping is obvious
**Rationale:** Separation of concerns: MoodState is a pure snapshot of current emotional state. Baseline PAD is agent configuration that informs decay targets. Keeping baseline external avoids embedding configuration into every event. The self-contained alternative was also rejected because personality may evolve (`DispositionEvolution`), and baseline should track personality without re-embedding in stored events.
**Trade-offs:** Consumers computing decay need access to the per-agent baseline configuration, not just the mood event
**Depends on:** D2 (event log storage), D8 (baseline mechanism)
**Exploration:** quick
**Status:** revised — R1-02 identified that `AgentDisposition` has no PAD fields; revised to use explicit per-agent configuration instead of non-existent derivation from disposition axes

## D6: Mood-modulated retrieval uses per-memory PAD annotation

**Choice:** Memories carrying PAD attributes (pleasure, arousal, dominance — stored at ingestion time) get mood-boosted. The decorator multiplies the existing score by a mood-alignment factor computed as dimensional proximity in PAD space (e.g., cosine similarity or normalized distance). Memories without PAD annotation are unaffected.
**Alternatives:**
- Valence-only — captures only the pleasure axis; loses arousal and dominance dimensions. An aroused agent should preferentially recall intense memories, not just positive ones. Mood-congruent recall in the affect literature (Bower 1981, Russell's circumplex 1980) operates on dimensional alignment, not unidimensional positive/negative
- Domain-based modulation — map mood axes to domain weights dynamically; too coarse, doesn't capture individual memory emotional tone
**Rationale:** The PAD model has three dimensions for a reason. If MoodState uses PAD (D5), mood-modulated retrieval must use PAD-dimensional alignment for architectural coherence. The alignment factor becomes: `1 - distance(memory_PAD, current_mood_PAD) / max_distance`. Graceful degradation: memories without PAD annotation are unaffected (same as valence-only design). Annotation is a convention at ingestion time, not an enforcement.
**Trade-offs:** Requires memory producers to annotate three floats per memory at ingestion time (vs one for valence-only); LLM-assisted annotation at ingestion time makes this practical. Coverage depends on adoption.
**Depends on:** D3 (mood as own domain), D5 (current-only PAD)
**Exploration:** quick
**Status:** revised — R1-03 identified that valence-only is architecturally incoherent with the PAD model; revised to full PAD triple per memory

## D7: EngagementEvent is per-interaction, not per-episode

**Choice:** One EngagementEvent per agent message, recording how that specific message was received. Per-interaction fields: `responded`, `responseTimeMs`, `responseLength`, `affectShift`, `reactionCount`, `continued`. Aggregate metrics (response rate, trends) derived by TrendAnalyzer from fine-grained data.
**Alternatives:**
- Per-episode — one event per conversation episode with aggregate metrics; loses granularity, can't correlate engagement with specific actions, can't derive per-interaction patterns from aggregate data
**Rationale:** Fine-grained data enables aggregate derivation but not vice versa. Per-interaction events correlate with the specific action via turnId, enabling StrategyLearning to learn "this type of message works better." TrendAnalyzer already handles aggregation over event history.
**Trade-offs:** More events stored (one per message vs one per conversation); consumers wanting aggregates need to query and compute
**Depends on:** D4 (standalone event type)
**Exploration:** quick
**Status:** captured

## D8: PAD baseline is explicit per-agent configuration

**Choice:** Each agent's PAD baseline (the emotional resting point that mood decays toward) is an explicit configuration — three float values (pleasure, arousal, dominance in [-1, 1]) set at agent creation time. Not derived from `AgentDisposition` axes.
**Alternatives:**
- Vocabulary-driven lookup tables — each disposition vocabulary defines PAD implications; brittle, requires maintaining a mapping table per vocabulary term
- LLM-inferred mapping at agent creation — generates PAD baseline from personality description; non-deterministic, not auditable
- Research-based heuristics (Big Five → PAD) — mapping exists in the affective computing literature but assumes Big Five traits, not CaseHub's disposition vocabulary; translation requires two mapping steps (disposition → Big Five → PAD)
**Rationale:** The disposition axes (socialOrient, ruleFollowing, riskAppetite, autonomy, conflictMode) are open-ended vocabulary terms — there is no deterministic mapping to continuous PAD floats. Explicit configuration is the simplest correct approach: the agent creator decides the emotional baseline as part of character design. Helper utilities can suggest PAD values from personality descriptions, but the baseline is a deliberate configuration choice, not a derived value.
**Trade-offs:** Agent creators must specify three additional floats per agent; without helper tooling, this requires understanding of the PAD model
**Surfaced by:** R1-10 (implicit decision I1)
**Exploration:** surfaced-by-review
**Status:** captured

## D9: Mood decay uses exponential decay toward baseline

**Choice:** Mood decays toward baseline PAD using exponential decay: `current + (baseline - current) * (1 - e^(-t/τ))`, where τ is a configurable time constant.
**Alternatives:**
- Linear decay — constant drift rate; simpler but less psychologically grounded
- Asymmetric decay — pleasure decays faster than arousal (hedonic adaptation); research-supported but adds per-axis configuration complexity
**Rationale:** Exponential decay is the standard in cognitive architecture (ACT-R base-level activation, `TemporalDecayCbrCaseMemoryStore` in the CBR decorator chain). It produces fast initial return from extreme states with a slow tail — agents recover quickly from spikes but retain mild emotional coloring. This matches hedonic adaptation research and is already the pattern used by temporal decay in CBR. The time constant τ is configurable per agent, allowing "emotionally sticky" (high τ) or "emotionally reactive" (low τ) characters.
**Trade-offs:** Single time constant for all PAD axes; asymmetric decay would require per-axis constants
**Surfaced by:** R1-11 (implicit decision I2)
**Exploration:** surfaced-by-review
**Status:** captured

## D10: Mood modulation scopes to CaseMemoryStore retrieval only

**Choice:** Mood-modulated retrieval applies to `CaseMemoryStore` queries via the `PersonalityWeightedRetrieval` path. `CbrCaseMemoryStore` retrieval is not mood-modulated.
**Alternatives:**
- Both retrieval paths — apply mood bias to CBR case retrieval as well; a frustrated agent would retrieve different case precedents than a happy agent
**Rationale:** `CaseMemoryStore` holds episodic memories (experiences, reflections, relationships) — these are the memories subject to mood-congruent recall in the affect literature. CBR retrieval is structured feature-vector similarity search — the query is "find cases with similar problem features," not "recall what feels relevant." Mood-congruent recall bias applies to episodic memory, not to analytical case retrieval. Adding mood bias to CBR scoring would distort case-based reasoning outcomes without psychological justification.
**Trade-offs:** CBR retrieval is mood-independent; an agent in a negative mood retrieves the same case precedents as a positive one
**Surfaced by:** R1-12 (implicit decision I3)
**Exploration:** surfaced-by-review
**Status:** captured

## D11: Concurrent mood writes resolved by timestamp ordering

**Choice:** Concurrent mood shifts are handled by the append-only event log (D2) with last-write-wins determined by store-assigned timestamp. No single-writer constraint or vector clock.
**Alternatives:**
- Vector clock — correct for distributed consensus but heavyweight for a state that is inherently approximate (mood is not transactional)
- Single-writer constraint — serialize all mood updates through one writer; adds contention, unnecessary given append-only storage
- Explicit merge strategy — combine concurrent mood shifts into a blended result; over-engineered for sub-millisecond concurrency windows
**Rationale:** Mood state is inherently approximate — the difference between pleasure=0.72 and pleasure=0.74 is not meaningful. Concurrent mood shifts within the same millisecond are extremely unlikely, and when they occur, either resulting state is a valid current mood. Append-only storage (D2) means writes never conflict. "Current mood = most recent event by store-assigned timestamp" is deterministic and sufficient.
**Trade-offs:** Under pathological concurrency (same-millisecond writes), the tiebreaker is arbitrary; this is acceptable for approximate emotional state
**Surfaced by:** R1-13 (implicit decision I4)
**Exploration:** surfaced-by-review
**Status:** captured
