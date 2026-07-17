# CBR Hierarchical Scoping Design

**Issue:** casehubio/neocortex#153
**Date:** 2026-07-16
**Status:** Approved

## Problem

CBR cases are partitioned by flat `tenantId` + `MemoryDomain` + `caseType` with equality-based filtering. There is no concept of hierarchical visibility — a patient-level case and a trial-level case are simply in different domains or mixed together in the same domain with no structural relationship.

Clinical and multi-scope domains need a hierarchy: entity-level cases (patient), group-level cases (site), domain-level cases (trial), global cases (cross-trial). Queries should prefer local scope and fall back to broader scopes with configurable decay.

## Decisions

1. **Scope is orthogonal to domain.** `MemoryDomain` stays as a semantic namespace ("safety", "cardiology"). `Path scope` is a new dimension representing visibility hierarchy within a tenant+domain partition.

2. **Use platform `Path`.** `io.casehub.platform.api.path.Path` from `casehub-platform-api` — segmented hierarchical path with `parent()`, `isAncestorOf()`, `depth()`. Already a platform type with 106+ usages. No new hierarchy type needed.

3. **Blended retrieval with scope decay.** Cases from all ancestor scopes are included in results. A `ScopeDecay` function (on `CbrQuery`, like `TemporalDecay`) applies a score multiplier based on depth distance. Store-level filtering prevents child-scope cases from leaking upward.

4. **Aggregates are regular cases.** Cross-scope aggregation (e.g., "3 of 5 sites show AE rate above threshold") is the domain's responsibility. The domain creates a regular `CbrCase` at a higher scope with provenance tracked via feature fields. No aggregation SPI. **Aggregate integrity after entity erasure** is also the domain's responsibility — scope isolation guarantees that `eraseEntity()` does not cascade to aggregate-scope cases (different scope, different entityId), but the domain must recompute aggregates when source data changes. See #159 for the reactive notification mechanism.

5. **No scope-based erasure in this issue.** `entityId` remains the GDPR Art.17 anchor. Scope-based bulk erasure (`eraseByScope`) filed as #158 for follow-on.

6. **ScopeDecay on CbrQuery.** Symmetric with `TemporalDecay` — query-level, nullable, applied by a decorator.

## Scope Model

Scope is represented by `Path`. Each CBR case is stored with a scope that positions it in a hierarchical tree within a tenant+domain partition.

```
Path.root()                                          → global (cross-trial)
Path.of("trial-alpha")                               → trial scope
Path.of("trial-alpha", "site-north")                 → site scope
Path.of("trial-alpha", "site-north", "patient-42")   → patient scope
```

The depth is implicit — there is no fixed entity/group/domain/global enum. A 2-level hierarchy works the same as a 4-level one. Domain-specific naming conventions are the application's concern.

### Visibility Rule

A case is visible to a query if the case's scope equals the query scope OR is an ancestor of the query scope. Child-scope (more specific) cases never leak upward.

| Stored scope | Query scope | Visible? | Why |
|---|---|---|---|
| root | anything | yes | root is ancestor of everything |
| `trial-alpha` | `trial-alpha/site-north/patient-42` | yes | ancestor |
| `trial-alpha/site-north` | `trial-alpha/site-north` | yes | exact match |
| `trial-alpha/site-north/patient-42` | `trial-alpha/site-north` | **no** | child not visible from parent |
| `trial-alpha/site-north` | `trial-beta` | **no** | different branch |

### Relationship to Existing Concepts

- `tenantId` — security boundary (unchanged)
- `MemoryDomain` — semantic namespace (unchanged)
- `Path scope` — **new** — visibility hierarchy within a tenant+domain partition

## API Changes

### CbrCaseMemoryStore

Add `Path scope` as the last parameter to `store()`:

```java
String store(CbrCase cbrCase, String caseType, String entityId, MemoryDomain domain,
             String tenantId, String caseId, Path scope);
```

`scope` is required, non-null. Callers that don't care about hierarchy pass `Path.root()`.

**Scope immutability:** `scope` is set at store time and cannot be changed, consistent with `entityId`, `domain`, and `tenantId`. If a case needs to move to a different scope (e.g., patient transfers between sites), store a new case at the new scope and use `supersede()` to mark the old one.

**Root query semantics:** A query with `Path.root()` sees only root-scope cases — not all cases. Root has no ancestors, and child-scope cases don't leak upward. This is correct by the visibility model but is a semantic shift for callers: pre-scope queries saw all cases; post-scope root queries see only root-scope cases. Since the Flyway migration defaults existing rows to root scope (see §JPA Migration below), existing callers see all their existing cases. New cases stored at non-root scopes are deliberately invisible to root queries. Scope-agnostic listing (e.g., for admin or diagnostics) is outside the `retrieveSimilar` contract and handled by direct store access.

`ReactiveCbrCaseMemoryStore` — same change, returns `Uni<String>`.

No changes to `erase()`, `eraseEntity()`, `recordOutcome()`, `purge()`, `supersede()`, `reinstate()`.

### CbrQuery

