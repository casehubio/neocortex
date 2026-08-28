# Neocortex Cognitive Architecture — Program of Work

A phased roadmap to consolidate, unify, and extend the 17 cognitive function groups
into a cohesive, composable cognitive system. Addresses all findings from the
[Cognitive Coherence Audit](cognitive-coherence-audit.md).

---

## Principles

Every phase is governed by these five constraints:

| Principle | What it means in practice |
|-----------|--------------------------|
| **Orthogonality** | Each concept (time, affect, confidence, identity) is ONE system, used everywhere. No concept has two implementations. |
| **Regularity** | Same patterns everywhere: builders for composition, sealed hierarchies for exhaustive modelling, SPI+decorator for extension. A developer who learns one subsystem can predict the others. |
| **Composability** | Any metadata dimension (affect, time, confidence, identity) can combine with any memory type without special-casing. Mood-weighted CBR retrieval should be as natural as mood-weighted text retrieval. |
| **Consistency** | One naming convention, one confidence model, one temporal model, one affective model. Terms are pinned in the terminology table and enforced across repos. |
| **Auditability** | At every phase gate, check for: duplication, overlap, conflicts, gaps, tensions, naming drift, broken composability. The checklist is a gate, not a guideline. |

---

## Terminology Unification

Before any code changes, pin the canonical vocabulary. The system borrows from cognitive science, AI/ML, and software engineering — the same concept has different names in different disciplines. This table is the single source of truth.

| Platform term | Definition | Cognitive science equivalent | Currently used in |
|---|---|---|---|
| **confidence** | How certain the system is about a piece of knowledge. Numeric [0,1], decays over time, reinforced by confirmation. | Activation level (ACT-R), belief strength (BDI) | MindMap (`confidence`), CBR (`CbrOutcome`), Memory (`importance`) — three implementations, should be ONE |
| **origin** | How the knowledge was established: directly told (STATED), derived by rules (INFERRED), LLM-suggested (SPECULATED). | Source monitoring (Johnson et al. 1993) | MindMap (`ConfidenceOrigin`) — not used in Memory or CBR |
| **affect** | The emotional valence of knowledge or experience, modelled as PAD (pleasure, arousal, dominance). Property of the knowledge, not the agent's current mood. | Dimensional affect (Mehrabian 1996), core affect (Russell 2003) | MindMap (PAD on nodes/edges), MoodState (PAD on agent) — not on Memory or CBR |
| **mood** | The agent's current emotional state. Decays toward a baseline. Influences retrieval (mood-congruent recall). | Core affect, mood-congruent memory (Bower 1981) | `MoodState`, `MoodBaseline`, `MoodDecay`, `MoodModulatedRetrieval` |
| **temporal bounds** | When knowledge is valid: `validFrom` (start of validity) and `validUntil` (end of validity). Nullable — unbounded if absent. | Temporal context, prospection (Gilbert & Wilson 2007) | MindMap only (`validFrom`/`validUntil` on nodes and edges) |
| **timestamp** | When something happened or was recorded. Always an `Instant`. | Episodic memory timestamp | Memory (`createdAt`), MindMap (`createdAt`/`updatedAt`/`confirmedAt`), events (`turnId` — NOT an Instant, a gap) |
| **provenance** | Free-text source attribution: who/what produced this knowledge. | Source memory (Tulving) | MindMap (`provenance` field), Memory (no equivalent) |
| **traits** | Dynamic type classifications applied to graph nodes by rules. Opaque to the SPI; semantics owned by the intelligence layer. | Categorisation, prototype theory (Rosch 1975) | MindMap (`Set<String> traits()` on nodes) |
| **domain** | The cognitive subsystem a memory belongs to: "experience", "relationship", "reflection", "mood", "engagement". Routing key for converters. | Memory system taxonomy (Tulving 1972) | Memory (`MemoryInput.domain`) |

**Naming rules:**
- Use `confidence` everywhere — retire `importance` (Memory) and `CbrOutcome` as a name (keep the EMA mechanics, rename the concept)
- Use `affect` for knowledge emotions — reserve `mood` for agent state, `emotion` for the general concept
- Use `validFrom`/`validUntil` for temporal bounds — do not introduce `effectiveFrom` or `startDate`
- Use `timestamp` for when-it-happened — add `Instant timestamp()` to all event types

---

## Phase 1: Structural Consolidation

