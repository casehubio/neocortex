# Neocortex Cognitive Coherence Audit

An analysis of how well the 17 cognitive types compose, with specific gaps and recommendations across six dimensions: composability, temporal coherence, MindMap temporal design, affective tagging, future-facing knowledge, and prospection with affective forecasting.

---

## Dimension 1: Composability

### What works well

**CbrQuery** is the gold standard for composable APIs. It uses immutable `with*()` methods (`withFilter`, `withTemporalDecay`, `withWeights`, `withScope`, `withScopeDecay`, `withRetrievalMode`, `withFusionStrategy`) — 13 composition methods on a single record. Any combination of features, filters, weights, temporal decay, scope, and fusion strategy can be assembled incrementally.

**MemoryQuery** follows the same pattern with `withCaseId`, `withQuestion`, `withLimit`, `withSince`, `withOrder` — plus factory methods `forEntity` and `forEntities`.

**Pure computation utilities** compose naturally: `PersonalityWeightedRetrieval.reweight()` and `MoodModulatedRetrieval.reweight()` both take `List<Memory>` and return `List<Memory>`, so they chain: personality first, then mood, or vice versa. Both use `Memory.createdAt()` for recency decay.

**CDI event cascade** composes implicitly: `ExperienceRecorded` → `RelationshipObserver` → `RelationshipRecorded` — zero coupling between producers and consumers.

### Gaps and tensions

| Issue | Detail | Impact |
|-------|--------|--------|
| **MindMapQuery has no composition API** | 9-arg constructor, no `with*()` methods, no factory helpers. Compare to CbrQuery's 13 `with*()` methods. | Building queries is error-prone — every field must be specified positionally including 5+ nulls. |
| **Three incompatible confidence models** | `ConfidenceOrigin` (STATED/INFERRED/SPECULATED with initialConfidence) for MindMap; `CbrOutcome` (EMA-adjusted double) for CBR; `importance` (Double) for text memory. Same concept — "how sure are we?" — three different representations. | Cannot sort across subsystems by confidence. A MindMap node at confidence 0.7 and a CBR case at confidence 0.7 mean different things (origin-initial vs EMA-adjusted). |
| **No mood-weighted CBR or graph retrieval** | `MoodModulatedRetrieval` and `PersonalityWeightedRetrieval` work on `List<Memory>` only. CBR retrieval (`ScoredCbrCase`) and MindMap search have no mood/affect/personality hooks. | Mood and personality influence text memory retrieval but have zero effect on CBR similarity scoring or graph traversal. |
| **No cross-system query** | Cannot ask "everything about Alice" in one call. Must query CaseMemoryStore (by entityId), CbrCaseMemoryStore (by features), and MindMapStore (by resolveNode) separately, then merge results manually. | NodeRef provides the *reference* but no *resolution* utility. |
| **ExperienceEvent has no MindMap reference** | ExperienceEvent carries agentId, tenantId, caseId, turnId, description — but no nodeId or NodeRef. Cannot link an experience to a MindMap entity at creation time. | The link must be created separately via MindMapExtractor or manual NodeRef construction. |

### Recommendations

1. **Add `with*()` methods to MindMapQuery** — at minimum: `withSubgraph`, `withText`, `withMinConfidence`, `withTraits`, `withLimit`. Follow CbrQuery's pattern.
2. **Create a `NodeResolver` utility** in mindmap that takes a NodeRef and resolves it against the appropriate store (CaseMemoryStore for scheme="memory", CbrCaseMemoryStore for scheme="cbr"). Lives in a bridge module that depends on all three SPIs.
3. **Add optional `nodeRef` to ExperienceEvent** — so experiences can be linked to MindMap entities at creation time, not only post-hoc via extraction.

---

## Dimension 2: Temporal Coherence

### Audit by subsystem

