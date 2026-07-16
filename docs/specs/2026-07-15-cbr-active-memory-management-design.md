# CBR Active Memory Management — Relevance Decay + Supersession

**Issue:** casehubio/neocortex#152
**Epic:** #93 (CBR Phase 7 — hierarchical scoping + memory lifecycle)
**Date:** 2026-07-15

## Problem

Two gaps in the CBR case base management:

1. **Relevance decay exists but is applied in the wrong place.** `TemporalDecay` SPI is defined on `CbrQuery` with a `HalfLife` variant. `InMemoryCbrCaseMemoryStore` applies it as a post-scoring multiplier inside the store. `QdrantCbrCaseMemoryStore` ignores it entirely. The store-level placement is also incorrect: the reranking decorator (`@Priority(75)`) replaces scores with cross-encoder evaluation, discarding any pre-applied decay factor. Store-level decay has zero effect on the final ranking when reranking is enabled.

2. **No supersession concept.** Cases that have been invalidated (overturned rulings, revised protocols, corrected diagnoses) continue to appear in retrieval results. There is no mechanism to exclude them without deleting them — and deletion destroys the audit trail.

## Scope

**In scope:** Relevance decay (fix placement, add variants), supersession (new SPI, store filter).

**Deferred:** Case compaction (#93 item 7). Compacted cases from different entities can't be decomposed for GDPR Art.17 erasure. Qdrant HNSW is O(log N), so scan reduction from compaction is marginal. Gets its own issue if needed.

---

## §1 Relevance Decay

### 1.1 Decorator placement

New `TemporalDecayCbrCaseMemoryStore` at `@Decorator @Priority(80)`:

```
TrendEnrichment@90 → Decay@80 → Reranking@75 → OutcomeWeighting@65 → Tracking@50 → Store
```

Result flow (outward): Store → Tracking(records) → OutcomeWeighting(adjusts) → Reranking(replaces scores) → **Decay(applies temporal factor)** → TrendEnrichment(passes through).

Decay sees the final reranked scores (or base scores when reranking is disabled) and applies the temporal factor. Final score: `rerankedScore * decayFactor`.

**Activation:** Query-driven. Reads `query.temporalDecay()`. If null, passes through (no-op). No `@IfBuildProperty` — same pattern as TrendEnrichment (schema-driven pass-through).

**Post-decay filtering:** After applying decay, re-filter against `query.minSimilarity()` and re-sort. Results that were above threshold before decay may fall below after.

**Reactive parity:** `ReactiveTemporalDecayCbrCaseMemoryStore` at same priority.

### 1.2 `Instant storedAt` on `ScoredCbrCase`

In CBR, when a precedent was established is domain-relevant — not a storage implementation detail. `caseId` is already on `ScoredCbrCase`; `storedAt` is the same kind of metadata.

Add `Instant storedAt` (nullable) to `ScoredCbrCase`. All stores populate it from their storage timestamp:
- **Qdrant:** Extract from `_stored_at` payload field (already persisted)
- **In-memory:** From the internal `StoredCase.storedAt()` field
- **JPA/SQLite:** From the `stored_at` column

Backward-compatible: existing convenience constructors default `storedAt` to null.

#### `with*` mutation methods

All score or flag modifications on `ScoredCbrCase` MUST use `with*` methods, not constructors. These methods return an immutable copy preserving all fields except the one being changed:

```java
public ScoredCbrCase<C> withScore(double newScore) {
    return new ScoredCbrCase<>(cbrCase, caseId, newScore, reranked, featureSimilarities, storedAt);
}

public ScoredCbrCase<C> withReranked() {
    return new ScoredCbrCase<>(cbrCase, caseId, score, true, featureSimilarities, storedAt);
}
```

This is a mandatory pattern for decorators: `OutcomeWeightingCbrCaseMemoryStore` and `RerankingCbrCaseMemoryStore` currently construct new `ScoredCbrCase` instances with the 5-arg constructor, which would silently drop `storedAt`. Both must be updated to use `withScore()` / `withReranked()` instead.

**OutcomeWeighting** (line 68-69): `new ScoredCbrCase<>(scored.cbrCase(), scored.caseId(), newScore, scored.reranked(), scored.featureSimilarities())` → `scored.withScore(newScore)`

**Reranking** (line 98): `new ScoredCbrCase<C>(...).withReranked()` → `original.withScore(sigmoidScore).withReranked()`

Constructors remain for initial creation (store implementations constructing results from storage), where `storedAt` is provided explicitly or defaulted to null.

### 1.3 Remove store-level decay from InMemoryCbrCaseMemoryStore and JpaCbrCaseMemoryStore

Both stores apply decay inline during retrieval. This must be removed — the decorator is the single point of decay application.

**InMemoryCbrCaseMemoryStore** (lines 131-132):
```java
if (query.temporalDecay() != null) {
    score *= query.temporalDecay().factor(stored.storedAt(), Instant.now());
}
```

**JpaCbrCaseMemoryStore** (lines 145-147):
```java
if (query.temporalDecay() != null) {
    score *= query.temporalDecay().factor(entity.storedAt, Instant.now());
}
```

Both blocks must be removed to avoid double-applied decay when the decorator is active.

### 1.4 New `TemporalDecay` variants

Add to the sealed interface in memory-api:

| Variant | Record | Formula | Validation |
|---------|--------|---------|------------|
| `HalfLife(Duration halfLife)` | Exists | `0.5^(age/halfLife)` | halfLife > 0 |
| `Linear(Duration zeroAt)` | New | `max(0, 1 - age/zeroAt)` | zeroAt > 0 |
| `Step(Duration cutoff, double afterCutoff)` | New | `1.0` if age < cutoff, else `afterCutoff` | cutoff > 0, afterCutoff in [0, 1] |

All implement `double factor(Instant storedAt, Instant now)`. Contract: return 1.0 when `storedAt` is null or age ≤ 0.

**Note:** The existing `HalfLife.factor()` does NOT handle null `storedAt` — `Duration.between(null, now)` throws NPE. `HalfLife` must be updated to add a null guard, and `Linear` and `Step` must include null guards from the start. The null-safety contract lives on the `TemporalDecay` interface — every implementation is independently correct. The decorator's external null guard (§1.5) is an optimization that avoids a virtual call, not the primary safety mechanism.

### 1.5 Decorator implementation

```java
@Decorator
@Priority(80)
public class TemporalDecayCbrCaseMemoryStore implements CbrCaseMemoryStore {

    private final CbrCaseMemoryStore delegate;

    @Inject
    TemporalDecayCbrCaseMemoryStore(@Delegate @Any CbrCaseMemoryStore delegate) {
        this.delegate = delegate;
    }

    @Override
    public <C extends CbrCase> List<ScoredCbrCase<C>> retrieveSimilar(
            CbrQuery query, Class<C> caseType) {
        List<ScoredCbrCase<C>> results = delegate.retrieveSimilar(query, caseType);
        if (query.temporalDecay() == null) {
            return results;
        }
        Instant now = Instant.now();
        TemporalDecay decay = query.temporalDecay();
        List<ScoredCbrCase<C>> decayed = new ArrayList<>(results.size());
        for (var scored : results) {
            double factor = (scored.storedAt() != null)
                ? decay.factor(scored.storedAt(), now) : 1.0;
            double adjustedScore = scored.score() * factor;
            if (adjustedScore >= query.minSimilarity()) {
                decayed.add(scored.withScore(adjustedScore));
            }
        }
        decayed.sort((a, b) -> Double.compare(b.score(), a.score()));
        return Collections.unmodifiableList(decayed);
    }

    // All other methods delegate directly
}
```

### 1.6 Module placement

`TemporalDecayCbrCaseMemoryStore` + reactive variant go in the `memory/` module alongside `TrendEnrichmentCbrCaseMemoryStore` (@90) and `OutcomeWeightingCbrCaseMemoryStore` (@65). All three are lightweight decorators with no external dependencies beyond memory-api.

---

## §2 Supersession

### 2.1 SPI methods

Add to `CbrCaseMemoryStore`:

```java
void supersede(String caseId, String tenantId,
               @Nullable String supersedingCaseId, @Nullable String reason);
void reinstate(String caseId, String tenantId);
```

Reactive parity on `ReactiveCbrCaseMemoryStore`:

```java
Uni<Void> supersede(String caseId, String tenantId,
                    @Nullable String supersedingCaseId, @Nullable String reason);
Uni<Void> reinstate(String caseId, String tenantId);
```

**Semantics:**
- `supersede` sets `supersededAt` to the current timestamp, stores `supersedingCaseId` and `reason` alongside it. If the case is already superseded, `supersededAt` is NOT updated (preserving when the case was first superseded), but `supersedingCaseId` and `reason` are updated if the new values are non-null. This allows metadata correction without a reinstate/re-supersede cycle.
- `reinstate` clears `supersededAt`, `supersedingCaseId`, and `reason` (sets to null). Idempotent — calling on an active case is a no-op. Reinstatement is a structural operation (make the case visible again); reinstatement reasons, if needed, belong in an external audit log, not the case store.
- Both throw `IllegalArgumentException` if `caseId` or `tenantId` is null.
- Neither method throws if the case doesn't exist — silent no-op (consistent with `recordOutcome` behavior).
- `supersedingCaseId` and `reason` are audit metadata — both nullable. In domains like overturned rulings or revised protocols, the reason and the replacing case are critical audit data. Adding these later would be a breaking SPI change across all implementations.

### 2.2 Storage

| Store | Field | Type |
|-------|-------|------|
| Qdrant | `_superseded_at` | Payload field, Long (epoch millis), null = active |
| Qdrant | `_superseding_case_id` | Payload field, String, nullable |
| Qdrant | `_supersession_reason` | Payload field, String, nullable |
| In-memory | `supersededAt` | `Instant` field on internal `StoredCase` record |
| In-memory | `supersedingCaseId` | `String` field on internal `StoredCase` record, nullable |
| In-memory | `supersessionReason` | `String` field on internal `StoredCase` record, nullable |
| JPA | `superseded_at` | `TIMESTAMP` column, nullable |
| JPA | `superseding_case_id` | `VARCHAR` column, nullable |
| JPA | `supersession_reason` | `VARCHAR` column, nullable |

### 2.3 Retrieval filter

`CbrQueryTranslator.toIdentityFilter()` in memory-qdrant adds a `IsNull("_superseded_at")` condition. Superseded cases never appear in `retrieveSimilar()` results. This is unconditional — no query flag to include superseded cases.

For in-memory: `InMemoryCbrCaseMemoryStore.retrieveSimilar()` skips cases where `stored.supersededAt() != null`.

For JPA: `JpaCbrCaseMemoryStore.retrieveSimilar()` adds `AND e.supersededAt IS NULL` to the JPQL query at line 119. The current query filters only by tenantId, domain, caseType, and optionally notBefore — without this condition, superseded cases appear in JPA retrieval results.

**`toFilter()` deletion:** `CbrQueryTranslator.toFilter()` is dead code — zero production callers (only referenced by `CbrQueryTranslatorTest`). Its Javadoc says "Retained for backward compatibility" but there is no backward compatibility concern in this platform. Delete `toFilter()` and its tests to eliminate the risk of a future caller bypassing the supersession filter.

### 2.4 Supersession and other operations

| Operation | Behavior with superseded cases |
|-----------|-------------------------------|
| `store()` | Not affected — new cases are always active |
| `retrieveSimilar()` | Superseded cases excluded by filter |
| `recordOutcome()` | Allowed on superseded cases — outcome data is independent of active status |
| `erase()` / `eraseEntity()` | Erases superseded cases too — GDPR erasure is unconditional |
| `purge()` | Purges superseded cases too — retention applies regardless of status |
| `supersede()` | Sets `supersededAt` on first call; subsequent calls update metadata only (timestamp preserved) |
| `reinstate()` | Clears all supersession metadata (`supersededAt`, `supersedingCaseId`, `reason`); idempotent |

### 2.5 Decorator chain impact

Supersession is a store-level filter, not a decorator. The decorators never see superseded cases because the store filters them out before returning — no behavioral changes to decorator retrieval logic.

However, adding `supersede()` and `reinstate()` to the `CbrCaseMemoryStore` interface requires all implementations to provide them. Every decorator must add mechanical delegation (`delegate.supersede(...)` / `delegate.reinstate(...)`).

**Blocking decorators requiring delegation:**
- `TrendEnrichmentCbrCaseMemoryStore` (memory/)
- `OutcomeWeightingCbrCaseMemoryStore` (memory/)
- `RerankingCbrCaseMemoryStore` (memory-cbr-crossencoder/)
- `TrackingCbrCaseMemoryStore` (memory-cbr-tracking/)
- `NoOpCbrCaseMemoryStore` (memory/) — empty implementations
- `BlockingToReactiveCbrBridge` (memory/) — delegates to blocking store

**Reactive decorators requiring delegation:**
- `ReactiveTrendEnrichmentCbrCaseMemoryStore` (memory/)
- `ReactiveOutcomeWeightingCbrCaseMemoryStore` (memory/)
- `ReactiveRerankingCbrCaseMemoryStore` (memory-cbr-crossencoder/)
- `ReactiveTrackingCbrCaseMemoryStore` (memory-cbr-tracking/)

---

## §3 Contract Tests

### 3.1 New contract tests (CbrCaseMemoryStoreContractTest)

**storedAt population:**
- `storedAt_populatedOnRetrievedCases` — store a case, retrieve it, assert `storedAt` is non-null and recent

**Supersession (contract-level — SPI-observable behavior only):**
- `supersede_excludesFromRetrieval` — store, supersede, retrieve → empty
- `reinstate_restoresRetrievalVisibility` — store, supersede, reinstate, retrieve → found
- `supersede_alreadySuperseded_noThrow` — double supersede doesn't throw, case remains excluded
- `reinstate_idempotent` — reinstate active case doesn't throw
- `supersede_nonExistentCase_noOp` — supersede unknown caseId doesn't throw
- `supersede_eraseStillWorks` — superseded case is still erasable
- `supersede_recordOutcomeStillWorks` — can record outcome on superseded case
- `supersede_purgeStillApplies` — retention purge includes superseded cases

**Supersession metadata-update (store-level — per-store test classes):**

The metadata-update semantics from §2.1 (timestamp preserved, metadata correctable) cannot be verified through the SPI because supersession metadata is not SPI-readable (deferred to #155). These tests belong in each store's own test class (`InMemoryCbrCaseMemoryStoreTest`, `JpaCbrCaseMemoryStoreTest`, `QdrantCbrCaseMemoryStoreTest`), where internal state can be queried directly:

- `supersede_repeatCall_preservesTimestamp` — supersede twice, verify `supersededAt` unchanged
- `supersede_repeatCall_updatesNonNullMetadata` — supersede with reason A, supersede with reason B, verify B stored
- `supersede_repeatCall_nullDoesNotOverwrite` — supersede with reason, supersede with null reason, verify original preserved

### 3.2 Decay decorator tests (memory/ module)

- `decay_nullTemporalDecay_passThrough` — no decay on query, results unchanged
- `decay_halfLife_appliesExponentialFactor` — verify score adjustment
- `decay_linear_rampToZero` — linear decay hits zero at boundary
- `decay_step_thresholdBehavior` — step function transitions at cutoff
- `decay_refiltersMinSimilarity` — results below threshold after decay are excluded
- `decay_resorts` — ordering changes after decay application
- `decay_nullStoredAt_factorIsOne` — null storedAt treated as no decay

### 3.3 Chain integration test (memory-cbr-tracking/ module, `DecoratorChainIntegrationTest`)

- `storedAt_preservedThroughDecoratorChain` — store a case, retrieve through a chain with OutcomeWeighting and/or Reranking active, assert the decay decorator receives non-null `storedAt` and applies the temporal factor correctly. Validates that `with*` methods preserve `storedAt` end-to-end.

---

## §4 Implementation Summary

| Module | Changes |
|--------|---------|
| **memory-api** | `TemporalDecay`: add `Linear`, `Step` records; update `HalfLife.factor()` for null `storedAt`. `ScoredCbrCase`: add `Instant storedAt`, `withScore(double)`, update `withReranked()` to preserve all fields. `CbrCaseMemoryStore`: add `supersede()`, `reinstate()` with audit metadata. `ReactiveCbrCaseMemoryStore`: reactive parity. |
| **memory/** | New `TemporalDecayCbrCaseMemoryStore` @Decorator @Priority(80) + reactive. `OutcomeWeightingCbrCaseMemoryStore` + reactive: use `withScore()` instead of constructing new instances. `TrendEnrichmentCbrCaseMemoryStore` + reactive: delegate `supersede()`/`reinstate()`. `BlockingToReactiveCbrBridge`: delegate new methods. `NoOpCbrCaseMemoryStore`: empty implementations. |
| **memory-cbr-inmem** | Remove store-level decay. Populate `storedAt`. Implement `supersede()`, `reinstate()`. |
| **memory-qdrant** | Populate `storedAt`. Implement `supersede()`, `reinstate()`. Add `_superseded_at` filter in `CbrQueryTranslator.toIdentityFilter()`. Delete dead-code `toFilter()` method and its tests. |
| **memory-cbr-jpa** | Remove store-level decay. Implement `supersede()`, `reinstate()`. Add `AND e.supersededAt IS NULL` to retrieval JPQL. Add Flyway migration for `superseded_at`, `superseding_case_id`, `supersession_reason` columns. Populate `storedAt`. |
| **memory-cbr-crossencoder** | `RerankingCbrCaseMemoryStore` + reactive: use `withScore().withReranked()` instead of constructing new instances. Delegate `supersede()`/`reinstate()`. |
| **memory-cbr-tracking** | `TrackingCbrCaseMemoryStore` + reactive: delegate `supersede()`/`reinstate()`. Chain integration test per §3.3. |
| **memory-testing** | Contract tests per §3.1. Decay decorator unit tests per §3.2. |

---

## §5 Design Decisions

### DD-1: Decay as decorator, not store-level

Store-level decay is discarded by the reranking decorator. Decorator at @Priority(80) applies after reranking, producing correct final rankings regardless of which decorators are active.

### DD-2: `storedAt` is domain data

When a precedent was established is domain-relevant in CBR. `storedAt` on `ScoredCbrCase` is the same kind of metadata as `caseId` — not storage leakage.

### DD-3: Query-driven activation for decay

No `@IfBuildProperty`. The decorator reads `query.temporalDecay()` — if null, passes through. Matches TrendEnrichment's schema-driven pass-through pattern. Callers control decay per-query: aggressive decay for "what worked recently?", no decay for "has this ever happened?".

### DD-4: Supersession as store-level filter

Superseded cases are unconditionally excluded — this is a hard constraint (correctness), not a score adjustment. Store-level filtering in `toIdentityFilter()` is the right mechanism, consistent with how tenantId/domain/caseType are filtered.

### DD-5: Compaction deferred

GDPR Art.17 complication (compacted cases from different entities can't be decomposed for erasure) and questionable benefit (Qdrant HNSW is O(log N)) make compaction a premature optimization. Retention policies and `minSimilarity` thresholds address the same concern with less risk.

### DD-6: Supersession is reversible

`reinstate()` reverses `supersede()`. Real domains need this — incorrect supersession, overturning of overturnings. Both operations are idempotent.
