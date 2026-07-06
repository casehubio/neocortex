# Semantic Text Field Similarity in CbrSimilarityScorer

**Issue:** casehubio/neocortex#106
**Date:** 2026-07-06
**Status:** Approved

## Problem

`CbrSimilarityScorer.localSimilarity()` hardcodes similarity functions per `FeatureField` type. `FeatureField.Text` fields use exact match (1.0/0.0), which is inadequate for longer text values where semantic similarity matters.

The root cause is deeper: similarity functions aren't pluggable. #106 (text embeddings), #107 (categorical similarity tables), and #108 (per-field configuration) are all symptoms. This design solves #106 and establishes the override mechanism that #107 and #108 will use.

## Design

### Approach: Per-field override map on `score()`

Add a `Map<String, LocalSimilarityFunction>` parameter to `CbrSimilarityScorer.score()`. The scorer checks overrides by field name first, falls back to the existing type-based default. Store implementations build the map at query time based on available capabilities (e.g., `EmbeddingModel` presence).

Rejected alternatives:
- **Type-level strategy on `CbrFeatureSchema`** — turns the schema from a data declaration into a behavior carrier. Doesn't support per-field overrides without additional complexity.
- **CDI-managed scorer** — pulls CDI into `memory-api`, violating Tier 1 purity.

### 1. `LocalSimilarityFunction` — functional interface in `memory-api`

```java
@FunctionalInterface
public interface LocalSimilarityFunction {
    double compute(Object queryValue, Object caseValue);

    LocalSimilarityFunction EXACT_MATCH = (q, c) -> q.equals(c) ? 1.0 : 0.0;
}
```

