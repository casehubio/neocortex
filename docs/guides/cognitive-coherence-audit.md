# Neocortex Cognitive Coherence Audit

Five-dimensional analysis of how well the 17 cognitive function groups compose, handle time, carry affect, and support future-facing knowledge.

---

## Dimension 1: Composability

**What works well:**
- `CbrQuery` has excellent composability: `of()` factory + 14 `with*()` builder methods (withProblem, withFilter, withTemporalDecay, etc.)
- `MemoryQuery` follows the same pattern: `forEntity()` factory + `withSince()`, `withOrder()`, `withLimit()`
- CDI events provide loose coupling — `ExperienceRecorded` cascades to relationships, reflections without explicit wiring
- All memory types serialise to `MemoryInput` via domain-tagged converters — single store, multiple cognitive functions

**Gaps and tensions:**

| Issue | Detail |
|-------|--------|
| **MindMapQuery has no builders** | 9-arg constructor, no `with*()` methods. Composing a graph query requires constructing the full record with positional nulls |
| **Two confidence models** | `ConfidenceOrigin` (STATED/INFERRED/SPECULATED with initial values) on MindMap vs `CbrOutcome` (EMA-adjusted double) on CBR vs `importance` (nullable Double) on Memory. Three concepts doing similar work |
| **No mood-weighted CBR** | `MoodModulatedRetrieval` works on `List<Memory>` only. CBR retrieval (`ScoredCbrCase`) has no mood/affect hook. Personality weighting also only applies to text memories |
| **No ExperienceEvent → MindMap link** | Experiences have no NodeRef or graph node reference. The CDI cascade doesn't create MindMap nodes — that requires explicit `MindMapExtractor` invocation |
| **MemoryInput has limited builders** | Only `withAttribute()`, `withText()`. No `withImportance()`, no `withDomain()` |

**Recommendation:** Add `with*()` builders to `MindMapQuery`. Consider a unified `Confidence` type that wraps origin + numeric value + decay metadata, used across all stores.

---

## Dimension 2: Temporal Coherence

| Store | Timestamp fields | Time-range query | Recency sort |
|-------|-----------------|------------------|--------------|
| **Memory** | `createdAt` | `MemoryQuery.since(Instant)` | `MemoryOrder` |
| **CbrCase** | None on interface | `CbrQuery.notBefore(Instant)` | `TemporalDecay` post-scoring |
| **MindMapNode** | `createdAt`, `updatedAt`, `confirmedAt`, `validFrom`, `validUntil` | **None** | **None** |
| **MindMapEdge** | `createdAt`, `updatedAt`, `validFrom`, `validUntil` | **None** | **None** |
| **ExperienceEvent** | `turnId` only (no Instant) | Via MemoryQuery.since after conversion | Via MemoryQuery |
| **MoodState** | `turnId` only | Via MemoryQuery after conversion | Via MemoryQuery |
| **EngagementEvent** | `turnId` only | Via MemoryQuery after conversion | Via MemoryQuery |
| **RelationshipEvent** | `turnId` only | Via MemoryQuery after conversion | Via MemoryQuery |

**Critical gap:** `MindMapQuery` has NO temporal query capability — no `since`, `before`, `validFromBefore`, `validFromAfter` fields. You cannot ask "what nodes have validFrom in the next 7 days?" at the SPI level. The `CuriositySignalGenerator` works around this by iterating ALL nodes via `nodesIn()` and filtering in Java.

**Event types lack Instant timestamps** — they carry `turnId` (string) but no `Instant`. Temporal queries only work after conversion to `Memory` (which gets `createdAt` at store time).

**Recommendation:** Add temporal query fields to `MindMapQuery`: `validFromAfter(Instant)`, `validFromBefore(Instant)`, `updatedAfter(Instant)`. Add `Instant timestamp()` to `ExperienceEvent`, `MoodState`, `EngagementEvent`, `RelationshipEvent`.

---

## Dimension 3: MindMap Temporal

**What works:**
- `MindMapNode` has 5 temporal fields: `createdAt`, `updatedAt`, `confirmedAt`, `validFrom`, `validUntil`
- `MindMapEdge` has 4: `createdAt`, `updatedAt`, `validFrom`, `validUntil`
- `ConfidenceDecayDecorator` uses `confirmedAt` for node decay, `updatedAt` for edge decay
- `CuriositySignalGenerator.collectProximitySignals()` uses `validFrom` for future events and `validUntil` for past events

**Gaps:**

| Issue | Impact |
|-------|--------|
| `MindMapQuery` cannot filter by ANY temporal field | All temporal logic is in-memory post-retrieval — O(n) scan of all nodes |
| No `confirmedAt` on edges | Edge confidence decays from `updatedAt` with no way to confirm without modifying the edge |
| `ConfidenceDecayDecorator` skips `getEdge()` | Single-edge lookups bypass decay (caught in spec review R1-08) |
| No "between dates" query | Calendar view / timeline is impossible without iterating everything |

**Recommendation:** Add `confirmedAt` to `MindMapEdge`. Add `getEdge()` interception to `ConfidenceDecayDecorator`. Add temporal predicates to `MindMapQuery`.

