# CBR Trend Detection Layer — Design Spec

**Issue:** casehubio/neocortex#88
**Date:** 2026-07-14
**Status:** Approved

## Context

Issue #88 was filed before the temporal CBR infrastructure existed. Since then,
the trajectory comparison layer has shipped:

- `FeatureField.TimeSeries` / `DiscreteSequence` — temporal field types in schema
- `DtwSimilarity` — full DTW with Unconstrained / SakoeChibaBand / ItakuraParallelogram
  warping constraints, early abandonment via `abandonCostThreshold`
- `LbKeogh` — O(n) lower-bound pruning for SakoeChibaBand DTW
- `EditDistanceSimilarity` — weighted Levenshtein for DiscreteSequence
- `CbrSimilarityScorer` — dispatches DTW and edit distance for temporal fields
- `TemporalDecay` on `CbrQuery` — exponential recency scoring via HalfLife
- 23+ contract tests covering store, validate, score, round-trip

Of #88's four deliverables, two have shipped (temporal case representation and
trajectory similarity). This spec addresses the third: **trend detection
primitives** — slope estimation, change-point detection, and acceleration over
the observation window, exposed as derived Numeric features for CBR scoring.
The fourth deliverable (trajectory query extensions — time window, minimum
trajectory length, trend filters) remains as future work.

## Problem

DTW answers "Are these two shapes similar?" but clinicians ask:

- "How fast is this escalating?" → slope
- "When did it start getting worse?" → change-point
- "Is the escalation accelerating?" → acceleration
- "How unstable is this trajectory?" → volatility
- "Find me past cases that escalated at a similar rate" → trend as a queryable feature

These are characterizations of trajectories — derived numeric properties that
should participate in CBR scoring and filtering alongside the raw DTW shape
comparison.

## Design Decision: No TemporalCbrCase

The CbrCase type hierarchy discriminates by problem-solution reasoning paradigm:
TextualCbrCase (text matching), FeatureVectorCbrCase (feature scoring),
PlanCbrCase (plan adaptation). A TemporalCbrCase would discriminate by feature
content — a category error. Cases with temporal fields remain FeatureVectorCbrCase
(or PlanCbrCase) with TimeSeries fields alongside flat features.

## Architecture

Trend metrics are **derived Numeric features** computed from TimeSeries data.
Once derived, they slot into existing scoring + filtering infrastructure with
no new query types, filter types, or case types.

### Tier 1 — memory-api (pure Java, zero deps)

#### TrendType enum

```java
public enum TrendType {
    SLOPE,              // linear regression per scorable Numeric field
    DELTA,              // last - first per scorable Numeric field
    VOLATILITY,         // standard deviation per scorable Numeric field
    ACCELERATION,       // rate of change of slope per scorable Numeric field
    CHANGE_POINTS,      // CUSUM count per scorable Numeric field
    DURATION,           // observation window (per-TimeSeries)
    OBSERVATION_COUNT   // number of observations (per-TimeSeries)
}
```

Per-inner-field metrics (SLOPE, DELTA, VOLATILITY, ACCELERATION, CHANGE_POINTS)
produce one derived Numeric field per **non-timestamp** Numeric inner field in
the TimeSeries. The timestamp field is excluded — computing trends of the
timestamp against itself is meaningless (SLOPE is always 1.0 for uniform
spacing). This is the same scorable-field distinction used by
`DtwSimilarity.scorableNumericFields()`.

Per-TimeSeries metrics (DURATION, OBSERVATION_COUNT) produce one derived field
per TimeSeries.

#### TrendSpec record

```java
public record TrendSpec(Set<TrendType> types, ChronoUnit timeUnit) {
    public TrendSpec {
        Objects.requireNonNull(types);
        if (types.isEmpty())
            throw new IllegalArgumentException("At least one TrendType required");
        types = Set.copyOf(types);
        Objects.requireNonNull(timeUnit);
    }
    public TrendSpec(Set<TrendType> types) { this(types, ChronoUnit.HOURS); }
}
```

Attached to `FeatureField.TimeSeries` as a nullable field alongside the existing
nullable `SimilaritySpec`. Independent concerns: SimilaritySpec controls DTW
comparison; TrendSpec controls trend feature derivation.