**Goal:** Make the 17 cognitive types feel like one system. Fix APIs, naming, and confidence model so that a developer moving between MindMap, CBR, and Memory encounters the same patterns and terminology.

### 1a: Unified Confidence Model

| | Current | Target |
|---|---|---|
| MindMap | `ConfidenceOrigin` (enum) + `double confidence` + decay via decorator | Unified `Confidence` record |
| CBR | `CbrOutcome` (EMA-adjusted double) | Same unified record |
| Memory | `Double importance` (nullable, no decay) | Same unified record |

**Design:** A shared `Confidence` record with `origin` (how we know), `value` (how sure, [0,1]), and `decayReference` (Instant from which decay is computed). Lives in a shared module (or `memory-api` which all stores already depend on conceptually).

**Migration:** Each store maps its current model to the unified record. `ConfidenceOrigin` becomes the `origin` field. `CbrOutcome`'s EMA value becomes the `value` field. `importance` becomes `value` with `origin=null` (source unknown). Breaking changes are fine — pre-release platform.

**Scope:** M — touches all three SPI modules but the change per module is mechanical.

### 1b: Builder APIs

| Type | Current | Target |
|---|---|---|
| `MindMapQuery` | 9-arg constructor, positional nulls | `MindMapQuery.builder().tenantId(t).text(q).limit(20).build()` or immutable `with*()` methods matching CbrQuery |
| `NodeInput` | 13-arg constructor | `NodeInput.builder().name(n).subgraphId(sg).confidence(c).build()` |
| `EdgeInput` | 12-arg constructor | Same pattern |
| `MemoryInput` | Mixed (some withX, missing others) | Complete `with*()` coverage |

Follow CbrQuery's proven pattern: immutable records with `withX()` methods that return a new instance with one field changed. No mutable builders — records are the builders.

**Scope:** S — pure API additions, no behavioural changes.

### 1c: Cross-Store Composability

**Current:** `MoodModulatedRetrieval` and `PersonalityWeightedRetrieval` only work on `List<Memory>`. CBR and MindMap retrieval results have no mood/personality hooks.

**Target:** A generic retrieval modulation layer parameterised by result type:

```
interface RetrievalModulator<T> {
    List<T> modulate(List<T> results, ModulationContext context);
}
```

Where `ModulationContext` carries the agent's current mood, personality weights, and any other cross-cutting retrieval parameters. Implementations for `Memory`, `ScoredCbrCase`, and `MindMapNode` adapt to their respective affect/confidence fields.

**Scope:** M — new abstraction, three implementations, replaces two existing utilities.

### 1d: Naming & Terminology Audit

- Walk every public type in `memory-api`, `mindmap-api`, `fusion-api`, `rag-api`
- Check each name against the terminology table
- Rename inconsistencies (pre-release — breaking changes are free)
- Produce a checklist of cross-repo terms that blocks must also adopt

**Scope:** S — renames only, no behavioural changes. But must coordinate with blocks and engine if types cross repo boundaries.

### 1e: ForwardingMindMapStore Adoption