| Subsystem | Timestamp field | Time-range query | Recency sort | Temporal decay |
|-----------|----------------|-----------------|--------------|----------------|
| **CaseMemoryStore** / Memory | `createdAt` (Instant) | `MemoryQuery.since` (Instant) | `MemoryOrder` enum | `PersonalityWeightedRetrieval` / `MoodModulatedRetrieval` (7-day half-life) |
| **CbrCaseMemoryStore** / CbrCase | None on interface | `CbrQuery.notBefore` (Instant) | Via `TemporalDecay` | `TemporalDecay` (HalfLife, Linear, Step) |
| **MindMapNode** | `createdAt`, `updatedAt`, `confirmedAt`, `validFrom`, `validUntil` | **None on MindMapQuery** | No | `ConfidenceDecayDecorator` (uses `confirmedAt`) |
| **MindMapEdge** | `createdAt`, `updatedAt`, `validFrom`, `validUntil` | **None on MindMapQuery** | No | `ConfidenceDecayDecorator` (uses `updatedAt`) |
| **ExperienceEvent** | `turnId` only (no Instant) | Via MemoryQuery.since after conversion | Via MemoryQuery | Via text memory recency |
| **RelationshipEvent** | `turnId` only | Via MemoryQuery.since after conversion | Via MemoryQuery | Via text memory recency |
| **ReflectionEvent** | `turnId` only | Via MemoryQuery.since after conversion | Via MemoryQuery | Via text memory recency |
| **MoodState** | `turnId` only | Via MemoryQuery.since after conversion | Via MemoryQuery | Via text memory recency |
| **EngagementEvent** | `turnId` only | Via MemoryQuery.since after conversion | Via MemoryQuery | Via text memory recency |

### Key findings

**The event types (Experience, Relationship, Reflection, Mood, Engagement) have no Instant timestamp.** They carry `turnId` (a string correlation ID) but no actual time. The timestamp is only created when the converter writes to CaseMemoryStore — `Memory.createdAt` is set by the store, not by the event. This means:
- You cannot sort events by time before storing them
- You cannot compute time intervals between events without querying the store
- The `turnId` is a logical ordering key, not a temporal one

**MindMap has rich temporal fields but no temporal query.** `MindMapQuery` has no `since`, `validBefore`, `validAfter`, or any temporal filter. To find "what's coming up this week" you must load all nodes via `nodesIn()` and filter in Java. The CuriositySignalGenerator does exactly this — iterates all nodes in all subgraphs checking `validFrom`.

**CbrQuery and MemoryQuery handle time differently.** `CbrQuery.notBefore` filters cases created after a cutoff. `MemoryQuery.since` filters memories created after a cutoff. Same concept, different names. Neither supports `until` (upper bound), `between`, or "most recent N within timeframe."

### Recommendations

1. **Add `Instant timestamp()` to all event types** (ExperienceEvent, RelationshipEvent, ReflectionEvent, MoodState, EngagementEvent). Default to `Instant.now()` at construction. The converter should use this timestamp, not invent one at store-time.
2. **Add temporal fields to MindMapQuery**: `Instant validAfter`, `Instant validBefore` — filter nodes whose `validFrom`/`validUntil` intersects the window. This enables "what's coming up?" as a query rather than a full scan.
3. **Unify temporal query naming**: adopt `since` consistently (not `notBefore`), add `until` to both MemoryQuery and CbrQuery.

---

## Dimension 3: MindMap Temporal Audit

### What's implemented

| Feature | Status | Evidence |
|---------|--------|----------|
| `validFrom` / `validUntil` on nodes | Present | Nullable Instant fields on `MindMapNode` |
| `validFrom` / `validUntil` on edges | Present | Nullable Instant fields on `MindMapEdge` |
| `createdAt` / `updatedAt` on both | Present | Set by backends at creation/mutation time |
| `confirmedAt` on nodes | Present | Reset by `NodeUpdate.confirmedAt` — resets decay clock |
| Confidence decay decorator | Implemented | `ConfidenceDecayDecorator.applyDecay()` |
| Proximity signals in curiosity engine | Implemented | `CuriositySignalGenerator.collectProximitySignals()` — checks `validFrom.isAfter(now)` |
| Past-event detection | Implemented | Checks `validUntil.isBefore(now)` — generates "did it happen?" signals |
| Temporal query on MindMapQuery | **Missing** | No `validAfter`/`validBefore` fields |
| `confirmedAt` on edges | **Missing** | Edges have no explicit confirmation mechanism |
| Per-edge-type decay rates | **Missing** | `EdgeTypeDefinition.defaultDecayHalfLifeDays` exists in the vocabulary definition, but `ConfidenceDecayDecorator` takes a single `double defaultHalfLifeDays` and applies it uniformly. The per-type rates are never read. |
| `getEdge()` decay interception | **Missing** | `ConfidenceDecayDecorator` intercepts `getNode`, `nodesIn`, `search`, `neighbors`, `bridgeEdges` — but NOT `getEdge()`. Single-edge lookups bypass decay entirely. |