Add `Path scope` (required) and `ScopeDecay scopeDecay` (nullable):

```java
public record CbrQuery(
    String tenantId,
    MemoryDomain domain,
    String caseType,
    Map<String, FeatureValue> features,
    Map<String, CbrFilter> filters,
    Map<String, Double> weights,
    int topK,
    double minSimilarity,
    Instant notBefore,
    String problem,
    double vectorWeight,
    RetrievalMode retrievalMode,
    FusionStrategy fusionStrategy,
    TemporalDecay temporalDecay,
    Path scope,
    ScopeDecay scopeDecay
)
```

`scope` validated non-null in compact constructor. `scopeDecay` nullable — null means all visible scopes score equally.

Factory updated:

```java
public static CbrQuery of(String tenantId, MemoryDomain domain, Path scope,
                          String caseType, Map<String, FeatureValue> features, int topK)
```

New methods: `withScope(Path)`, `withScopeDecay(ScopeDecay)`.

### ScoredCbrCase

Add `Path scope`:

```java
public record ScoredCbrCase<C extends CbrCase>(
    C cbrCase, String caseId, double score, boolean reranked,
    Map<String, Double> featureSimilarities, Instant storedAt,
    Path scope
)
```

The scope decay decorator reads `scope` to compute depth distance. Existing convenience constructors default to `Path.root()`.

## ScopeDecay

Sealed interface analogous to `TemporalDecay` — pure function from depth distance to score multiplier. Unlike `TemporalDecay.factor(Instant, Instant)` which encapsulates both distance computation and decay (since temporal distance depends on wall-clock time), `ScopeDecay.factor(int)` takes a pre-computed depth distance because scope distance is a pure structural property of two `Path` values. The decorator computes `depthDistance` and passes it in — this is intentionally simpler, not an accidental divergence.

```java
public sealed interface ScopeDecay {

    double factor(int depthDistance);

    record Exponential(double base) implements ScopeDecay {
        // factor = base^depthDistance
        // base=0.5 → exact=1.0, parent=0.5, grandparent=0.25
    }

    record Linear(int maxDepth) implements ScopeDecay {
        // factor = max(0, 1 - depthDistance / maxDepth)
        // maxDepth=3 → exact=1.0, parent=0.67, grandparent=0.33, great=0.0
    }

    record Step(double beyondExact) implements ScopeDecay {
        // factor = 1.0 for depthDistance==0, beyondExact for any ancestor
        // beyondExact=0.3 → exact=1.0, everything else=0.3
    }
}
```

`depthDistance` is `queryScope.depth() - caseScope.depth()`. Zero for exact scope match, positive for ancestor scopes.

Validation:
- `Exponential.base` must be in (0, 1]
- `Linear.maxDepth` must be ≥ 1
- `Step.beyondExact` must be in [0, 1]

### Strategy selection guidance

`Linear(maxDepth)` produces factor 0.0 when `depthDistance == maxDepth`. If `maxDepth` equals the actual tree depth, root-scope cases (cross-trial safety signals, regulatory defaults) get factor 0.0 and are eliminated. This is by design — linear decay reaches zero at the configured boundary. To preserve root visibility from deep queries:

- **Exponential** — asymptotically approaches zero but never reaches it. `Exponential(0.5)` at depth 3: factor = 0.125.
- **Step** — configurable floor for all ancestor scopes. `Step(0.3)` gives factor 0.3 for any non-exact match.
- **Linear with headroom** — set `maxDepth` greater than the expected tree depth. `Linear(5)` on a 3-level tree: root factor = 0.4.

## Store-Level Scope Filtering

Each store adds scope visibility to its retrieval alongside the existing `tenantId`/`domain`/`caseType` equality checks:

```java
static boolean isVisibleAtScope(Path storedScope, Path queryScope) {
    return storedScope.equals(queryScope) || storedScope.isAncestorOf(queryScope);
}
```

Root-scope cases are visible everywhere — `Path.root().isAncestorOf(anyNonRoot)` returns true per the platform `Path.isAncestorOf()` implementation.

### Per-store implementation notes

- **InMemoryCbrCaseMemoryStore** — one predicate added to the retrieval loop. `StoredCase` record gains a `Path scope` field.
- **QdrantCbrCaseMemoryStore** — scope stored as a keyword payload field (`scope` = `Path.value()`, e.g. `""` for root, `"trial-alpha/site-north"` for a site scope). At query time, `CbrQueryTranslator.toIdentityFilter()` enumerates all ancestor scope values from the query scope and uses `ConditionFactory.matchKeywords("scope", ancestorScopes)` — Qdrant's match-any filter. For query scope `trial-alpha/site-north/patient-42`, the filter matches scope IN `["", "trial-alpha", "trial-alpha/site-north", "trial-alpha/site-north/patient-42"]`. Ancestor enumeration is bounded by depth (typically ≤5) and must be constructed by iterating `Path.segments()` — not by walking `Path.parent()`, which returns `null` at depth 1 (not `Path.root()`), so parent-chain traversal misses root.
- **JpaCbrCaseMemoryStore** — scope stored as a VARCHAR column. SQL: `scope_value = '' OR scope_value = :queryScope OR :queryScope LIKE scope_value || '/%'`.