Already implemented (#223). Verify all current and future decorators extend `AbstractForwardingMindMapStore`. Add to the contributor guide as a requirement.

**Scope:** Done.

---

## Phase 2: Temporal Unification

**Goal:** One temporal model that handles wall-clock time, relative time, and ordinal (turn-based) time. Every store supports temporal queries. A unified temporal index enables cross-store "what happened when?" and "what's coming up?" queries.

### 2a: Temporal Taxonomy

The system has three kinds of time, currently handled ad-hoc:

| Kind | Example | Current representation | Gap |
|---|---|---|---|
| **Wall-clock** | "meeting Dec 25 at 3pm" | `Instant` (`validFrom`, `createdAt`) | Works, but not queryable on MindMap |
| **Relative** | "3 days from now", "last week" | Not represented | Must be resolved to wall-clock at capture time |
| **Ordinal** | "turn 42", "after the third message" | `String turnId` on events | No mapping to wall-clock; not sortable across conversations |

**Design:**

```java
sealed interface TemporalMark {
    record WallClock(Instant instant) implements TemporalMark {}
    record Relative(Duration offset, Instant anchor) implements TemporalMark {}
    record Ordinal(String turnId, int sequence) implements TemporalMark {}

    Instant resolveToInstant(Instant now);
}
```

`resolveToInstant()` produces a best-effort `Instant` for sorting and proximity:
- `WallClock` returns its instant directly
- `Relative` computes `anchor + offset` (or `now + offset` if anchor is null)
- `Ordinal` returns the timestamp from when the turn was stored (requires lookup), or `now` as fallback

Not every use site needs `TemporalMark` — most will continue using `Instant` directly. The sealed hierarchy is for LLM extraction and natural-language temporal references where the input might be any of the three kinds.

**Scope:** M — new type + integration into MindMapExtractor's temporal parsing.

### 2b: Timestamps on Event Types

Add `Instant timestamp()` to: `ExperienceEvent`, `RelationshipEvent`, `ReflectionEvent`, `MoodState`, `EngagementEvent`.

These currently carry `turnId` only. The converters (`ExperienceEvents.toMemoryInput`, etc.) use the store's `createdAt` — but the event itself has no time. This means you can't compute intervals between events, sort events before storing them, or query by time without a round-trip through the memory store.

Default to `Instant.now()` at construction. The converter should propagate the event's timestamp to the memory's attributes.

**Scope:** S — field addition to 5 record types + converter updates.

### 2c: Temporal Query on MindMapQuery

Add three fields to `MindMapQuery`:
- `Instant validAfter` — nodes whose `validFrom` is after this instant ("what's coming up?")
- `Instant validBefore` — nodes whose `validFrom` is before this instant ("what already started?")
- `Instant updatedAfter` — nodes updated since this instant ("what changed recently?")

Implement in `InMemoryMindMapStore` (filter in Java) and `SqliteMindMapStore` (WHERE clause).

This is the **single highest-impact change** across all dimensions. It unlocks:
- "What's coming up in 7 days?" as a query, not a full scan
- Efficient curiosity signals (replace O(n) node iteration)
- Temporal coherence between MindMap and Memory/CBR query models

**Scope:** S — 3 fields on a record, 2 backend implementations, contract tests.

### 2d: Chronological Index

A cross-store `TemporalIndex` that maintains a sorted view of all temporal events:
- Upcoming MindMap events (by `validFrom`)
- Recent memories (by `createdAt`)
- Recent experiences (by `timestamp`)
- Active CBR cases (by last retrieval)

Answers: "what happened in the last hour?", "what's coming up this week?", "what's on my mind right now?"

Feeds the curiosity engine's proximity signals efficiently, replacing the current O(n) node scan in `CuriositySignalGenerator`.

**Scope:** L — new cross-store utility, requires temporal query support from 2b and 2c.

---

## Phase 3: Affective Universality

**Goal:** Every memory type can carry emotional metadata. Affect is a trajectory (evolving over time), not a snapshot (overwritten on each update). Prospective events carry anticipatory affect distinct from inherent affect.

### 3a: PAD on MemoryInput/Memory

Add nullable `Double pleasure, Double arousal, Double dominance` to `MemoryInput` and `Memory`.

This aligns text memory with MindMap's affective model. A memory tagged `pleasure=-0.8, arousal=0.6` ("I lost my job") participates in mood-congruent retrieval naturally — `MoodModulatedRetrieval` already reads PAD from attributes; this makes it first-class.

Update `MoodModulatedRetrieval` to read PAD from any memory's fields, not just mood-domain attributes.

**Scope:** S — field additions + retrieval update.

### 3b: Affect Trajectory Log

PAD becomes a timestamped log, not a mutable field. Each `updateNode()` that changes PAD creates an `AffectEntry(Instant timestamp, double pleasure, double arousal, double dominance)`. The "current" PAD is the most recent entry. The trajectory is queryable:

- Slope of pleasure over time (improving or worsening?)
- Volatility of arousal (stable or oscillating?)
- Trend direction and rate of change

**Implementation:** Reuse the Memory domain pattern — each PAD update becomes a `domain="affect"` memory with the node ID as `entityId`. This leverages existing temporal query infrastructure (`MemoryQuery.since`), gets recency sorting for free, and follows the established converter pattern. A `TrendAnalyzer`-style utility computes trajectory metrics from the log.

**Scope:** M — new converter, decorator intercept on `updateNode`, trajectory utility.

### 3c: Prospective Event Model

Future-dated nodes need richer semantics:

**Event traits** (via the existing trait system):
- `Appointable` — calendar entry with a fixed time (meeting, flight, appointment)
- `Aspirational` — hoped-for outcome, may not happen (new job, promotion, moving)
- `Threatening` — anticipated negative event (debt, disciplinary, exam)
- `Opportunistic` — anticipated positive event (interview, holiday, bonus)

Each trait has a `TraitRule` that fires automatically — e.g., `AppointableTraitRule` matches nodes with `validFrom` set + no uncertainty markers.

**Event lifecycle** (via property convention, enforced by trait rules):
`PLANNED → CONFIRMED → ACTIVE → COMPLETED | CANCELLED | REVIEWED`

A property `status=planned` on a future-dated node enables queries like "show confirmed events this week." State transitions are explicit via `updateNode` — with affect log entries capturing how the agent's feelings changed at each transition.

**Anticipatory affect:** Distinct from inherent affect. A funeral has inherent `pleasure=-0.8`, but anticipatory affect might be `pleasure=-0.4` (sadness tempered by acceptance). Model as a separate PAD annotation in the affect log, tagged with `type=anticipatory` vs `type=inherent`.

**Recurring events:** A `RecurrenceRule` property (iCalendar RRULE-style: `FREQ=WEEKLY;BYDAY=MO`) on a template node. A generator creates instance nodes from the template. Each instance is a regular node with `validFrom` — no special query support needed.

**Scope:** M — traits + rules + property conventions + recurring event generator.

### 3d: Trajectory-Aware Curiosity

Update `CuriositySignalGenerator`'s affect dampening to use trajectory, not snapshot:

| Trajectory | Curiosity response | Rationale |
|---|---|---|
| Escalating negative | **INCREASE** curiosity | Something is festering — probe, don't avoid |
| Stable negative | Dampen as now | Known negative, no change |
| Improving | Moderate curiosity | Coping is working — light monitoring |
| Volatile (oscillating) | **INCREASE** curiosity | Unresolved ambivalence |

**Temporal-affective interplay:** Proximity score x affect trajectory = cognitive priority.
- Approaching + worsening = **highest priority** (exam tomorrow, anxiety spiking)
- Approaching + stable = normal proximity signal
- Approaching + improving = lower priority (coping working)
- Distant + worsening = elevated (early warning)

**Scope:** S — update signal scoring, add trajectory slope computation.

---

## Phase 4: Cross-Store Queries & Graph Reasoning

**Goal:** Unified querying across all cognitive stores. "Tell me everything about Alice" in one call. "What's on my mind right now?" as a single ranked list. Graph reasoning over the unified knowledge structure.

### 4a: Cross-Store Entity Resolution

A `CognitiveProfile` utility that, given an entity name or ID:
1. Resolves the MindMap node (via `resolveNode`)
2. Follows `NodeRef`s to text memories (`scheme="memory"`) and CBR cases (`scheme="cbr"`)
3. Queries engagement events, relationship quality, experience history
4. Reads the affect trajectory
5. Returns a unified `EntityKnowledge` record aggregating everything the system knows

This is the "tell me everything about Alice" query.

**Scope:** L — bridge module depending on all three SPIs.

### 4b: TemporalFocus Utility

"What's on my mind right now?" — aggregates:
- Upcoming MindMap events (by proximity score)
- Recent memories (by recency)
- Active affect trajectories (by urgency — worsening scores higher)
- Recent experiences (by timestamp)

Returns a ranked `AttentionList` — the cognitive equivalent of working memory contents. Each item has a source (which store), a salience score, and a reason ("approaching event", "recent experience", "worsening affect").

This feeds the agent's executive function — deciding what to think about next.

**Scope:** M — depends on temporal index (2d) and affect trajectory (3b).

### 4c: Graph Reasoning Integration

DesiredState has developed graph reasoning capabilities (directed acyclic graph traversal, dependency resolution, convergence analysis). Future exploration:

- Can DesiredState's graph query engine traverse MindMap structure?
- "Find all paths between Alice and Project X"
- "What entities are transitively connected to this upcoming event?"
- Convergence analysis: "which knowledge areas are well-connected vs isolated?"

This is an exploration item — assess feasibility, don't commit to implementation.

**Scope:** Exploration — assessment only.

### 4d: Unified Query Language

Long-term vision: a query DSL that spans all stores.

```
find entities
  where affect.pleasure < -0.5
  and temporal.validFrom within 7 days
  and confidence > 0.6
  order by proximity desc, affect.trajectory asc
  limit 10
```

This crosses MindMap + Memory + temporal + affective in one query. The DSL compiles to store-specific queries and merges results. Requires all prior phases to provide the underlying query capabilities.

**Scope:** XL — language design, compiler, cross-store execution engine.

---

## Phase Gates

At each phase boundary, run the coherence checklist:

- [ ] No duplication of concepts across stores
- [ ] No overlapping types that should be unified
- [ ] No naming conflicts or inconsistencies (check against terminology table)
- [ ] No gaps where composition should work but doesn't
- [ ] Orthogonality: each dimension (time, affect, confidence, identity) handled by ONE system
- [ ] Regularity: same patterns used everywhere (builders, sealed hierarchies, SPI+decorator)
- [ ] All public API types have `with*()` builders or equivalent composability
- [ ] Terminology table updated with any new terms introduced
- [ ] Cross-repo consistency: blocks and engine use the same terms for the same concepts

---

## Sequencing & Dependencies

```
Phase 1 (Structural)          Phase 2 (Temporal)           Phase 3 (Affective)         Phase 4 (Queries)
─────────────────────          ─────────────────            ───────────────────          ─────────────────
1a: Unified Confidence ──┐     2a: Temporal Taxonomy ──┐    3a: PAD on Memory ──┐       4a: Entity Resolution
1b: Builder APIs         │     2b: Event Timestamps    │    3b: Affect Trajectory│       4b: TemporalFocus
1c: Cross-Store Comp. ◄──┘     2c: Temporal MindMap Q  │    3c: Prospective Model       4c: Graph Reasoning
1d: Naming Audit ─────────►    2d: Chronological Index◄┘    3d: Trajectory Curiosity    4d: Query DSL
1e: Forwarding (DONE)
```

| Work item | Depends on | Scale | Key deliverable |
|---|---|---|---|
| 1a: Unified Confidence | — | M | Single `Confidence` type across MindMap, CBR, Memory |
| 1b: Builder APIs | — | S | `MindMapQuery.builder()`, `NodeInput.builder()`, `MemoryInput.withX()` |
| 1c: Cross-Store Composability | 1a | M | Generic `RetrievalModulator<T>` for mood/personality/trust |
| 1d: Naming Audit | 1a | S | Terminology table enforced; inconsistencies renamed |
| 1e: Forwarding Store | Done (#223) | — | — |
| 2a: Temporal Taxonomy | 1d | M | `TemporalMark` sealed hierarchy |
| 2b: Event Timestamps | 2a | S | `Instant timestamp()` on all event types |
| 2c: Temporal MindMapQuery | 2a | S | `validAfter`/`validBefore`/`updatedAfter` fields |
| 2d: Chronological Index | 2a, 2b, 2c | L | Cross-store `TemporalIndex` |
| 3a: PAD on Memory | 1d | S | `pleasure`/`arousal`/`dominance` on MemoryInput |
| 3b: Affect Trajectory | 3a | M | `AffectEntry` log per node/entity |
| 3c: Prospective Events | 2a, 3b | M | Event lifecycle + traits + recurrence |
| 3d: Trajectory Curiosity | 3b | S | Updated `CuriositySignalGenerator` |
| 4a: Cross-Store Entity | 1c, 2d | L | `CognitiveProfile` utility |
| 4b: TemporalFocus | 2d, 3b | M | `AttentionList` — "what's on my mind?" |
| 4c: Graph Reasoning | 4a | Exploration | DesiredState integration assessment |
| 4d: Query DSL | 4a, 4b | XL | Unified cognitive query language |

**Phase 1** can start immediately — no external dependencies. Items 1a and 1b are parallelisable.

**Phase 2** depends on the naming audit (1d) for consistent temporal terminology. Items 2b and 2c are parallelisable once 2a is done.

**Phase 3** depends on naming (1d) for affective terminology. 3a and 3b are sequential. 3c depends on both temporal (2a) and affect trajectory (3b).

**Phase 4** is the integration phase — depends on everything before it. 4c and 4d are exploratory/long-term.

---

## What This Program Does NOT Cover

- **Blocks integration** — `CuriosityDrive`, `InnerLifeOrchestrator`, and orchestrator tick-loop changes live in blocks, not neocortex. This program prepares the neocortex SPIs; blocks adoption is a separate program.
- **New backends** — no new storage engines (TinkerPop, PostgreSQL graph). The improvements work with existing InMemory + SQLite backends.
- **LLM prompt evolution** — `MindMapExtractor`'s prompts may need updating as the temporal/affective model evolves, but prompt engineering is not a structural concern.
- **Performance** — the chronological index (2d) has performance implications but this program focuses on capability, not optimisation.