### Decay reference asymmetry

- **Nodes**: decay from `confirmedAt` (explicit confirmation resets clock). If `confirmedAt` is null, no decay applied (returns raw confidence).
- **Edges**: decay from `updatedAt` (any mutation resets clock). No `confirmedAt` on edges — no way to say "I re-confirmed this relationship is still true" without modifying the edge.

This asymmetry means: a node created 2 years ago but never confirmed decays to near-zero. An edge created 2 years ago but updated yesterday (even with a trivial property change) shows full confidence. Employment relationships and project assignments decay at the same rate, despite `EdgeTypeDefinition` carrying per-type half-life data that the decorator ignores.

### Recommendations

1. **Add `confirmedAt` to MindMapEdge** and implement `updateEdge()` on MindMapStore (spec already specifies this — R1-07 from the spec review).
2. **Add temporal filtering to MindMapQuery** — `validAfter` and `validBefore` fields.
3. **Wire per-edge-type decay** — `ConfidenceDecayDecorator` should read `EdgeTypeDefinition.defaultDecayHalfLifeDays` from the registered vocabulary for each edge type, falling back to the global default only when no type-specific rate is defined.
4. **Intercept `getEdge()` in ConfidenceDecayDecorator** — single-edge lookups should return decayed confidence, consistent with all other query methods.

---

## Dimension 4: Affective Tagging

### Audit by subsystem

| Subsystem | Affective fields | Model | Nullable? |
|-----------|-----------------|-------|-----------|
| **MindMapNode** | `pleasure`, `arousal`, `dominance` | PAD (Mehrabian) | Yes — all `Double` (nullable) |
| **MindMapEdge** | `pleasure`, `arousal`, `dominance` | PAD | Yes — all `Double` (nullable) |
| **MoodState** | `pleasure`, `arousal`, `dominance` | PAD | No — primitive `double`, validated [-1,1] |
| **CaseMemoryStore / MemoryInput** | **None** | N/A | N/A |
| **Memory** | **None** | N/A | N/A |
| **ExperienceEvent** | **None** | N/A | N/A |
| **RelationshipEvent** | `qualitySignal` (POSITIVE/NEGATIVE/NEUTRAL) | 3-value enum | Not PAD — much coarser |
| **EngagementEvent** | `sentimentShift` (Double) | Scalar shift | Nullable, but not PAD |
| **CbrCase** | **None** | N/A | N/A |
| **ReflectionEvent** | **None** | N/A | N/A |

### Key findings

**Affective tagging is MindMap-only.** Only MindMap nodes and edges carry full PAD annotations. Text memories (the largest store) have NO affective fields. You cannot tag a text memory with "this is a bad memory" — not even via the attributes map in a typed way.

**MoodModulatedRetrieval works, but only on mood-domain memories.** The `moodFactor()` method reads PAD values from the memory's attributes — specifically `MoodAttributeKeys.PLEASURE`, `AROUSAL`, `DOMINANCE`. But these are only set when MoodState is stored as a memory (domain="mood"). Regular text memories, experiences, relationships, and reflections have no PAD attributes. So mood-congruent retrieval only boosts mood-domain memories, not all memories by their emotional valence.

**QualitySignal (3-value) and PAD (3-axis) are different models.** RelationshipEvent uses a coarse POSITIVE/NEGATIVE/NEUTRAL signal. This doesn't map cleanly to PAD — a NEGATIVE relationship could be high-arousal (conflict) or low-arousal (neglect). The two models don't compose.

