# Neocortex Cognitive Coherence Audit

An analysis of how well the 17 cognitive types compose, with specific gaps and recommendations across five dimensions: composability, temporal coherence, MindMap temporal design, affective tagging, and future-facing knowledge.

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
| **Two confidence models, not unified** | `ConfidenceOrigin` (STATED/INFERRED/SPECULATED with initialConfidence) for MindMap; `CbrOutcome` (EMA-adjusted double) for CBR; `importance` (Double) for text memory. Same concept — "how sure are we?" — three different representations. | Cannot sort across subsystems by confidence. A MindMap node at confidence 0.7 and a CBR case at confidence 0.7 mean different things (origin-initial vs EMA-adjusted). |
| **No cross-system query** | Cannot ask "everything about Alice" in one call. Must query CaseMemoryStore (by entityId), CbrCaseMemoryStore (by features), and MindMapStore (by resolveNode) separately, then merge results manually. | NodeRef provides the *reference* but no *resolution* utility. The guide describes cross-system entity understanding as an emergent capability, but no utility implements the traversal. |
| **ExperienceEvent has no MindMap reference** | ExperienceEvent carries agentId, tenantId, caseId, turnId, description — but no nodeId or NodeRef. Cannot link an experience to a MindMap entity at creation time. | The link must be created separately via MindMapExtractor or manual NodeRef construction. |
| **MemoryInput has no builder** | 7-arg constructor. Has `withAttribute()`, `withText()` but no factory methods or builder for common patterns. ExperienceEvents, MoodEvents, RelationshipEvents, EngagementEvents all have dedicated converter classes — but ad-hoc memory creation is verbose. | Minor — the converters handle 90% of cases. |

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
| **ExperienceEvent** | `turnId` only (no Instant) | No (queries via MemoryQuery.since after conversion) | Via MemoryQuery | Via text memory recency |
| **RelationshipEvent** | `turnId` only | No | Via MemoryQuery | Via text memory recency |
| **ReflectionEvent** | `turnId` only | No | Via MemoryQuery | Via text memory recency |
| **MoodState** | `turnId` only | No | Via text memory | Via text memory recency |
| **EngagementEvent** | `turnId` only | No | Via MemoryQuery | Via text memory recency |

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
| `validFrom` / `validUntil` on nodes | Present on `MindMapNode` interface | Nullable Instant fields |
| `validFrom` / `validUntil` on edges | Present on `MindMapEdge` interface | Nullable Instant fields |
| `createdAt` / `updatedAt` on both | Present | Set by backends at creation/mutation time |
| `confirmedAt` on nodes | Present | Reset by `NodeUpdate.confirmedAt` — resets decay clock |
| Confidence decay decorator | Implemented | `ConfidenceDecayDecorator.applyDecay()` — uses `confirmedAt` for nodes, `updatedAt` for edges |
| Proximity signals in curiosity engine | Implemented | `CuriositySignalGenerator.collectProximitySignals()` — iterates `nodesIn()`, checks `validFrom.isAfter(now)` |
| Past-event detection | Implemented | Checks `validUntil.isBefore(now)` — generates "did it happen?" signals |
| Temporal query on MindMapQuery | **Missing** | No `validAfter`/`validBefore` fields |
| Per-edge-type decay rates | **Missing** | `ConfidenceDecayDecorator` uses single `defaultHalfLifeDays` for everything. `EdgeTypeDefinition.defaultDecayHalfLifeDays` exists in the vocabulary definition but the decorator never reads it. |

### Inconsistency: decay reference point

- **Nodes**: decay from `confirmedAt` (explicit confirmation resets clock). If `confirmedAt` is null, no decay applied (returns raw confidence).
- **Edges**: decay from `updatedAt` (any mutation resets clock). No `confirmedAt` on edges.

This asymmetry means: a node that was created 2 years ago but never confirmed decays to near-zero. An edge created 2 years ago but updated yesterday (even with a trivial property change) shows full confidence. The edge should also have `confirmedAt` — the spec review (R1-07) flagged this and the spec was updated to include `EdgeUpdate` with `confirmedAt`, but the code hasn't caught up.

### Recommendations

1. **Add `confirmedAt` to MindMapEdge** and implement `updateEdge()` on MindMapStore (spec already specifies this — R1-07 from the spec review).
2. **Add temporal filtering to MindMapQuery** — `validAfter` and `validBefore` fields.
3. **Wire per-edge-type decay** — ConfidenceDecayDecorator should read `EdgeTypeDefinition.defaultDecayHalfLifeDays` from the registered vocabulary, falling back to the global default.

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
| **RelationshipEvent** | `qualitySignal` (POSITIVE/NEGATIVE/NEUTRAL) | 3-value enum | Not PAD — coarser |
| **EngagementEvent** | `sentimentShift` (Double) | Scalar shift | Nullable, but not PAD |
| **CbrCase** | **None** | N/A | N/A |
| **ReflectionEvent** | **None** | N/A | N/A |