Contract: returns similarity in [0, 1]. Pure Java, Tier 1 clean. Serves as the extension point for all three issues (#106, #107, #108).

### 2. `FeatureField.Text` — opt-in via `semantic` flag

```java
record Text(String name, boolean semantic) implements FeatureField {
    public Text { Objects.requireNonNull(name, "name"); }
    public Text(String name) { this(name, false); }
}
```

- `FeatureField.text("label")` — `semantic=false` (default), exact match.
- `FeatureField.semanticText("description")` — `semantic=true`, auto-upgrades to embedding similarity when `EmbeddingModel` is available.
- Existing callers via `FeatureField.text("name")` are unchanged — they remain exact match.

Factory methods:
```java
static FeatureField text(String name) { return new Text(name); }
static FeatureField semanticText(String name) { return new Text(name, true); }
```

### 3. `CbrSimilarityScorer` — new 5-arg `score()` overload

```java
public static double score(Map<String, Object> queryFeatures,
                           Map<String, Object> caseFeatures,
                           Map<String, Double> weights,
                           CbrFeatureSchema schema,
                           Map<String, LocalSimilarityFunction> overrides)
```

Existing 4-arg signature becomes a convenience method delegating with `Map.of()`.

The `localSimilarity` dispatch:

```java
private static double localSimilarity(FeatureField field, Object queryVal, Object caseVal,
                                       Map<String, LocalSimilarityFunction> overrides) {
    LocalSimilarityFunction override = overrides.get(field.name());
    if (override != null) return override.compute(queryVal, caseVal);

    if (field instanceof FeatureField.Numeric n) {
        return numericSimilarity(n, queryVal, caseVal);
    }
    return queryVal.equals(caseVal) ? 1.0 : 0.0;
}
```

### 4. `EmbeddingTextSimilarity` — implementation in `memory-cbr-embedding`

```java
public class EmbeddingTextSimilarity implements LocalSimilarityFunction {
    private final EmbeddingModel model;
    private final Map<String, Embedding> cache = new HashMap<>();

    public EmbeddingTextSimilarity(EmbeddingModel model) {
        this.model = Objects.requireNonNull(model);
    }

    public void precompute(List<String> texts) {
        List<TextSegment> uncached = texts.stream()
            .filter(t -> !cache.containsKey(t))
            .distinct()
            .map(TextSegment::from).toList();
        if (!uncached.isEmpty()) {
            List<Embedding> embeddings = model.embedAll(uncached).content();
            for (int i = 0; i < uncached.size(); i++) {
                cache.put(uncached.get(i).text(), embeddings.get(i));
            }
        }
    }

    @Override
    public double compute(Object queryValue, Object caseValue) {
        Embedding queryEmb = embed((String) queryValue);
        Embedding caseEmb = embed((String) caseValue);
        return Math.max(0.0, CosineSimilarity.between(queryEmb, caseEmb));
    }

    private Embedding embed(String text) {
        return cache.computeIfAbsent(text, t -> model.embed(TextSegment.from(t)).content());
    }
}
```

- Lives in new `memory-cbr-embedding` module — depends on `memory-api` + `langchain4j-core`. Zero Qdrant dependencies. Any store implementation can reuse it.
- `precompute(List<String>)` batch-embeds all texts in a single `embedAll()` call. The store calls this with all candidate text values before the scoring loop. Individual `compute()` calls hit the warm cache.
- `compute()` still works without `precompute()` (lazy embedding via `computeIfAbsent`), but batch precomputation is the expected usage for performance.
- Single instance shared across all semantic Text fields in one query.
- Clamps negative cosine to 0.0 — consistent with the scorer's [0, 1] range.
- Not CDI-managed — created per query by the store, discarded after.
- Failure behavior: embedding failures propagate as exceptions, consistent with existing `problem()` embedding in `executeDenseSearch()`. No silent fallback — fail-fast ensures callers know when the embedding model is unavailable. Batch precomputation via `precompute()` reduces the failure surface from N×K sequential calls to 1-2 batch calls.

### 5. Store wiring

**QdrantCbrCaseMemoryStore** — builds overrides when EmbeddingModel is present.

`buildTextOverrides()` takes an externally-created `textSim` instance so the caller retains a reference for precomputation:

```java
private Map<String, LocalSimilarityFunction> buildTextOverrides(
        CbrFeatureSchema schema, EmbeddingTextSimilarity textSim) {
    if (textSim == null) return Map.of();
    Map<String, LocalSimilarityFunction> overrides = new HashMap<>();
    for (FeatureField field : schema.fields()) {
        if (field instanceof FeatureField.Text t && t.semantic()) {
            overrides.put(field.name(), textSim);
        }
    }
    return overrides.isEmpty() ? Map.of() : Collections.unmodifiableMap(overrides);
}
```

`retrieveSimilar()` restructured to a two-pass flow — reconstruct all candidates first, then batch-precompute, then score:

```java
CbrFeatureSchema schema = schemas.get(query.caseType());
if (schema != null) {
    CbrQueryTranslator.validateQueryFeatures(query.features(), schema);
}

// 1. Build overrides (textSim reference retained for precompute)
EmbeddingTextSimilarity textSim = (schema != null && embeddingModel != null)
    ? new EmbeddingTextSimilarity(embeddingModel) : null;
Map<String, LocalSimilarityFunction> overrides = schema != null
    ? buildTextOverrides(schema, textSim)
    : Map.of();

// ... dense search / filter query ...

// 2. Reconstruct all candidates (pass 1)
List<ReconstructedCandidate<C>> reconstructed = new ArrayList<>();
for (ScoredPoint point : scoredPoints) {
    C cbrCase = (C) reconstructCase(point.getPayloadMap(), caseClass);
    if (cbrCase != null) reconstructed.add(new ReconstructedCandidate<>(cbrCase, point.getScore()));
}

// 3. Batch precompute embeddings for all semantic text values
if (textSim != null && !overrides.isEmpty()) {
    List<String> texts = collectSemanticTextValues(query, reconstructed, overrides.keySet());
    textSim.precompute(texts);
}

// 4. Score and filter (pass 2 — compute() hits warm cache)
for (var rc : reconstructed) {
    double featureScore = CbrSimilarityScorer.score(
        query.features(), rc.cbrCase().features(), query.weights(), schema, overrides);
    // ... compositeScore, minSimilarity filter, collect ...
}
```

`collectSemanticTextValues()` uses `instanceof String s` for null-safe collection — `null instanceof String` is `false`, so missing feature values are naturally excluded without explicit null checks:

```java
private List<String> collectSemanticTextValues(
        CbrQuery query, List<ReconstructedCandidate<?>> candidates, Set<String> fieldNames) {
    List<String> texts = new ArrayList<>();
    for (String fieldName : fieldNames) {
        if (query.features().get(fieldName) instanceof String s) texts.add(s);
        for (var rc : candidates) {
            if (rc.cbrCase().features().get(fieldName) instanceof String s) texts.add(s);
        }
    }
    return texts;
}
```

When `schema` is null, the 5-arg `score()` with empty overrides delegates to the 4-arg behavior (returns 1.0).

**InMemoryCbrCaseMemoryStore** — same two-pass flow with empty overrides by default:

```java
// InMemory has no EmbeddingModel, so textSim is always null
Map<String, LocalSimilarityFunction> overrides = schema != null
    ? overrideBuilder.apply(schema)  // defaults to schema -> Map.of()
    : Map.of();
double featureScore = CbrSimilarityScorer.score(
    query.features(), stored.cbrCase().features(), query.weights(), schema, overrides);
```

The InMemory store uses the same scoring code path as Qdrant. Without an `EmbeddingModel`, overrides are empty and Text fields use exact match. A testing constructor accepts a `Function<CbrFeatureSchema, Map<String, LocalSimilarityFunction>>` override builder, allowing unit tests to verify override behavior without requiring langchain4j dependencies. The InMemory store does not need a two-pass loop — it iterates its in-memory list and scores directly, since `precompute()` is only needed when embedding calls are expensive (Qdrant reconstructing from payloads).

## Testing

1. **`CbrSimilarityScorerTest`** (memory-api) — override mechanism with lambdas:
   - Override replaces default text behavior
   - Override for one field, default for others
   - Empty overrides preserves existing behavior

2. **`EmbeddingTextSimilarityTest`** (memory-cbr-embedding) — unit test with deterministic stub `EmbeddingModel`:
   - Identical texts → similarity 1.0
   - Different texts → similarity < 1.0
   - Query embedding cached across calls
   - Negative cosine clamped to 0.0
   - `precompute()` batch-embeds uncached texts in single `embedAll()` call
   - `compute()` hits warm cache after `precompute()`

3. **`CbrCaseMemoryStoreContractTest`** (memory-testing) — extended with Text field:
   - Schema includes `text("description")` alongside existing Categorical and Numeric fields
   - Text exact match: identical strings → 1.0, different strings → 0.0
   - Text field scoring consistent across store implementations

4. **`QdrantCbrCaseMemoryStoreTest`** (memory-qdrant) — integration test with Testcontainers Qdrant + stub EmbeddingModel verifying end-to-end semantic text ranking.

## Module changes

| Module | Changes |
|--------|---------|
| `memory-api` | New `LocalSimilarityFunction`. `FeatureField.Text` gains `semantic` flag. New `semanticText()` factory method. `CbrSimilarityScorer` gains 5-arg `score()`. |
| `memory-cbr-embedding` | **New module.** `EmbeddingTextSimilarity` with batch `precompute()`. Depends on `memory-api` + `langchain4j-core`. Zero Qdrant dependencies. |
| `memory-qdrant` | `QdrantCbrCaseMemoryStore.retrieveSimilar()` builds overrides via `buildTextOverrides()`. Depends on `memory-cbr-embedding`. |
| `memory-cbr-inmem` | Uses 5-arg `score()` with `Map.of()` overrides. Testing constructor accepts override builder function. |
| `memory-testing` | Contract test extended with `text("description")` field. |

## Performance

Per-query cost for N candidates with K semantic Text fields:
- K embed calls for query text (cached → effectively 1 per unique value)
- N × K embed calls for case text values — batch-precomputed via `embedAll()` in 1-2 calls
- Typical N=50, K=1: ~52 unique texts, batch-embedded in 1-2 `embedAll()` calls
- ONNX local: ~5-15ms total for batch embedding
- Remote models (OpenAI, Cohere): ~200-400ms for 1-2 batch API calls (vs ~5s for 50 sequential calls without batch)

Batch precomputation via `precompute()` is the expected usage. Sequential `compute()` without precompute works (lazy embedding via `computeIfAbsent`) but is not recommended for remote models.

## Future extensibility

- **#107 (categorical similarity tables):** `LocalSimilarityFunction` backed by a lookup table. Same overrides map.
- **#108 (per-field configuration):** Configuration layer populating the overrides map. No mechanism changes.