### Recommendations

1. **Add optional PAD fields to MemoryInput** — `Double pleasure`, `Double arousal`, `Double dominance`. Store them in the attributes map with standard keys. This allows any memory to carry emotional valence: "lost my job" with pleasure=-0.8, arousal=0.6.
2. **Update MoodModulatedRetrieval to read PAD from any memory's attributes** — not just mood-domain memories. The attribute keys already exist (`MoodAttributeKeys`); the retrieval logic just needs to check them on all memories.
3. **Add a `toAffect()` method on QualitySignal** that maps to approximate PAD values: POSITIVE → pleasure=0.5, NEGATIVE → pleasure=-0.5, NEUTRAL → pleasure=0. This bridges the two models without replacing either.

---

## Dimension 5: Future-Facing Knowledge

### What works

| Feature | Status | How |
|---------|--------|-----|
| Calendar-like events | Works | Create a MindMapNode with `validFrom` set to the event date. E.g., "Holiday Dec 25" with `validFrom=2026-12-25`. |
| Hope vs fact | Works | `ConfidenceOrigin.SPECULATED` (0.3) for hopes, `STATED` (1.0) for confirmed facts. "Hoping to start new job" = SPECULATED. "Accepted job offer" = STATED. |
| Proximity-driven curiosity | Works | `CuriositySignalGenerator` finds future-dated nodes and scores them with `1/(1+daysUntil/7)`. Approaching events generate stronger signals. |
| Past-event outcome checks | Works | Nodes with `validUntil` in the past generate "Did X happen? What was the outcome?" signals. |

### What's missing

| Gap | Detail | Impact |
|-----|--------|--------|
| **No "upcoming events" query** | No way to ask MindMapStore "what's coming up in the next 7 days?" without scanning all nodes in all subgraphs. `MindMapQuery` has no temporal filter. | CuriositySignalGenerator does a full scan — works at agent scale but won't scale. |
| **No event lifecycle** | No status field on nodes (PLANNED → CONFIRMED → HAPPENING → COMPLETED → CANCELLED → REVIEWED). `validFrom`/`validUntil` define the temporal window but don't track whether the event was attended, cancelled, or rescheduled. | "Holiday Dec 25" after Dec 25 still shows as a past event — no distinction between "happened as planned" and "was cancelled." |
| **No transition from hope to fact** | A "hoping to start a job" node (SPECULATED) that becomes real has no clean transition path. You'd manually change `ConfidenceOrigin` via an update, but there's no lifecycle event, no audit trail of the transition, and no automatic edge/property propagation. | The knowledge graph can't answer "what changed from speculation to reality?" |
| **No recurring events** | No recurrence model (weekly team meeting, annual holiday, monthly review). Each occurrence must be a separate node. | Manual node creation for every instance of a recurring event. An agent with a "weekly standup" must create 52 nodes per year. |
| **No priority/urgency beyond proximity** | Future events carry confidence and proximity score, but no explicit priority. "Dentist appointment" and "job interview" tomorrow get the same proximity score. | All events are equal — the curiosity engine can't distinguish important from trivial without additional property conventions. |
| **Plans and aspirations are undifferentiated** | "Hope to start new job" is a MindMapNode with SPECULATED confidence. There's no semantic distinction between an aspiration, a plan, a prediction, and a fact — only the confidence level differs. | An agent can't query "what are my aspirations?" separately from "what are my facts?" without a trait or property convention. |
| **No unified temporal focus** | There is no cross-store "what should I think about right now?" utility. MindMap has `validFrom`, text memory has `createdAt`, CBR has `notBefore` — but no aggregation across stores into a single ranked attention list. | The cognitive equivalent of "what's on my mind" requires manually querying three stores and merging results. |

### Recommendations

