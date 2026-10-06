# Decisions — #409 Runtime Gut Feeling

## D1: Probe data source

**Choice:** Layered probe — experience-memory semantic search first (domain="experience" with MemoryOrder.RELEVANCE), PAD-space mood-distance fallback when no experience matches.
**Alternatives:**
- Experience memories only — uses existing semantic search, no new infrastructure. But misses affect trajectory signal when no textual match.
- Affect memories via PAD distance only — numeric, no embeddings needed. But answers "how does this mood compare" not "does this situation feel familiar."
**Rationale:** The issue describes a "gut feeling" as instinctive response to novel situations. Experience memories carry the text needed for semantic matching AND the PAD values needed for valence aggregation. The PAD fallback adds a mood-resonance layer when text matching finds nothing.
**Trade-offs:** Two-layer probe is more complex than single-layer. The fallback may rarely fire if experience memories have good coverage.
**Sources:** CaseMemoryStore.query() SPI (MemoryOrder.RELEVANCE), AffectEvents.toMemoryInput() (affect memory structure), Memory record (PAD fields)
**Exploration:** quick
**Status:** captured

## D2: Execution point

**Choice:** CognitionTickParticipant — DERIVED-phase participant that runs during the cognitive tick, receives CognitionTickContext with observation, queries CaseMemoryStore, caches result per agent/tenant.
**Alternatives:**
- Inline in BehavioralPromptSection — simpler but couples rendering to query execution and requires extending CognitionRenderContext
- New GutFeelingOrchestrator SPI — formal SPI but adds abstraction for a single implementation
**Rationale:** Follows the established pattern of AppraisalTickParticipant. Tick participants have access to CognitionTickContext (including observation), run at the right lifecycle point, and can cache results for render-time consumption.
**Trade-offs:** Adds a tick participant class and lifecycle wiring. Simpler than alternatives since the pattern is established.
**Sources:** AppraisalTickParticipant.java (pattern), CognitionTickContext.java (observation field), CognitionPhase.DERIVED
**Exploration:** quick
**Status:** captured

## D3: Rendering integration

**Choice:** Extend BehavioralPromptSection with a second zone for transient gut signals. The section accepts an optional GutFeelingParticipant via constructor.
**Alternatives:**
- Separate GutFeelingPromptSection — clean separation but fragments behavioral context across two sections
**Rationale:** The issue's design explicitly shows both crystallized attractors and gut signals in one section. The LLM consumer sees them as one coherent behavioral context — crystallized patterns as defaults, gut signals as modifiers. Matches #410 D8's allowance for extension.
**Trade-offs:** BehavioralPromptSection grows in responsibility. Acceptable — the two data types are semantically related and render in the same context.
**Depends on:** #410 D3 (BehavioralPromptSection in cognition module)
**Sources:** BehavioralPromptSection.java (just implemented), #410 D8 (scope decision)
**Exploration:** quick
**Status:** captured

## D4: Data passing mechanism

**Choice:** Constructor injection of GutFeelingParticipant into BehavioralPromptSection. CognitionCore passes it in promptSections().
**Alternatives:**
- CognitionCore caches result as field — puts cognitive state in composition root
- CDI event — indirection for an in-process same-tick value
**Rationale:** Same pattern as AppraisalPromptSection receiving AppraisalTickParticipant. The participant caches the last result per agent/tenant, and the prompt section calls currentResult() at render time.
**Depends on:** D2 (GutFeelingParticipant as tick participant), D3 (extend BehavioralPromptSection)
**Sources:** AppraisalPromptSection.java (constructor injection pattern), CognitionCore.promptSections() (wiring pattern)
**Exploration:** quick
**Status:** captured

## D5: Result type

**Choice:** GutFeeling record in cognition-api with GutValence enum (APPROACH, AVOID, CAUTIOUS), double intensity (0-1), and @Nullable String resonanceDescription.
**Alternatives:**
- Reuse AppraisalResult — semantic mismatch: gut feeling is pre-cognitive, not an emotion
- Raw PAD triple — pushes interpretation to the renderer
**Rationale:** Purpose-built type matches the domain concept. Three valence states map to the behavioral guidance the LLM needs. The resonanceDescription provides context ("past negative authority experiences") without surfacing specific memories.
**Trade-offs:** New type in cognition-api. Minimal — it's a simple record.
**Sources:** ActionTendency (similar pattern — readiness + intensity + target), AppraisalResult (render-time value type pattern)
**Exploration:** quick
**Status:** captured

## D6: Aggregation method

**Choice:** Top-3 experience memories by RELEVANCE, mean PAD, pleasure-driven valence. Pleasure > 0.1 → APPROACH, pleasure < -0.1 → AVOID, else CAUTIOUS. Intensity = |mean pleasure| clamped to [0,1].
**Alternatives:**
- Top-5 weighted by relevance score — CaseMemoryStore doesn't expose scores, only ordering
- Single best match — one memory may be an outlier
**Rationale:** Three results provide stable signal without over-fetching. Pleasure is the most interpretable PAD dimension for approach/avoid classification. Dead-band around zero prevents noise.
**Trade-offs:** Mean of 3 may wash out strong signals from a single highly relevant memory. Acceptable — gut feeling is a weak signal by design.
**Depends on:** D1 (experience-memory query)
**Sources:** MemoryQuery.withLimit(3).withOrder(MemoryOrder.RELEVANCE), Memory.pleasure()/arousal()/dominance()
**Exploration:** quick
**Status:** captured

## D7: PAD fallback mechanism

**Choice:** Mood-distance comparison — compare current mood PAD (from MoodOrchestrator) to historical affect-memory PAD distribution using Euclidean distance in PAD space. Signal resonance when current mood is near a cluster.
**Alternatives:**
- Skip PAD fallback — simpler scope, marginal value since affect memories lack situation context
- Recent affect trajectory — momentum signal, not similarity probe
**Rationale:** When text-based matching fails, PAD-space proximity provides a weaker but still useful signal. "Your current emotional state resembles past states associated with [high-arousal negative experiences]" is a legitimate gut feeling.
**Trade-offs:** Requires MoodOrchestrator access in the tick participant. Adds complexity to the probe. The fallback fires rarely if experience memories cover most situations.
**Depends on:** D1 (layered probe), D2 (tick participant has access to orchestrators)
**Sources:** MoodOrchestrator.currentMood(), AffectEvents.DOMAIN (affect memory domain), Euclidean distance in 3D PAD space
**Exploration:** quick
**Status:** captured

## D8: Null observation handling

**Choice:** Graceful degradation — skip experience-memory layer when observation is null, still try PAD fallback. Return empty Optional when neither layer produces signal.
**Alternatives:**
- Require observation — return empty immediately. Simpler but wastes the PAD fallback opportunity.
**Rationale:** The probe never fails. It finds less signal when data is scarce. The PAD fallback can produce useful resonance even without situation text.
**Trade-offs:** The PAD fallback without observation context produces a weaker, less actionable signal. Still better than nothing.
**Depends on:** D1 (layered probe), D7 (PAD fallback)
**Sources:** CognitionTickContext.observation() (@Nullable)
**Exploration:** quick
**Status:** captured
