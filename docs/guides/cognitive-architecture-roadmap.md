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
| **Dual-API contract** | Every composable API must be expressible in both Java (builders, DSLs, annotations) and YAML (configuration, authoring). If a Java API can't map to YAML, it's a composability smell — fix the Java API first, then build the YAML layer. YAML is a litmus test for API orthogonality. |
| **Memory spaces are cross-cutting** | Every API, query model, and data type must account for private, shared, and selectively-shared memory from the start. If you can't explain how a feature works when the agent sees both private and shared spaces, the feature isn't designed yet. Memory spaces are an architectural property, not a feature. See [Shared Memory Design](shared-memory-design.md). |

---

## Terminology Unification

Before any code changes, pin the canonical vocabulary. The system borrows from cognitive science, AI/ML, and software engineering — the same concept has different names in different disciplines. This table is the single source of truth.

| Platform term | Definition | Cognitive science equivalent | Currently used in |
|---|---|---|---|
| **confidence** | How certain the system is about a piece of knowledge. Numeric [0,1], decays over time, reinforced by confirmation. | Activation level (ACT-R), belief strength (BDI) | Unified: `Confidence` record in `cognitive-api`, used by MindMap, CBR, and Memory (#229). Event types use `Double confidence` (#232). |
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
- Use `affectShift` for engagement emotional change — retire `sentimentShift`
- Use `affect` for knowledge emotions — reserve `mood` for agent state, `emotion` for the general concept
- Use `validFrom`/`validUntil` for temporal bounds — do not introduce `effectiveFrom` or `startDate`
- Use `timestamp` for when-it-happened — add `Instant timestamp()` to all event types

---

## Phase 1: Structural Consolidation

**Goal:** Make the 17 cognitive types feel like one system. Fix APIs, naming, and confidence model so that a developer moving between MindMap, CBR, and Memory encounters the same patterns and terminology.

### 1a: Unified Confidence Model — **DONE** (#229)

Implemented in `cognitive-api` module. `Confidence(ConfidenceOrigin origin, double value, Instant decayReference)` record used by all three subsystems:

| Subsystem | Before | After |
|---|---|---|
| MindMap | `ConfidenceOrigin` enum + `double confidence` + `confirmedAt` | `Confidence` record (confirmedAt removed — subsumed by `decayReference`) |
| CBR | `CbrOutcome` EMA-adjusted `Double` | `Confidence` record (EMA preserves origin) |
| Memory | `Double importance` | `Confidence` record (origin=UNKNOWN for legacy) |

Module: `cognitive-api` (tier-0, zero deps). `ConfidenceOrigin` moved from `mindmap-api` with `UNKNOWN` added. Factory methods enforce non-null `decayReference` for MindMap contexts; `Confidence.unknown()` for Memory/CBR. `MindMapConfidenceDefaults` provides origin-based default values.

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

### 1d: Naming & Terminology Audit — **DONE** (#232)

Audited all public types in `memory-api`, `mindmap-api`, `fusion-api`, `rag-api` against the terminology table. Findings and renames:

| Module | Before | After |
|---|---|---|
| memory-api (7 event types) | `Double importance` | `Double confidence` |
| memory-api | `sentimentShift` / `SENTIMENT_SHIFT` | `affectShift` / `AFFECT_SHIFT` |
| memory-jpa, memory-sqlite | DB column `importance` | DB column `confidence` (V1 migration updated) |
| memory-inmem, retrieval utils | local vars `importance` | `confidenceValue` |

Clean areas: mindmap-api, fusion-api, rag-api — no terminology violations found. No `effectiveFrom`, `emotion`, or `valence` misuse. `startDate` in mindmap-intelligence is a domain term (project start date), not a temporal bound.

Cross-repo adoption checklist filed as casehubio/blocks#218.

**Scope:** S — renames only, no behavioural changes.

### 1e: ForwardingMindMapStore Adoption

Already implemented (#223). Verify all current and future decorators extend `AbstractForwardingMindMapStore`. Add to the contributor guide as a requirement.

**Scope:** Done.

### 1f: Memory Space Model

Define the multi-agent memory space model as part of structural consolidation — not as a later phase. Every subsequent API design (confidence, builders, temporal, affective) must be space-aware from the start.

**Core concepts:**
- `MemorySpace` — PRIVATE (one agent), SHARED (group), SELECTIVE (named recipients)
- Visibility layer above the store — each space IS a tenant; the layer unions results from all spaces an agent belongs to
- `Visibility` sealed type: `Private(ownerId)`, `Shared(spaceId)`, `Selective(spaceId, Set<String> recipientIds)`
- Space membership with temporal validity (access changes over time)

**Impact on other Phase 1 items:**
- 1a (Confidence): shared knowledge has collective confidence (multiple observers strengthen it); individual confidence is per-viewer
- 1b (Builders): every query builder must accept space parameters — `MindMapQuery.builder().spaces(PRIVATE, SHARED).build()`
- 1c (Cross-Store Composability): `RetrievalModulator<T>` must work across private + shared result sets
- 1d (Naming): add space terminology to the naming table

See [Shared Memory Design](shared-memory-design.md) for full design.

**Scope:** M — `MemorySpace` type, `Visibility` sealed hierarchy, space membership model, visibility layer SPI. No store implementation changes — the abstraction sits above the stores.

---

## Phase 2: Temporal Unification

**Goal:** One temporal model that handles wall-clock time, relative time, and ordinal (turn-based) time. Every store supports temporal queries. A unified temporal index enables cross-store "what happened when?" and "what's coming up?" queries.

### 2a: Temporal Taxonomy — **DONE** (#234)

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

### 2b: Timestamps on Event Types — **DONE** (#235)

Add `Instant timestamp()` to: `ExperienceEvent`, `RelationshipEvent`, `ReflectionEvent`, `MoodState`, `EngagementEvent`.

These currently carry `turnId` only. The converters (`ExperienceEvents.toMemoryInput`, etc.) use the store's `createdAt` — but the event itself has no time. This means you can't compute intervals between events, sort events before storing them, or query by time without a round-trip through the memory store.

Default to `Instant.now()` at construction. The converter should propagate the event's timestamp to the memory's attributes.

**Scope:** S — field addition to 5 record types + converter updates.

### 2c: Temporal Query on MindMapQuery — **DONE** (#236)

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

### 2d: Chronological Index — **DONE** (#237)

A cross-store `TemporalIndex` that maintains a sorted view of all temporal events:
- Upcoming MindMap events (by `validFrom`)
- Recent memories (by `createdAt`)
- Recent experiences (by `timestamp`)
- Active CBR cases (by last retrieval)

Answers: "what happened in the last hour?", "what's coming up this week?", "what's on my mind right now?"

Feeds the curiosity engine's proximity signals efficiently, replacing the current O(n) node scan in `CuriositySignalGenerator`.

**Memory space impact:** The chronological index must span private + shared spaces. A family calendar is a shared temporal view — `TemporalIndex` must aggregate upcoming events from the agent's private space AND all shared spaces they belong to. Group temporal membership (when someone joins/leaves a space) is itself a temporal event that changes what the index contains.

**Scope:** L — new cross-store utility, requires temporal query support from 2b and 2c.

---

## Phase 3: Affective Universality

**Goal:** Every memory type can carry emotional metadata. Affect is a trajectory (evolving over time), not a snapshot (overwritten on each update). Prospective events carry anticipatory affect distinct from inherent affect.

### 3a: PAD on MemoryInput/Memory — **DONE** (#238)

Add nullable `Double pleasure, Double arousal, Double dominance` to `MemoryInput` and `Memory`.

This aligns text memory with MindMap's affective model. A memory tagged `pleasure=-0.8, arousal=0.6` ("I lost my job") participates in mood-congruent retrieval naturally — `MoodModulatedRetrieval` already reads PAD from attributes; this makes it first-class.

Update `MoodModulatedRetrieval` to read PAD from any memory's fields, not just mood-domain attributes.

**Scope:** S — field additions + retrieval update.

### 3b: Affect Trajectory Log — **DONE** (#239)

PAD becomes a timestamped log, not a mutable field. Each `updateNode()` that changes PAD creates an `AffectEntry(Instant timestamp, double pleasure, double arousal, double dominance)`. The "current" PAD is the most recent entry. The trajectory is queryable:

- Slope of pleasure over time (improving or worsening?)
- Volatility of arousal (stable or oscillating?)
- Trend direction and rate of change

**Implementation:** Reuse the Memory domain pattern — each PAD update becomes a `domain="affect"` memory with the node ID as `entityId`. This leverages existing temporal query infrastructure (`MemoryQuery.since`), gets recency sorting for free, and follows the established converter pattern. A `TrendAnalyzer`-style utility computes trajectory metrics from the log.

**Scope:** M — new converter, decorator intercept on `updateNode`, trajectory utility.

### 3c: Prospective Event Model — **DONE** (#241)

Implemented in `mindmap-intelligence` (rules, interface, generator), `mindmap-api` (RecurrenceRule), `cognitive-api` (AffectType), `memory-api` (AffectEvents overload).

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

### 3d: Perspectival Affect Overlays

Per-agent PAD overlays on shared MindMap nodes. When multiple agents share a knowledge graph, the same node ("Grandma", "family holiday") carries different emotional meaning for each viewer.

**Design:** Shared nodes carry NO PAD (affect is always perspectival). Each agent stores a lightweight overlay in their private tenant, linked by `NodeRef` to the shared node. The visibility layer merges shared node + private overlay at query time, returning a single `MindMapNode` with the viewer's perspective.

**Overlay record:** `(sharedNodeId, pleasure, arousal, dominance, confidence, Map<String, String> properties)` — stored as a regular node in the private tenant. This means no changes to `MindMapStore` — the overlay is a convention, not a new SPI method.

See [Shared Memory Design](shared-memory-design.md) § Perspectival Memory.

**Scope:** M — overlay convention, visibility layer merge logic, query-time composition.

### 3e: Trajectory-Aware Curiosity — **DONE** (#242)

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

### 4a: Cross-Store Entity Resolution — **DONE** (#243)

Implemented in `cognitive-index` module. `CognitiveProfile` CDI bean (`@ApplicationScoped`, `Instance<T>` graceful degradation) resolves everything the system knows about an entity:

1. Resolves MindMap node (by ID via `getNode` or by name/alias via `resolveNode`)
2. Follows `NodeRef(scheme="memory")` to linked memories; records `scheme="cbr"` as unresolved (CbrCaseMemoryStore has no get-by-ID)
3. Queries memories across 6 cognitive domains (experience, relationship, reflection, mood, engagement, affect) using dual entity ID resolution (nodeId + nodeName)
4. Computes affect trajectory via `AffectTrajectoryAnalyzer` composition
5. Returns unified `EntityKnowledge` record (node, edges, memories-by-domain, trajectory, unresolved refs)

Query configuration via `CognitiveProfileQuery` record: `byId`/`byName` factories, `withDomains()` for selective domain querying, `withIncludeEdges()`, `withMemoryLimit()`. Single `tenantId` — designed for future multi-tenant extension when memory spaces (#230) land.

**Memory space impact:** Entity resolution must work across memory spaces. "Tell me everything about Alice" traverses the agent's private graph AND shared family graph, follows NodeRefs into private and shared memories, and merges perspectival affect overlays. The result carries provenance: which facts are private, which are shared, which are the viewer's perspective vs consensus.

**Scope:** L — bridge module depending on all three SPIs.

### 4b: TemporalFocus Utility — **DONE** (#244)

"What's on my mind right now?" — aggregates:
- Upcoming MindMap events (by proximity score)
- Recent memories (by recency)
- Active affect trajectories (by urgency — worsening scores higher)
- Recent experiences (by timestamp)

Returns a ranked `AttentionList` — the cognitive equivalent of working memory contents. Each item has a source (which store), a salience score, and a reason ("approaching event", "recent experience", "worsening affect").

This feeds the agent's executive function — deciding what to think about next.

**Memory space impact:** `TemporalFocus` must aggregate across private + shared spaces. A family member's attention list includes both their private upcoming events AND shared family calendar entries. Items from shared spaces carry a `spaceId` so the agent knows the source.

**Scope:** M — depends on temporal index (2d) and affect trajectory (3b).

### 4c: Graph Reasoning Integration — **DONE** (#245)

Exploration complete. **DesiredStateGraph DAG data model does not apply** — acyclic enforcement, `Dependency` provisioning semantics, and `NodeSpec` are incompatible with MindMap's cyclic semantic graph.

**Three algorithmic patterns ARE transferable:**
1. `GraphRuleEngine` — iterative pattern-match → mutate → convergence-check loop. Graph-type-agnostic. Reference for evolving `DerivedEdgeDecorator` when multi-hop inference is needed.
2. `PatternEvaluator` — structural graph pattern matching with variable bindings. Reference for MindMap inference rules beyond property-based `TraitRule`.
3. Declarative rule model (`@GraphRule`) — working reference for roadmap §5d (Declarative Rule DSL).

**Platform extraction opportunity:** Pure graph reasoning (rule engine, pattern evaluator, traversal) could be extracted to platform behind a generic graph interface. Both desiredstate (DAG) and MindMap (general graph) would be consumers — constraint enforcement lives in the graph implementation, not the reasoning layer.

**Short-term:** Extend MindMapAnalyzer with `findPaths`, `reachableFrom`, `connectedComponents` (~S effort each, pure Java on `neighbors()`).

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

## Phase 5: YAML Frontend Layer

**Goal:** Every composable API maps cleanly to YAML. Agents, designers, and ops can define cognitive configuration, vocabulary, trait rules, and derived edge rules in YAML without writing Java. YAML is the authoring frontend; Java is the canonical model.

The platform has a dual-API strategy: Java as the canonical model (builders, DSLs, annotations for programmatic use) and YAML as the configuration/authoring frontend (for non-programmatic definition). If something is hard to express in YAML, that's a signal the Java API isn't orthogonal enough — fix the Java API first.

**Eidos alignment:** Neocortex YAML must be consistent with eidos agent descriptor YAML conventions. The two compose into a single agent definition file — `descriptor:` (eidos, WHO) + `cognitive:` (neocortex, HOW). See [Identity-Memory Integration](identity-memory-integration.md) for the full design.

**Schema generation:** The engine repo (`engine/generator`) uses `victools/jsonschema-generator` to reflect Java records into JSON/YAML schemas at runtime. The same pattern applies here — point the generator at neocortex records and get YAML schemas. Needs a `SealedHierarchyModule` for `FeatureValue`, `CbrFilter`, `TemporalMark`, `SimilaritySpec` (producing `oneOf` with type discriminators). Consider extracting the generator to a shared `casehub-schema-generator` module to avoid duplication.

**Identity-cognition derivation:** Neocortex cognitive config derives defaults from eidos identity declarations. The `derive-from: descriptor` YAML directive bridges WHO and HOW — the YAML loader reads an `AgentDescriptor` and produces default `PersonalityWeights`, `MoodBaseline`, curiosity config, CBR parameters, and extraction biases. See Phase 5f.

### 5a: API-to-YAML Mapping Audit

Before building any YAML layer, audit every builder/DSL/annotation API for YAML-friendliness:

| Java API | YAML shape | Mapping issues |
|----------|-----------|----------------|
| `CbrQuery.of().withFilter().withTemporalDecay()` | Nested map with typed fields | Clean — each `with*()` maps to a YAML key |
| `MindMapQuery` (9-arg constructor) | Flat map | Clean once builders exist (Phase 1b prerequisite) |
| `SimilaritySpec` sealed hierarchy | Discriminator tag | Needs `type:` field to select variant |
| `CbrFilter` sealed hierarchy | Discriminator tag | Same — `type: contains` / `type: containsAll` etc. |
| `TemporalMark` sealed hierarchy | Discriminator tag | `type: wall-clock` / `type: relative` / `type: ordinal` |
| `DerivedEdgeRule` (functional interface) | Declarative rule DSL | Cannot express arbitrary Java logic — need a declarative rule format |
| `TraitRule` (functional interface) | Declarative trait condition DSL | Same — need condition expressions, not code |
| `PersonalityWeights` (Map<MemoryDomain, Double>) | Simple map | Clean |
| `MoodBaseline` (PAD values) | 3 numeric fields | Clean |

Key questions:
- Can every builder chain be expressed as a flat or nested YAML structure?
- Are there APIs that rely on method chaining ORDER (not just content)? These won't map to YAML.
- Do any APIs use lambdas/functional interfaces with no YAML equivalent? These need declarative alternatives.

**Scope:** S — audit only, no code changes. Produces a mapping table identifying all YAML gaps.

### 5b: YAML Schema Design

Design the YAML schema conventions used across all cognitive types:

- **Type discriminators:** `type: gaussian-decay` for sealed variants (follow existing platform patterns)
- **References:** `ref: memory://entityId` for NodeRef cross-references
- **Temporal marks:** `at: 2026-12-25T15:00:00Z` (wall-clock), `in: P3D` (relative), `turn: 42` (ordinal)
- **Affective annotations:** `affect: { pleasure: -0.7, arousal: 0.8, dominance: -0.3 }`
- **Confidence:** `confidence: { origin: STATED, value: 0.95, decay: { half-life: P90D } }`

Ensure consistency with existing platform YAML conventions (check DSL-STYLE-GUIDE.md).

**Scope:** M — schema conventions document, YAML examples, validation rules.

### 5c: Cognitive Profile YAML

A single YAML file that defines an agent's cognitive configuration:

```yaml
cognitive:
  personality:
    weights:
      experience: 1.2
      relationship: 0.8
      reflection: 1.5
  mood:
    baseline:
      pleasure: 0.3
      arousal: 0.4
      dominance: 0.5
  curiosity:
    proximity-scale: 7.0
    affect-dampening: true
    proximity-bypass: true
    topical-distance-max-depth: 4
  memory:
    decay:
      default-half-life: P180D
      per-subgraph-type:
        PERSON: P365D
        PROJECT: P90D
  vocabulary:
    edge-types:
      - canonical: works-at
        aliases: [employed-by, job-at]
        decay-half-life: P365D
      - canonical: parent-of
        aliases: [father-of, mother-of]
```

**Scope:** M — YAML schema + parser + CDI producer for configuration beans.

### 5d: Declarative Rule DSL

`DerivedEdgeRule` and `TraitRule` are functional interfaces — pure Java, no YAML mapping. Design a declarative condition/action DSL that YAML can express:

```yaml
trait-rules:
  - trait: Personable
    when:
      any:
        - has-property: [birthday, role, email]
        - has-edge-type: [parent-of, child-of, works-at]

derived-edge-rules:
  - name: inverse-parent
    when:
      edge-type: has-child
    then:
      create-edge:
        type: parent-of
        source: target    # flip direction
        target: source
        confidence: INFERRED
```

This is the hardest part — expressing rules declaratively without losing the power of programmatic rules. The YAML rules compile to the same `DerivedEdgeRule`/`TraitRule` interfaces, so programmatic and declarative rules coexist. Programmatic rules handle complex logic (graph traversal, multi-step inference); YAML rules handle the common structural patterns (inverses, property presence, edge type matching).

**Scope:** L — DSL design, YAML parser, rule compiler, integration with CDI discovery.

### 5e: YAML-to-Java Compiler/Loader

Runtime component that reads YAML cognitive configuration and produces CDI beans:

- Parses YAML cognitive profile → produces `PersonalityWeights`, `MoodBaseline`, `CuriositySignalGenerator` config
- Parses YAML trait rules → produces `TraitRule` CDI beans
- Parses YAML derived edge rules → produces `DerivedEdgeRule` CDI beans
- Parses YAML vocabulary → calls `registerVocabulary()`

This is a Quarkus build-time or startup-time loader, not a runtime interpreter. YAML files are read once at startup; changes require restart (hot-reload is a future concern).

**Scope:** L — Quarkus extension or `@Startup` loader, CDI bean production, validation.

### 5f: Identity-Cognition Derivation Engine

The bridge between WHO (eidos) and HOW (neocortex). A pure-function derivation engine that reads an `AgentDescriptor` and produces default cognitive configuration.

**Derivation rules:**
- `dispositionProfile` → `PersonalityWeights` (Ni-dominant → reflection=1.5, Fe-dominant → relationship=1.4)
- Disposition axes → `MoodBaseline` (low riskAppetite → lower baseline pleasure, higher arousal)
- Goals + disposition → curiosity category weights (autonomy → STRUCTURAL boost, ruleFollowing → QUALITY boost)
- ruleFollowing + riskAppetite → CBR retrieval parameters (strict vs broad similarity)
- Disposition profile → extraction biases (analytical → relationship-bias, empathetic → affect-sensitivity)

**YAML directive:** `derive-from: descriptor` in the `cognitive:` block triggers derivation. Explicit overrides in the same block take precedence over derived defaults.

**The derivation rules are themselves configurable** — different platforms may have different mappings from disposition to cognition. The default rules implement the personality-cognition research consensus; domain-specific deployments can override them via a `DerivationRuleSet` SPI.

**Implementation:** Pure function `AgentDescriptor → CognitiveDefaults`. No CDI, no state, fully testable. Runs at YAML load time as part of the 5e loader.

See [Identity-Memory Integration](identity-memory-integration.md) for the full design of all 8 integration points.

**Scope:** M — derivation function, default rule set, integration with 5e loader.

### 5g: Memory Space YAML Configuration

YAML surface for defining memory spaces, membership, visibility rules, and scope-based access:

```yaml
memory-spaces:
  - id: smiths-family
    type: shared
    members:
      - { id: alice, roles: [admin, financial-authority], since: 2010-06-15 }
      - { id: bob, roles: [admin, school-authority], since: 2010-06-15 }
      - { id: emma, roles: [member], since: 2012-09-01 }
    scopes:
      calendar:  { visibility: all-members }
      finances:  { visibility: [alice, bob] }
      health:    { visibility: owner-only }
```

Composes with `descriptor:` (eidos) + `cognitive:` (neocortex) in the same agent YAML. Group identity (shared goals, collective values) lives here alongside individual cognitive profiles.

See [Shared Memory Design](shared-memory-design.md) for the full model.

**Scope:** M — YAML schema + parser + integration with visibility layer (1f).

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
- [ ] YAML-expressible: every new API can map to a flat or nested YAML structure without special-casing
- [ ] Shared memory litmus test: does this API/model/query work correctly when the agent sees both private and shared spaces?
- [ ] Perspectival correctness: does affect/confidence on shared entities support per-viewer overlays?

---

## Sequencing & Dependencies

```
Phase 1 (Structural)       Phase 2 (Temporal)        Phase 3 (Affective)       Phase 4 (Queries)        Phase 5 (YAML)
─────────────────────       ─────────────────         ───────────────────       ─────────────────        ──────────────
1a: Unified Confidence ─┐   2a: Temporal Taxonomy ─┐  3a: PAD on Memory ──┐    4a: Entity Resolution   5a: API-to-YAML Audit
1b: Builder APIs ───────┤   2b: Event Timestamps   │  3b: Affect Trajectory   4b: TemporalFocus       5b: YAML Schema Design
1c: Cross-Store Comp. ◄─┘   2c: Temporal MindMap Q │  3c: Prospective Model   4c: Graph Reasoning     5c: Cognitive Profile
1d: Naming Audit ──────────► 2d: Chronological Idx◄─┘  3d: Trajectory Curiosity 4d: Query DSL          5d: Declarative Rule DSL
1e: Forwarding (DONE)                                                                                   5e: YAML-to-Java Loader
```

| Work item | Depends on | Scale | Key deliverable |
|---|---|---|---|
| 1a: Unified Confidence | Done (#229) | — | `cognitive-api` module: `Confidence` record + `ConfidenceOrigin` enum |
| 1b: Builder APIs | — | S | `MindMapQuery.builder()`, `NodeInput.builder()`, `MemoryInput.withX()` |
| 1c: Cross-Store Composability | 1a | M | Generic `RetrievalModulator<T>` for mood/personality/trust |
| 1d: Naming Audit | 1a | S | Terminology table enforced; inconsistencies renamed |
| 1e: Forwarding Store | Done (#223) | — | — |
| 1f: Memory Space Model | — | M | `MemorySpace`, `Visibility`, space membership, visibility layer SPI |
| 2a: Temporal Taxonomy | 1d | M | `TemporalMark` sealed hierarchy |
| 2b: Event Timestamps | 2a | S | `Instant timestamp()` on all event types |
| 2c: Temporal MindMapQuery | 2a | S | `validAfter`/`validBefore`/`updatedAfter` fields |
| 2d: Chronological Index | 2a, 2b, 2c | M | Cross-store `TemporalIndex` — **DONE** (#237) |
| 3a: PAD on Memory | 1d | S | `pleasure`/`arousal`/`dominance` on MemoryInput |
| 3b: Affect Trajectory | 3a | M | `AffectEntry` log per node/entity — **DONE** (#239) |
| 3c: Prospective Events | 2a, 3b | M | Event lifecycle + traits + recurrence |
| 3d: Perspectival Overlays | 1f, 3a | M | Per-agent PAD overlays on shared nodes |
| 3e: Trajectory Curiosity | 3b | S | Updated `CuriositySignalGenerator` |
| 4a: Cross-Store Entity | 1c, 2d | L | `CognitiveProfile` utility — **DONE** (#243) |
| 4b: TemporalFocus | 2d, 3b | M | `AttentionList` — "what's on my mind?" |
| 4c: Graph Reasoning | 4a | Exploration | DesiredState integration assessment — **DONE** (#245) |
| 4d: Query DSL | 4a, 4b | XL | Unified cognitive query language |
| 5a: API-to-YAML Mapping Audit | 1b, 1d | S | Mapping table identifying all YAML gaps |
| 5b: YAML Schema Design | 5a | M | YAML schema conventions document |
| 5c: Cognitive Profile YAML | 5b, 3a | M | Agent cognitive configuration in YAML |
| 5d: Declarative Rule DSL | 5b | L | YAML trait rules + derived edge rules |
| 5e: YAML-to-Java Compiler | 5c, 5d | L | Build-time/startup YAML → CDI bean loader |
| 5f: Identity-Cognition Derivation | 5c, eidos | M | `derive-from: descriptor` → default cognitive config |
| 5g: Memory Space YAML | 5b, 1f | M | YAML memory space configuration + group identity |

**Phase 1** can start immediately — no external dependencies. Items 1a and 1b are parallelisable.

**Phase 2** depends on the naming audit (1d) for consistent temporal terminology. Items 2b and 2c are parallelisable once 2a is done.

**Phase 3** depends on naming (1d) for affective terminology. 3a and 3b are sequential. 3c depends on both temporal (2a) and affect trajectory (3b).

**Phase 4** is the integration phase — depends on everything before it. 4c and 4d are exploratory/long-term.

**Phase 5** depends on builder APIs (1b) and naming audit (1d) — can start the audit (5a) as soon as Phase 1 completes. The cognitive profile YAML (5c) also needs PAD on Memory (3a). The rule DSL (5d) is the hardest item and can run in parallel with Phase 3/4 work. Phase 5 serves as a validation layer: if a Java API is hard to express in YAML, that signals an orthogonality problem in the Java API that should be fixed first.

---

## What This Program Does NOT Cover

- **Blocks integration** — `CuriosityDrive`, `InnerLifeOrchestrator`, and orchestrator tick-loop changes live in blocks, not neocortex. This program prepares the neocortex SPIs; blocks adoption is a separate program.
- **New backends** — no new storage engines (TinkerPop, PostgreSQL graph). The improvements work with existing InMemory + SQLite backends.
- **LLM prompt evolution** — `MindMapExtractor`'s prompts may need updating as the temporal/affective model evolves, but prompt engineering is not a structural concern.
- **Performance** — the chronological index (2d) has performance implications but this program focuses on capability, not optimisation.
- **casehub-life wiring** — this program designs the memory space model and visibility layer. Wiring it to life's family model (household membership, decision authority, care coordination) is a life-side integration task that depends on Phases 1f and 3d being complete.