1. **Add temporal filtering to MindMapQuery** (repeating from Dimension 3 — this is the single most impactful improvement). Fields: `Instant validAfter`, `Instant validBefore`.
2. **Establish a `status` property convention** for event nodes — `status=planned|confirmed|active|completed|cancelled|reviewed`. Not a core field — a property convention enforced by the trait system. A `TemporalEventTraitRule` could auto-apply a "TemporalEvent" trait when `validFrom` is set.
3. **Add an `intent` property convention** — `intent=fact|plan|aspiration|prediction`. Combined with confidence, this distinguishes "I accepted a job" (fact, STATED) from "I hope to get a job" (aspiration, SPECULATED) from "I think I'll get the job" (prediction, INFERRED).
4. **Priority can be modelled as importance** — add a `Double importance` field to NodeInput (parallel to MemoryInput.importance). High-importance future events generate stronger curiosity signals.
5. **Consider a cross-store `TemporalFocus` utility** that aggregates upcoming MindMap events + recent memories + recent experiences into a single ranked "attention list" — the cognitive equivalent of "what's on my mind right now."

---

## Dimension 6: Prospection & Affective Forecasting

### The concept

**Prospection** (Gilbert & Wilson 2007) is the human capacity for mental time travel into the future — imagining specific upcoming episodes and experiencing emotions about them now. **Affective forecasting** (Wilson & Gilbert 2003) is the process of predicting how we'll feel about future events. **Anticipatory affect** (Baumgartner et al. 2008) is the emotion experienced NOW about a FUTURE event — distinct from the event's inherent emotional character.

This is not "hope." Hope is one point on a rich spectrum:

| Future event | Anticipatory affect | Affect trajectory over time |
|---|---|---|
| Starting new job | excitement + anxiety | anxiety fades as confidence builds |
| Upcoming exam | dread + motivation | intensifies as date approaches |
| Disciplinary meeting | fear + anger | may shift to resignation or defiance |
| First skydive | thrill + terror | oscillates, spikes day-of |
| Anticipated job loss | dread + helplessness | may shift to relief or determination |
| Cash flow crisis ahead | anxiety + shame | compounds if unresolved |
| Holiday next month | joy + anticipation | builds steadily |

Each future event carries a **trajectory** of changing emotions, not a single static value. The trajectory itself is data — "is this person's anxiety about the exam increasing or decreasing?" is a meaningful cognitive question.

### Current state

| Feature | Status |
|---------|--------|
| Future-dated nodes via `validFrom` | Present — a node can represent a future event |
| Confidence distinguishes fact from speculation | Present — STATED (accepted job) vs SPECULATED (hoping) |
| PAD fields on nodes | Present — but a single mutable snapshot, not a log |
| Proximity signals in curiosity engine | Present — future events intensify as they approach |

### The gap: affect as snapshot vs trajectory

PAD on a `MindMapNode` is a single overwritten value. When `updateNode()` sets new PAD values, the old ones are lost. A node for "disciplinary meeting Thursday" has `pleasure=-0.7, arousal=0.8` on Monday, but by Thursday it might be `pleasure=-0.3, arousal=0.4` (resigned acceptance). The system:

- **Overwrites** old PAD values on `updateNode()` — no history preserved
- **Cannot answer** "is anxiety about the exam increasing or decreasing?"
- **Cannot detect** emotional escalation patterns (compounding dread, building excitement)
- **Cannot distinguish** anticipatory affect (how I feel NOW about a future event) from inherent affect (the event's emotional character)
- The curiosity engine **dampens by current affect**, blind to trajectory — a node with stable `pleasure=-0.5` and one with `pleasure` dropping from `-0.2` to `-0.8` get the same dampening, despite the second being far more cognitively urgent

### Design sketch: what prospection needs

**1. Affect log, not affect field**

Each PAD annotation becomes a timestamped entry in a log. The current PAD values become the "most recent entry." The trajectory is queryable: slope of pleasure over time, volatility of arousal, trend direction.

Implementation options:
- **New `AffectEntry` record** — `AffectEntry(Instant timestamp, double pleasure, double arousal, double dominance)` stored per node/edge, queryable as a time series
- **Reuse the Memory domain pattern** — each PAD update becomes a `domain="affect"` memory with the node ID as `entityId`. Leverages existing temporal query infrastructure (`MemoryQuery.since`)
- **Edge properties with timestamped keys** — lighter-weight but less queryable

The Memory domain approach is architecturally consistent: it reuses the existing store infrastructure, gets temporal querying for free, and follows the established pattern where every cognitive subsystem serialises into text memory via a domain-tagged converter.

**2. Prospective event enrichment**

Future-dated nodes should carry richer semantics:

- **`anticipatoryAffect`** — how the agent feels about this future event NOW, distinct from the event's inherent PAD values. A funeral has inherent `pleasure=-0.8`, but anticipatory affect might be `pleasure=-0.4` (sadness tempered by acceptance). These are different signals.
- **Event category via traits** — `Appointable` (calendar entry with a fixed time), `Aspirational` (hoped-for outcome), `Threatening` (anticipated negative), `Opportunistic` (anticipated positive). Traits compose with the existing trait system and fire automatically via `TraitApplicationDecorator`.
- **Certainty** — orthogonal to `ConfidenceOrigin`. STATED/SPECULATED covers "how did I learn this?" but not "how certain am I it will happen?" A job offer is STATED (someone told me) but certainty varies (they might rescind). A `Double certainty` field on `NodeInput`/`MindMapNode` would complete the model.

**3. Trajectory-aware curiosity**

The curiosity engine's affect dampening (D40) currently uses the node's current pleasure value. With a trajectory:

| Trajectory | Curiosity response | Rationale |
|---|---|---|
| Escalating negative affect | **INCREASE** curiosity | Something is festering — the agent should probe, not avoid |
| Stable negative affect | Dampen as now | Known negative, no change — standard avoidance |
| Improving affect | Moderate curiosity | Things are getting better — light monitoring |
| Volatile affect (oscillating) | **INCREASE** curiosity | Instability signals unresolved ambivalence |

This **inverts** the current logic for the escalating case. A worsening emotional trajectory is exactly when the agent SHOULD be curious, not avoidant. The current design suppresses curiosity about negative topics — but a negative topic that's getting WORSE is the most important thing to attend to.

**4. Temporal-affective interplay**

As a future event approaches (proximity score increases), its affect should intensify — humans experience this as anticipatory anxiety or building excitement. **Temporal construal theory** (Trope & Liberman 2003) predicts that psychological distance changes how we represent events: abstract when distant, concrete when near. The proximity score and affect trajectory should compound:

| Proximity × Trajectory | Cognitive priority | Example |
|---|---|---|
| Approaching + worsening affect | **Highest** — urgent cognitive focus | Exam tomorrow, anxiety spiking |
| Approaching + stable affect | Normal proximity signal | Dentist appointment tomorrow, mild anxiety |
| Approaching + improving affect | Lower priority — coping is working | Job interview tomorrow, growing confidence |
| Distant + worsening affect | Elevated — early warning | Debt crisis in 3 months, anxiety building |
| Distant + stable affect | Background — no action needed | Holiday in 6 months, steady anticipation |

### Cognitive science framing

- **Prospection** (Gilbert & Wilson 2007) — mental simulation of future events
- **Episodic future thinking** (Atance & O'Neill 2001) — imagining specific future episodes
- **Affective forecasting** (Wilson & Gilbert 2003) — predicting future emotional states, including systematic biases (impact bias, focalism)
- **Anticipatory affect** (Baumgartner et al. 2008) — emotions experienced NOW about FUTURE events
- **Temporal construal theory** (Trope & Liberman 2003) — psychological distance changes event representation (abstract ↔ concrete)
- **Mental time travel** (Suddendorf & Corballis 2007) — the capacity to project oneself into the future, sharing neural substrates with episodic memory

**Research tags:** prospection, episodic future thinking, Atance & O'Neill 2001, affective forecasting, Wilson & Gilbert 2003, impact bias, anticipatory emotions, Baumgartner 2008, temporal construal theory, Trope & Liberman 2003, future-oriented cognition, mental time travel, Suddendorf & Corballis 2007, pre-experiencing, emotional trajectories, anticipatory anxiety

### Key insight

The deepest gap is not that the system can't represent future events — it can, via `validFrom`. The gap is that **emotions about future events are treated as static properties rather than evolving trajectories**. A human doesn't feel the same way about "exam next month" as "exam tomorrow" — the emotional relationship to the event changes as it approaches, as preparation progresses (or doesn't), as context shifts. The current PAD-as-snapshot model captures one frame of this trajectory. The affect-log model would capture the film.

---

## Cross-Cutting Summary

| Dimension | Verdict | Top Priority |
|-----------|---------|-------------|
| **Composability** | Good for CBR/Memory; weak for MindMap queries | Add `with*()` methods to MindMapQuery |
| **Temporal coherence** | Fragmented — 3 different temporal models | Add `Instant timestamp()` to all event types; unify query naming |
| **MindMap temporal** | Rich fields, no query support; per-edge-type decay unwired | Add `validAfter`/`validBefore` to MindMapQuery; wire per-edge-type decay; add `confirmedAt` to edges |
| **Affective tagging** | MindMap-only; text memories are affect-blind | Add PAD fields to MemoryInput; update MoodModulatedRetrieval |
| **Future-facing** | Mechanically works; semantically thin | Property conventions for status + intent; event lifecycle; temporal query support |
| **Prospection** | Not modelled — PAD is snapshot, not trajectory | Affect log on nodes; prospective event traits; trajectory-aware curiosity |

### Priority Improvements (all dimensions)

| Priority | Change | Impact |
|----------|--------|--------|
| **High** | Add temporal predicates to `MindMapQuery` | Unlocks "what's coming up?" without O(n) scan (D2, D3, D5) |
| **High** | Add PAD fields to `MemoryInput`/`Memory` | Every memory type gets typed affective tagging (D4) |
| **High** | Add `with*()` builders to `MindMapQuery` | Composability parity with CbrQuery/MemoryQuery (D1) |
| **Medium** | Affect trajectory log on MindMapNode | PAD becomes a time series — enables "is anxiety increasing?" (D6) |
| **Medium** | Prospective event traits (Appointable, Aspirational, Threatening) | Distinguish calendar entries from hopes from threats (D5, D6) |
| **Medium** | Add `confirmedAt` to `MindMapEdge` | Edge confidence decay can be reset without mutation (D3) |
| **Medium** | Add `Instant timestamp()` to event types | Direct temporal queries without Memory conversion (D2) |
| **Medium** | Wire per-edge-type decay rates | `EdgeTypeDefinition.defaultDecayHalfLifeDays` actually used (D3) |
| **Low** | Trajectory-aware curiosity dampening | Escalating negative affect increases curiosity instead of suppressing (D6) |
| **Low** | Cross-store `TemporalFocus` utility | "What's on my mind right now?" aggregation (D5) |
| **Low** | Unified `Confidence` type across stores | Single model instead of three (D1) |

**The single highest-impact change across all dimensions: add temporal filtering to MindMapQuery.** It's needed for upcoming-event queries (D5), curiosity signal efficiency (D3), temporal coherence (D2), and prospective event querying (D6). Every other recommendation is secondary to this.

## Design Observation

The system was built layer by layer — text memory first, then CBR, then MindMap, then intelligence. Each layer is internally consistent and well-designed. The tensions arise at **cross-layer boundaries**: temporal queries that work in Memory but not MindMap, affective annotations that work in MindMap but not Memory, builder APIs in CBR but not MindMap. The architecture is sound — these are integration gaps, not design flaws. Filling them would make the 17 cognitive types feel like one system rather than 17 composable parts.

The four highest-leverage improvements — temporal MindMapQuery, PAD on MemoryInput, MindMapQuery builders, and affect trajectory logging — would close the majority of the cross-layer gaps. The first three are integration fixes (bringing MindMap to parity with CBR/Memory). The fourth — affect trajectory — is a conceptual evolution: moving from "what does the agent know?" to "how does the agent feel about what it knows, and how is that feeling changing?" This is the difference between a knowledge system and a cognitive system.