The `timeUnit` field declares what unit the raw timestamp values are already
expressed in — no unit conversion is performed. If the TimeSeries timestamp
values are in minutes (0, 60, 120) and `timeUnit` is `ChronoUnit.MINUTES`, the
analyzer uses the raw values directly. The `timeUnit` serves two purposes:
(1) determining heuristic DURATION ranges for similarity normalization, and
(2) giving SLOPE values human-readable semantics (e.g., "change per hour"
vs. "change per day").

#### TimeSeries field extension

```java
record TimeSeries(String name, List<FeatureField> innerFields,
                  String timestampField, SimilaritySpec similaritySpec,
                  TrendSpec trendSpec) implements FeatureField
```

New factory method:
```java
static FeatureField timeSeries(String name, String timestampField,
    SimilaritySpec spec, TrendSpec trendSpec, FeatureField... innerFields)
```

Existing factory overloads pass `null` for trendSpec (backward compatible).

#### TrendFieldNaming utility

Deterministic derived field names using underscore separators (not dots, to
avoid Qdrant dot-notation path collisions):

- Per-inner-field: `{tsName}_{trendType}_{innerFieldName}`
  - Example: `vitals_slope_heart_rate`
- Per-TimeSeries: `{tsName}_{trendType}`
  - Example: `vitals_duration`

Only non-timestamp Numeric inner fields participate in per-inner-field naming.

#### Derived field heuristic ranges

When schema expansion produces derived Numeric fields, each gets a heuristic
range for similarity normalization:

| TrendType | Range | Rationale |
|---|---|---|
| SLOPE | `[-(max-min), +(max-min)]` | Full range traversal per timeUnit |
| DELTA | `[-(max-min), +(max-min)]` | Full range change first-to-last |
| VOLATILITY | `[0, (max-min)]` | Std dev bounded by range |
| ACCELERATION | `[-(max-min), +(max-min)]` | Same magnitude as slope |
| CHANGE_POINTS | `[0, 1000]` | Bounded by observation count |
| DURATION | `[0, durationMax(timeUnit)]` | Approximately one year in declared timeUnit |
| OBSERVATION_COUNT | `[0, 1000]` | Reasonable upper bound |

**SLOPE, DELTA, VOLATILITY, ACCELERATION** ranges derive from the inner Numeric
field's `[min, max]` range. These are timeUnit-independent because the formula
expresses the theoretical maximum change rate in whatever unit the timestamps
use.

**DURATION** range depends on `timeUnit` — `durationMax` returns approximately
one year in the declared unit:

| timeUnit | durationMax |
|----------|------------|
| SECONDS | 31,536,000 |
| MINUTES | 525,600 |
| HOURS | 8,760 |
| DAYS | 365 |

Formula for other units:
`Duration.ofDays(365).toSeconds() / timeUnit.getDuration().getSeconds()`.

**CHANGE_POINTS** range is aligned with OBSERVATION_COUNT (change-point count
is bounded by observation count). Values exceeding the range produce 0.0
similarity for that dimension via the existing `numericSimilarity` clamping
behavior: `Math.max(0.0, 1.0 - normalizedDistance)`.

Validation: name collision between declared and derived fields is rejected
during schema expansion.

#### TrendProfile record

```java
public record TrendProfile(Map<String, Double> metrics) {
    public TrendProfile { metrics = Map.copyOf(metrics); }
    public Map<String, FeatureValue> toFeatures() { /* each → FeatureValue.number() */ }
}
```

#### TrendAnalyzer utility

```java
public final class TrendAnalyzer {
    public static TrendProfile analyze(
        List<Map<String, FeatureValue>> observations, TimeSeries schema);

    public static Map<String, FeatureValue> enrichFeatures(
        Map<String, FeatureValue> features, CbrFeatureSchema schema);

    public static CbrFeatureSchema expandSchema(CbrFeatureSchema schema);
}
```

`analyze` — computes trend metrics from raw observations. Extracts timestamps,
uses raw values in the declared timeUnit (no conversion), computes each requested
TrendType across non-timestamp Numeric inner fields.

`enrichFeatures` — scans a features map for TimeSeries values with TrendSpec,
runs `analyze`, returns a **new map** containing the original features plus
derived Numeric values. The input map is not mutated. If no TimeSeries values
with TrendSpec are present, returns the input map unchanged.