### JPA Migration

Flyway migration adds a `scope` VARCHAR column to `cbr_case` with default `''` (root scope). Existing rows default to root scope, preserving backward compatibility: root-scope queries continue to find all pre-existing cases. An index on `(tenant_id, domain, case_type, scope)` replaces the existing `(tenant_id, domain, case_type)` index.

### Qdrant Migration

Existing Qdrant points have no `scope` payload field. The `matchKeywords("scope", ancestorScopes)` filter evaluates to false on points with a missing field, so unpatched points silently vanish from results after deployment.

Migration is two parts:

1. **Index creation** — `CbrCollectionManager.BASE_KEYWORD_FIELDS` extended to include `"scope"`. New collections get the keyword index automatically. For existing collections, `ensureCollection` creates the index if absent.
2. **Backfill** — `ensureCollection` scrolls all points in the collection where `scope` is absent (Qdrant's `IsEmpty` condition on the `scope` field) and sets `scope = ""` via `setPayload`. This runs once per collection on first startup after upgrade; subsequent calls skip (no points match the `IsEmpty` filter). Bounded by collection size, not tree depth.

## ScopeDecay Decorator

`ScopeDecayCbrCaseMemoryStore` `@Decorator` `@Priority(85)` — between TrendEnrichment (90) and TemporalDecay (80).

1. Delegates to get results (store already filtered to visible scopes)
2. If `query.scopeDecay() == null` → returns results unchanged
3. For each result: `depthDistance = query.scope().depth() - scored.scope().depth()`
4. Applies `scored.withScore(scored.score() * scopeDecay.factor(depthDistance))`
5. Filters results below `minSimilarity`, re-sorts by score descending
6. Returns

Reactive parity: `ReactiveScopeDecayCbrCaseMemoryStore` `@Decorator` `@Priority(85)`.

### Full decorator chain

```
@Priority(90)  TrendEnrichment      — enrich features
@Priority(85)  ScopeDecay           — decay by scope distance
@Priority(80)  TemporalDecay        — decay by time distance
@Priority(75)  Reranking            — cross-encoder rerank
@Priority(65)  OutcomeWeighting     — modulate by confidence
@Priority(50)  Tracking             — record retrieval events
```

## Module Impact

| Module | What changes |
|---|---|
| **memory-api** | `CbrCaseMemoryStore.store()` + `ReactiveCbrCaseMemoryStore.store()` gain `Path scope`. `CbrQuery` gains `scope` + `scopeDecay`. `ScoredCbrCase` gains `scope`. New `ScopeDecay` sealed interface. |
| **memory/** | New `ScopeDecayCbrCaseMemoryStore` @Decorator @Priority(85) + reactive parity. All existing decorators update `store()` pass-through to forward `scope`. |
| **memory-cbr-inmem** | `InMemoryCbrCaseMemoryStore` — `StoredCase` gains `scope`, retrieval adds visibility predicate. |
| **memory-cbr-jpa** | `JpaCbrCaseMemoryStore` — new `scope` column, Flyway migration, scope-aware SQL. |
| **memory-qdrant** | `QdrantCbrCaseMemoryStore` — scope payload field, scope-aware filter construction. |
| **memory-testing** | `CbrCaseMemoryStoreContractTest` — new scope tests. |
| **memory-cbr-crossencoder** | Pass-through update for `store()` signature. |
| **memory-cbr-tracking** | Pass-through update for `store()` signature. |

## Contract Tests

Added to `CbrCaseMemoryStoreContractTest`:

1. **Cascade visibility** — store at root, mid, and leaf scope depths. Query from leaf → all three visible.
2. **No upward leakage** — store at child scope, query from parent → not returned.
3. **Root visibility** — store at root, query from any scope → visible.
4. **Branch isolation** — store at `trial-alpha/site-north`, query at `trial-beta/site-south` → not returned.
5. **Scope round-trip** — store with scope, retrieve → `ScoredCbrCase.scope()` matches.
6. **Root-scope query isolation** — store at `trial-alpha/site-north`, query from `Path.root()` → case NOT returned. Validates no-upward-leakage at the root boundary.

Decorator unit tests (`ScopeDecayCbrCaseMemoryStoreTest`):

7. **Null scopeDecay** — pass-through.
8. **Exponential decay** — exact=1.0, parent=0.5, grandparent=0.25.
9. **Linear decay** — scores decrease linearly.
10. **Step decay** — exact=1.0, ancestors=step value.
11. **Below minSimilarity after decay** — filtered out.
12. **Re-sort after decay** — order changes when decay inverts relative scores.

## Out of Scope

- **Scope-based bulk erasure** → #158
- **Aggregate adjustment on entity erasure** → #159 (reactive notification so domains can recompute aggregates when source cases are erased)
- **Scope-aware retention policy** → follow-on (natural extension of `CbrRetentionPolicy`)
- **Cross-scope aggregation SPI** → not needed (aggregates are regular cases at higher scopes)