---

## Dimension 4: Affective Tagging

| Store type | PAD fields | Nullable | Can tag emotions |
|------------|-----------|----------|-----------------|
| **MindMapNode** | pleasure, arousal, dominance | All `Double` (nullable) | Yes — on the knowledge itself |
| **MindMapEdge** | pleasure, arousal, dominance | All `Double` (nullable) | Yes — on relationships |
| **Memory / MemoryInput** | **None** | — | **No** — only via string attributes |
| **CbrCase** | **None** | — | Only via FeatureValue if schema defines it |
| **ExperienceEvent** | **None** | — | **No** |
| **MoodState** | pleasure, arousal, dominance | Non-nullable (primitives) | This IS the mood, not tagged onto something |
| **EngagementEvent** | `sentimentShift` (Double) | Nullable | Partial — one dimension only |

**The inconsistency:** MindMap has first-class PAD on knowledge. Text memory has nothing — `MoodModulatedRetrieval.moodFactor()` reads PAD from `memory.attributes()` using `MoodAttributeKeys`, but only for mood-domain memories. You cannot tag an arbitrary text memory with "this is a bad memory (pleasure=-0.8)" in a typed way.

To answer the user's question directly: **no, not every memory can be tagged with mood/emotion/feeling**. Only MindMap nodes/edges have typed PAD fields. Text memories would need to use the untyped `attributes` map (string keys, string values). CBR cases and experience events have no affective capability at all.

**Recommendation:** Add nullable `Double pleasure, Double arousal, Double dominance` to `MemoryInput` and `Memory`. This aligns text memory with MindMap's affective model and enables affect-weighted retrieval across all memory types, not just graph nodes.

---

## Dimension 5: Future-Facing Knowledge

**What works:**

| Scenario | Supported | How |
|----------|-----------|-----|
| "Accepted a job" | Yes | Node with STATED confidence, `validFrom` = start date |
| "Hoping to start a job" | Yes | Node with SPECULATED confidence, `validFrom` = hoped date |
| "Holiday on Dec 25" | Yes | Node with `validFrom` = Dec 25 |
| Past events generating follow-up | Yes | `validUntil < now` → "did it happen?" curiosity signal |

**What doesn't work:**

| Scenario | Gap |
|----------|-----|
| "What's coming up in 7 days?" | Must iterate ALL nodes, filter in Java. No `MindMapQuery` temporal filter |
| Calendar view / timeline | No sorted temporal query. No "between dates" query on the SPI |
| Interleaving past facts + future plans | Both exist as nodes, but no unified temporal index across MindMap + Memory |
| "What should I focus on now?" | `CuriositySignalGenerator` provides signals but has no concept of "current focus" aggregating proximity + recency + importance across stores |
| Distinguishing event types | A holiday, a work meeting, and an aspiration are all just nodes with `validFrom` — no event type taxonomy |

**The fundamental missing piece:** There is no **unified temporal index** across cognitive types. MindMap nodes have `validFrom`/`validUntil` but no temporal query. Text memories have `createdAt` and `since`-query but no future dates. CBR has `notBefore` but no "upcoming" concept. A "what should I think about right now?" query requires manually querying all three stores and merging results in Java.

**Recommendation:**
1. Add temporal fields to `MindMapQuery` (`validFromAfter`/`validFromBefore`, `updatedAfter`)
2. Consider a cross-store `TemporalFocus` utility that aggregates upcoming MindMap events + recent memories + recent experiences into a single ranked "attention list" — the cognitive equivalent of "what's on my mind right now"
3. Consider an event type taxonomy (or use traits: `Appointable`, `Aspirational`, `Deadline`) to distinguish between calendar events, hopes, and hard commitments

---

## Summary: Priority Improvements

| Priority | Change | Impact |
|----------|--------|--------|
| **High** | Add temporal predicates to `MindMapQuery` | Unlocks temporal queries on the graph — "what's coming up?" without O(n) scan |
| **High** | Add PAD fields to `MemoryInput`/`Memory` | Every memory type gets typed affective tagging, not just MindMap |
| **High** | Add `with*()` builders to `MindMapQuery` | Composability parity with CbrQuery/MemoryQuery |
| **Medium** | Add `confirmedAt` to `MindMapEdge` | Edge confidence decay can be reset without mutation |
| **Medium** | Add `Instant timestamp()` to event types | Direct temporal queries on experiences without Memory conversion |
| **Medium** | Unified `Confidence` type | Single model across MindMap, CBR, and Memory instead of three |
| **Low** | Cross-store `TemporalFocus` utility | "What's on my mind right now?" aggregation |
| **Low** | Event type traits on MindMap nodes | Distinguish calendar entries, aspirations, deadlines |

---

## Design Observation

The system was built layer by layer — text memory first, then CBR, then MindMap, then intelligence. Each layer is internally consistent and well-designed. The tensions arise at **cross-layer boundaries**: temporal queries that work in Memory but not MindMap, affective annotations that work in MindMap but not Memory, builder APIs in CBR but not MindMap. The architecture is sound — these are integration gaps, not design flaws. Filling them would make the 17 cognitive types feel like one system rather than 17 composable parts.