`expandSchema` — returns a new `CbrFeatureSchema` with derived Numeric fields
appended for each TimeSeries field that has a non-null TrendSpec. The original
schema is not modified. `CbrFeatureSchema` remains a pure value record with no
expansion logic in its constructor. **Idempotent:** if derived fields for a
TimeSeries are already present in the schema (matching the naming pattern for
the given TrendSpec), they are skipped. Calling `expandSchema` on an
already-expanded schema returns an identical schema without collision errors.

**Observation ordering precondition:** all order-dependent metrics (DELTA,
DURATION, ACCELERATION) assume observations are in ascending timestamp order.
This is not validated by TrendAnalyzer — it is already enforced by
`CbrFeatureValidator.validateTimeSeries()`, which rejects non-ascending
timestamps at both store and query time. DTW processing relies on the same
ordering guarantee.

**Algorithms (all O(n)):**

- **SLOPE** — Least-squares linear regression. Single-pass running sums over
  `(timestamp, value)` pairs where timestamps are raw values in declared
  timeUnit. Returns 0.0 for ≤1 observation.
- **DELTA** — `values[last] - values[first]`. Returns 0.0 for ≤1 observation.
- **VOLATILITY** — Population standard deviation via Welford's algorithm
  for numerical stability. Returns 0.0 for <2 observations.
