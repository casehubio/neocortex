# CBR Enhancements — Design Spec

**Date:** 2026-07-12
**Issues:** #125, #126, #127, #138, #139
**Branch:** issue-125-cbr-enhancements

## Overview

Five small CBR enhancements delivered on a single branch. Two refine temporal similarity (#138, #139), three extend query/filter capabilities (#125, #126, #127). All changes root in `memory-api` with implementations flowing through `memory-cbr-inmem`, `memory-qdrant`, and `memory-testing`.

---

## 1. Itakura Parallelogram Constraint (#138)

### Problem

`DtwSpec(Integer windowSize)` bakes Sakoe-Chiba directly into the spec. Adding Itakura as another parameter creates mutually exclusive fields.

### Design

New top-level sealed interface `WarpingConstraint` (follows existing pattern of `AlignmentPair`, `DtwResult`, `EditStep`):

```java
public sealed interface WarpingConstraint {
    record Unconstrained() implements WarpingConstraint {}
    record SakoeChibaBand(int windowSize) implements WarpingConstraint {
        // windowSize >= 1
    }
    record ItakuraParallelogram(double maxSlope) implements WarpingConstraint {
        // maxSlope > 1.0 — constrains path slope to [1/maxSlope, maxSlope]
    }
}
```

`DtwSpec` changes from `DtwSpec(Integer windowSize)` to `DtwSpec(WarpingConstraint constraint)`. Non-null — `Unconstrained()` replaces the previous null/absent semantics. **Breaking change** — all callers migrate.

### Implementation

`DtwSimilarity.compute()` dispatches on constraint type to compute `jStart`/`jEnd` per row:

- **Unconstrained** → `w = max(n, m)`, existing logic
- **SakoeChibaBand** → existing Sakoe-Chiba: `w = max(windowSize, |n-m|)`
- **ItakuraParallelogram** → parallelogram bounds at row `i`:
  ```
  jStart = max(1, max(ceil(i / maxSlope), ceil(m - maxSlope * (n - i))))
  jEnd   = min(m, min(floor(maxSlope * i), floor(m - (n - i) / maxSlope)))
  ```

The parallelogram constrains path slope rather than absolute deviation — narrows at corners, widens in the middle (opposite of Sakoe-Chiba).

**Itakura infeasibility:** Inside the DP loop, after computing `jStart` and `jEnd` for each row, check `if (jStart > jEnd) return new DtwResult(0.0, List.of())`. This catches all infeasibility cases — including discrete ceil/floor rounding edge cases where the continuous-domain length ratio passes but individual rows become empty (e.g., n=4, m=3, maxSlope=1.5: ratio 1.33 ≤ 1.5 but row 2 has jStart=2 > jEnd=1). Short-circuits at the first empty row. Unlike Sakoe-Chiba — which guarantees endpoint reachability via `w = max(windowSize, |n-m|)` — the Itakura parallelogram has no such self-correction; infeasibility is an inherent property of slope-bounded warping on length-mismatched sequences.

### Files Changed

| File | Change |
|------|--------|
| `memory-api` SimilaritySpec.java | DtwSpec(Integer) → DtwSpec(WarpingConstraint) |
| `memory-api` WarpingConstraint.java | New file — sealed interface |
| `memory-api` DtwSimilarity.java | Dispatch on constraint type for jStart/jEnd |
| `memory-api` FeatureField.java | Update DtwSpec validation switch arms (unchanged logic) |
| `memory-api` CbrSimilarityScorer.java | Extract constraint from DtwSpec, pass to compute() |
| `memory-api` tests | WarpingConstraint validation, Itakura DTW tests |
| `memory-testing` contract tests | If DTW-related tests exist, update DtwSpec construction |

---

## 2. Configurable Insert/Delete Costs (#139)

### Problem

EditDistanceSimilarity hardcodes insert and delete costs at 1.0. Domain-specific CBR may need asymmetric costs (e.g., inserting an aggressive phase costlier than deleting a defensive one).

### Design

`EditDistanceSpec` changes from `(Map substitutionSimilarities)` to `(Map substitutionSimilarities, Double insertCost, Double deleteCost)`. Both nullable, defaulting to 1.0. **Breaking change** to EditDistanceSpec constructor.

Validation: costs must be > 0 when provided.

### Normalization

The DP uses configured costs. Normalization computes the correct maximum possible edit distance for variable costs:

```java
double effDel = deleteCost != null ? deleteCost : 1.0;
double effIns = insertCost != null ? insertCost : 1.0;
double maxDist;
if (1.0 <= effDel + effIns) {
    // Prefer substitution: match min(n,m) + handle remainder
    maxDist = Math.min(n, m) + Math.max(0, n - m) * effDel + Math.max(0, m - n) * effIns;
} else {
    // Prefer delete+insert: nuke and rebuild
    maxDist = n * effDel + m * effIns;
}
double score = 1.0 - editDistance / maxDist;
```

With unit costs: `min(n,m) + |n-m| = max(n,m)` — backward compatible.

### Files Changed

| File | Change |
|------|--------|
| `memory-api` SimilaritySpec.java | EditDistanceSpec gains insertCost, deleteCost |
| `memory-api` EditDistanceSimilarity.java | Variable costs in DP + normalization fix |
| `memory-api` CbrSimilarityScorer.java | Pass costs from spec to compute() |
| `memory-api` tests | Variable cost tests, normalization edge cases |

---

## 3. NumericList Field Type (#125)

### Problem

Symmetry gap: `CategoricalList` exists for `List<String>` but no equivalent for `List<Number>`.

### Design

New FeatureField variant:

```java
record NumericList(String name, double min, double max) implements FeatureField {
    // min/max for per-element validation
}
```

**Filter-only** — consistent with CategoricalList pattern:
- Skipped by `CbrSimilarityScorer`
- Rejected by `validateQueryFeatures`
- Stored as `List<Number>`, each element validated within [min, max]

New CbrFilter variant for numeric list querying:

```java
record ContainsRange(NumericRange range) implements CbrFilter {
    // Matches if ANY element in the list falls within the range
}
```

`ContainsRange` valid only on `NumericList` fields.

### Qdrant Mapping

- Payload: stored as array of doubles via `toListValue()`
- Index: float payload index (like Numeric but on array field)
- Filter: Qdrant range condition on array field — matches if any element satisfies

### In-Memory

```java
case CbrFilter.ContainsRange cr ->
    storedValue instanceof List<?> list && list.stream()
        .filter(Number.class::isInstance)
        .map(Number.class::cast)
        .anyMatch(n -> n.doubleValue() >= cr.range().min()
                    && n.doubleValue() <= cr.range().max());
```

### Files Changed

| File | Change |
|------|--------|
| `memory-api` FeatureField.java | Add NumericList variant + factory method + validateFlatFields rejection |
| `memory-api` CbrFilter.java | Add ContainsRange variant + factory method |
| `memory-api` CbrFeatureValidator.java | Store/query/filter validation for NumericList + ContainsRange. New `requireNumericList()` helper (parallel to `requireCategoricalList()`) validates field is NumericList |
| `memory-api` CbrSimilarityScorer.java | Skip NumericList (like CategoricalList) |
| `memory-cbr-inmem` InMemoryCbrCaseMemoryStore.java | Filter evaluation for ContainsRange |
| `memory-qdrant` CbrQueryTranslator.java | `toFilter()`: add NumericList case (throws — filter-only field). `applyStructuralFilters()`: ContainsRange → range condition on array field |
| `memory-qdrant` CbrCollectionManager.java | `registerSchemaIndexes()`: float payload index for NumericList fields |
| `memory-qdrant` QdrantCbrCaseMemoryStore.java | `buildTextOverrides()`: add NumericList case (empty handler — no text semantics) |
| `memory-testing` contract tests | NumericList store/retrieve/filter tests |

---

## 4. NotContains / NotContainsAny (#126)

### Problem

No negation filters for CategoricalList fields.

### Design

```java
record NotContains(String value) implements CbrFilter {
    // CategoricalList does NOT contain this value
}
record NotContainsAny(List<String> values) implements CbrFilter {
    // CategoricalList contains NONE of these values
}
```

Validator: same `requireCategoricalList` check as Contains/ContainsAll/ContainsAny.

### In-Memory

```java
case CbrFilter.NotContains nc ->
    storedValue instanceof List<?> list && !list.contains(nc.value());
case CbrFilter.NotContainsAny nca ->
    storedValue instanceof List<?> list && nca.values().stream().noneMatch(list::contains);
```

### Qdrant Mapping

```java
case CbrFilter.NotContains nc ->
    builder.addMustNot(ConditionFactory.matchKeyword(payloadKey, nc.value()));
case CbrFilter.NotContainsAny nca ->
    nca.values().forEach(v ->
        builder.addMustNot(ConditionFactory.matchKeyword(payloadKey, v)));
```

### Files Changed

| File | Change |
|------|--------|
| `memory-api` CbrFilter.java | Add NotContains, NotContainsAny + factory methods |
| `memory-api` CbrFeatureValidator.java | Extend requireCategoricalList dispatch |
| `memory-cbr-inmem` InMemoryCbrCaseMemoryStore.java | Filter evaluation |
| `memory-qdrant` CbrQueryTranslator.java | must_not conditions |
| `memory-testing` contract tests | Negation filter tests |

---

## 5. Compound Same-Field Filters — AllOf (#127)

### Problem

`Map<String, CbrFilter>` allows one filter per field. Compound conditions on the same field (e.g., two HasMatch predicates on one ObjectList) require a wrapper.

### Design

```java
record AllOf(List<CbrFilter> filters) implements CbrFilter {
    // Requires >= 2 inner filters
    // No nested AllOf (rejected at construction)
}
```

Validator: each inner filter validated individually against the field type via existing `validateFilters` dispatch. Recursive — walks into AllOf and validates each element.

### In-Memory

All inner filters must match — loop and short-circuit on first failure.

### Qdrant Mapping

Each inner filter is dispatched through the standard per-filter-type translation, preserving polarity — positive filters (Contains, ContainsAll, ContainsAny, ContainsRange, HasMatch) route to `must`, negation filters (NotContains, NotContainsAny) route to `must_not`. AllOf is a conjunction wrapper, not a polarity override. Requires extracting the per-filter dispatch in `applyStructuralFilters()` into a helper method so AllOf can delegate recursively (depth exactly 1 since nested AllOf is rejected at construction).

### Files Changed

| File | Change |
|------|--------|
| `memory-api` CbrFilter.java | Add AllOf + factory method |
| `memory-api` CbrFeatureValidator.java | Recursive validation into AllOf |
| `memory-cbr-inmem` InMemoryCbrCaseMemoryStore.java | All-match evaluation |
| `memory-qdrant` CbrQueryTranslator.java | Multi-condition translation |
| `memory-testing` contract tests | AllOf tests with various inner filter combos |

---

## Implementation Order

1. **#138 + #139** — temporal similarity (independent, shared test infrastructure)
2. **#126** — NotContains/NotContainsAny (simple additions)
3. **#125** — NumericList + ContainsRange (new field type + new filter type)
4. **#127** — AllOf (wraps all existing filters including new ones from #125/#126)

#127 last because it wraps other filter types — implementing it after #125/#126 means it can immediately compose with the new variants.