### Key findings

**Affective tagging is MindMap-only.** Only MindMap nodes and edges carry full PAD annotations. Text memories (the largest store) have NO affective fields. You cannot tag a text memory with "this is a bad memory" — not even via the attributes map, since `MoodModulatedRetrieval` doesn't read attributes for PAD values on non-mood memories.

**MoodModulatedRetrieval works, but only on MindMap-annotated memories.** The `moodFactor()` method reads PAD values from the memory's attributes — specifically `MoodAttributeKeys.PLEASURE`, `AROUSAL`, `DOMINANCE`. But these are only set when MoodState is stored as a memory (domain="mood"). Regular text memories, experiences, relationships, and reflections have no PAD attributes. So mood-congruent retrieval only boosts mood-domain memories, not all memories by their emotional valence.

**QualitySignal (3-value) and PAD (3-axis) are different models.** RelationshipEvent uses a coarse POSITIVE/NEGATIVE/NEUTRAL signal. This doesn't map cleanly to PAD — a NEGATIVE relationship could be high-arousal (conflict) or low-arousal (neglect). The two models don't compose.

### Recommendations

1. **Add optional PAD fields to MemoryInput** — `Double pleasure`, `Double arousal`, `Double dominance`. Store them in the attributes map with standard keys. This allows any memory to carry emotional valence.
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
| **No event lifecycle** | No status field on nodes (PLANNED → HAPPENING → COMPLETED → REVIEWED). `validFrom`/`validUntil` define the temporal window but don't track whether the event was attended, cancelled, or rescheduled. | "Holiday Dec 25" after Dec 25 still shows as a past event — no distinction between "happened as planned" and "was cancelled." |
| **No recurring events** | No recurrence model (weekly meeting, annual holiday). Each occurrence must be a separate node. | Manual node creation for every instance of a recurring event. |
| **No priority/urgency model** | Future events carry confidence (STATED/SPECULATED) and proximity score, but no explicit priority. "Dentist appointment" and "job interview" tomorrow get the same proximity score. | All events are equal — the curiosity engine can't distinguish important from trivial. |
| **Plans and aspirations are nodes, not a distinct type** | "Hope to start new job" is a MindMapNode with SPECULATED confidence. There's no semantic distinction between an aspiration, a plan, a prediction, and a fact — only the confidence level. | An agent can't query "what are my aspirations?" separately from "what are my facts?" without a trait or property convention. |

### Recommendations

1. **Add temporal filtering to MindMapQuery** (repeating from Dimension 3 — this is the single most impactful improvement). Fields: `Instant validAfter`, `Instant validBefore`.
2. **Establish a `status` property convention** for event nodes — `status=planned|active|completed|cancelled|reviewed`. Not a core field — a property convention enforced by the trait system. A `TemporalEventTraitRule` could auto-apply a "TemporalEvent" trait when `validFrom` is set.
3. **Add an `intent` property convention** — `intent=fact|plan|aspiration|prediction`. Combined with confidence, this distinguishes "I accepted a job" (fact, STATED) from "I hope to get a job" (aspiration, SPECULATED) from "I think I'll get the job" (prediction, INFERRED).
4. **Priority can be modelled as importance** — add a `Double importance` field to NodeInput (parallel to MemoryInput.importance). High-importance future events generate stronger curiosity signals.

---

## Cross-Cutting Summary

| Dimension | Verdict | Top Priority |
|-----------|---------|-------------|
| **Composability** | Good for CBR/Memory; weak for MindMap queries | Add `with*()` methods to MindMapQuery |
| **Temporal coherence** | Fragmented — 3 different temporal models | Add `Instant timestamp()` to all event types; unify query naming |
| **MindMap temporal** | Rich fields, no query support | Add `validAfter`/`validBefore` to MindMapQuery; wire per-edge-type decay |
| **Affective tagging** | MindMap-only; text memories are affect-blind | Add PAD fields to MemoryInput; update MoodModulatedRetrieval |
| **Future-facing** | Mechanically works; semantically thin | Property conventions for status + intent; temporal query support |

**The single highest-impact change across all dimensions: add temporal filtering to MindMapQuery.** It's needed for upcoming-event queries (D5), curiosity signal efficiency (D3), and temporal coherence (D2). Every other recommendation is secondary to this.