- **ACCELERATION** — Split observations into halves, compute slope of each,
  acceleration = (slope₂ - slope₁) / time between half midpoints.
  Returns 0.0 for <4 observations.
  **Limitation:** this is a two-point finite difference approximation. For
  multi-phase trajectories (acceleration → deceleration → acceleration), it
  collapses to a single average. For highly non-uniform timestamp spacing,
  the half midpoints may be biased. This approximation is intentional —
  it produces an interpretable metric ("the second half is steeper than the
  first") suitable for CBR similarity comparison. Multi-phase trajectory
  detail is better captured by CHANGE_POINTS. A more sophisticated
  acceleration algorithm (e.g., second-order polynomial fit) can be added
  as a future TrendType variant without architectural change.
- **CHANGE_POINTS** — CUSUM control chart. Running mean, cumulative positive
  and negative deviations, threshold at 1.5 × stddev. Count of detected
  change-points. Returns 0 for <3 observations.
- **DURATION** — Last timestamp minus first timestamp (raw values in declared
  timeUnit). Returns 0.0 for ≤1 observation.
- **OBSERVATION_COUNT** — `observations.size()`.

**Edge cases:**
- Empty observations → all metrics zero
- Non-numeric inner fields → skipped
- Missing field in an observation → observation skipped for that field

#### CbrCase.withFeatures

Add `withFeatures` to the `CbrCase` interface:

```java
default CbrCase withFeatures(Map<String, FeatureValue> features) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " does not support withFeatures");
}
```

Overridden in `FeatureVectorCbrCase` and `PlanCbrCase`:

```java
// FeatureVectorCbrCase
@Override
public CbrCase withFeatures(Map<String, FeatureValue> features) {
    return new FeatureVectorCbrCase(problem(), solution(), outcome(),
                                    confidence(), features);
}

// PlanCbrCase
@Override
public CbrCase withFeatures(Map<String, FeatureValue> features) {
    return new PlanCbrCase(problem(), solution(), outcome(),
                           confidence(), features, planTrace());
}
```

This follows the existing `withOutcome` copy-with-modification pattern. The
decorator uses it to construct enriched cases without instanceof dispatch.
The default throws for case types without features (e.g., `TextualCbrCase`)
— the decorator never reaches this path because TextualCbrCase cannot have
TimeSeries fields.

#### CbrQuery.withFeatures

Add `withFeatures` to `CbrQuery`:

```java
public CbrQuery withFeatures(Map<String, FeatureValue> features) {
    return new CbrQuery(tenantId, domain, caseType, features, filters, weights,
                        topK, minSimilarity, notBefore, problem, vectorWeight,
                        retrievalMode, fusionStrategy, temporalDecay);
}
```

Used by the decorator to enrich query features before delegating to the
underlying store.

### Tier 2 — memory module (CDI wiring)

#### TrendEnrichmentDecorator

`@Decorator @Priority(90)` on CbrCaseMemoryStore. Intercepts three methods:

1. **`registerSchema()`** — calls `TrendAnalyzer.expandSchema(schema)` and
   delegates with the expanded schema. Maintains an internal
   `ConcurrentHashMap<String, CbrFeatureSchema>` of caseType → expanded
   schema for use in store/query interception.

2. **`store()`** — looks up the expanded schema for the caseType. If schema
   has TimeSeries fields with TrendSpec, calls
   `TrendAnalyzer.enrichFeatures()` to compute derived metrics, then
   reconstructs the case via `cbrCase.withFeatures(enriched)`. Delegates
   with enriched case.

3. **`retrieveSimilar()`** — looks up the expanded schema for the query's
   caseType. If schema has TimeSeries fields with TrendSpec and the query
   features contain TimeSeries values, calls
   `TrendAnalyzer.enrichFeatures()` on the query features, then delegates
   with `query.withFeatures(enrichedFeatures)`. If the query features
   contain manually-set derived field values but no raw TimeSeries data,
   those values pass through unchanged — enrichment only processes
   TimeSeries values that are actually present.

If `store()` or `retrieveSimilar()` is called with a caseType that has no
registered schema, the decorator delegates without modification.

Priority 90 places enrichment early — before outcome weighting (65),
cross-encoder reranking (75), and tracking (50).

**Activation:** classpath-activated, not `@IfBuildProperty`-gated. This
departs from existing decorators (OutcomeWeightingCbrCaseMemoryStore uses
`@IfBuildProperty`), but the departure is intentional: TrendSpec on the
schema IS the declaration of intent. Config-gating would add a redundant
toggle — consumers already opt in by declaring TrendSpec, and the decorator
short-circuits for schemas without TrendSpec. No configuration required.

#### ReactiveTrendEnrichmentDecorator

Same logic, same priority. Wraps synchronous enrichment in `Uni.createFrom().item()`
(pure computation, no I/O).

### Backend impact

All backends benefit automatically — no code changes:

- **InMemory** — schema expansion adds Numeric fields handled by CbrSimilarityScorer.
  LbKeogh still applies to raw TimeSeries DTW.
- **Qdrant** — derived features stored in `_features_json` payload via existing
  serialization. Client-side scoring via CbrSimilarityScorer.
- **JPA** — JSON feature serialization handles additional Numeric values.

The enrichment decorator adds derived features before any store sees the case.

## Consumer API

**Schema declaration:**
```java
var schema = new CbrFeatureSchema("clinical-ae",
    FeatureField.categorical("drug"),
    FeatureField.numeric("age", 0, 120),
    FeatureField.timeSeries("vitals", "timestamp",
        new DtwSpec(WarpingConstraint.unconstrained()),
        new TrendSpec(Set.of(SLOPE, DELTA, CHANGE_POINTS), ChronoUnit.DAYS),
        FeatureField.numeric("heart_rate", 40, 200),
        FeatureField.numeric("bp_systolic", 60, 250)));
// Schema registered via store.registerSchema(schema)
// Decorator expands with vitals_slope_heart_rate, vitals_delta_heart_rate, etc.
```

**Store time (automatic via decorator):**
```java
store.store(new FeatureVectorCbrCase(problem, solution, null, null,
    Map.of("drug", FeatureValue.string("pembrolizumab"),
           "age", FeatureValue.number(67),
           "vitals", FeatureValue.structList(observations))),
    "clinical-ae", "patient-1", CBR, "tenant-1", null);
// Decorator enriches with vitals_slope_heart_rate: 15.0, etc.
```

**Query time (automatic via decorator):**
```java
var query = CbrQuery.of(tenant, CBR, "clinical-ae",
    Map.of("drug", FeatureValue.string("pembrolizumab"),
           "age", FeatureValue.number(65),
           "vitals", FeatureValue.structList(currentObservations)),
    10)
    .withWeight("vitals_slope_heart_rate", 3.0)   // prioritize escalation rate
    .withWeight("vitals", 2.0);                   // DTW shape matching
// Decorator enriches query features automatically before scoring
```

**Weight calibration:** DTW similarity and trend-derived Numeric similarity
both encode information from the same underlying TimeSeries observations.
This is intentional — DTW captures shape similarity while trend metrics
capture specific trajectory properties (rate, volatility, phase transitions).
The two are complementary, not redundant. When using both, weight calibration
may be needed: DTW scores are normalized differently (inverse of path cost)
from Numeric similarity (linear decay from range). Start with equal weights
and adjust empirically based on domain-specific relevance.

## Test plan

### TrendAnalyzer unit tests (memory-api)

- Each algorithm tested independently with known-answer values
- Edge cases: empty, single, two, four observations
- Welford's numerical stability for volatility
- CUSUM threshold sensitivity for change-points
- Timestamp field excluded from per-inner-field metrics
- `enrichFeatures` with multiple TimeSeries fields — returns new map, input unchanged
- `expandSchema` produces correct derived fields and ranges
- `expandSchema` rejects name collisions between declared and derived fields
- `expandSchema` is idempotent — double-expansion returns identical schema

### Contract tests (memory-testing, CbrCaseMemoryStoreContractTest)

Schema + validation:
- `trend_schemaWithTrendSpec_expandsDerivedFields`
- `trend_validation_trendFieldNameCollision_rejected`
- `trend_enrichment_storeWithTrendSpec_derivedFieldsPopulated`

Scoring integration:
- `trend_slope_similarSlopesScoreHigher`
- `trend_delta_weightsAffectRanking`
- `trend_mixedTemporalAndTrend_compositeScoringWorks`

Query enrichment:
- `trend_queryEnrichment_derivedFieldsAddedAutomatically`
- `trend_queryEnrichment_manualTrendFieldsPassThrough`

Edge cases:
- `trend_emptyObservations_trendFeaturesAreZero`
- `trend_singleObservation_slopeDeltaZero`
- `trend_noTrendSpec_noDerivedFields`

### Decorator tests (memory module)

- `trendEnrichment_registerSchema_expandsSchema`
- `trendEnrichment_storeEnrichesFeatures`
- `trendEnrichment_retrieveSimilar_enrichesQueryFeatures`
- `trendEnrichment_noTrendSpec_passesThrough`
- `trendEnrichment_retrievePassesThrough` (non-retrieveSimilar methods)
- Reactive parity tests

### CbrCase / CbrQuery tests (memory-api)

- `withFeatures_featureVectorCbrCase_returnsNewInstance`
- `withFeatures_planCbrCase_preservesPlanTrace`
- `withFeatures_textualCbrCase_throwsUnsupportedOperation`
- `cbrQuery_withFeatures_returnsNewQueryWithUpdatedFeatures`

## Scope boundary

**In scope:** TrendSpec, TrendType, TrendProfile, TrendFieldNaming, TrendAnalyzer
(analyze, enrichFeatures, expandSchema), CbrCase.withFeatures,
CbrQuery.withFeatures, TrendEnrichmentDecorator
(registerSchema/store/retrieveSimilar interception) + reactive parity,
contract tests, TrendAnalyzer unit tests.

**Exhaustive switch sites** (compiler will enforce — enumerated here for
implementation clarity):

| File | Method | Required change |
|------|--------|----------------|
| `FeatureField.TimeSeries` | constructor | Add `trendSpec` parameter; validate TrendSpec independently (non-null types, non-empty set) |
| `FeatureField` | `timeSeries()` factory | Add overload with `TrendSpec` parameter; existing overloads pass `null` |
| `CbrFeatureValidator` | `validateStoreFeatures()` | No change — derived fields are Numeric, handled by existing Numeric branch |
| `CbrFeatureValidator` | `validateQueryFeatures()` | No change — derived fields are Numeric (NumberVal or RangeVal) |
| `CbrSimilarityScorer` | `localSimilarity()` | No change — derived fields are Numeric, dispatched to existing `numericSimilarity` |
| `CbrCase` | interface | Add `withFeatures()` default method |
| `FeatureVectorCbrCase` | record | Add `withFeatures()` override |
| `PlanCbrCase` | record | Add `withFeatures()` override |
| `CbrQuery` | record | Add `withFeatures()` method |

**Out of scope (future work):**
- DiscreteSequence trend analysis (transition count, dominant label) — different
  problem (sequential pattern mining)
- Windowed trend computation (trends over last N hours only) — follow-up
- Custom CUSUM threshold configuration — extend TrendSpec later if needed
- Qdrant-side LbKeogh pruning for trend-enriched queries — optimization
- Trajectory query extensions (#88's fourth deliverable) — time window, minimum
  trajectory length, trend filters on CbrQuery
- More sophisticated ACCELERATION algorithm (polynomial fit) — future TrendType
  variant if the half-split approximation proves insufficient
