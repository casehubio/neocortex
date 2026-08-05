# io.casehub.neocortex.memory.cbr.CbrSimilarityScorer

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `class`

Computes CBR similarity scores using per-field local similarity functions
and configurable per-field weights.

<p>Local similarity functions use three-level precedence:
<ol>
  <li>Caller-provided override via `Map<String, LocalSimilarityFunction>`</li>
  <li>Field-attached `SimilaritySpec` (if present)</li>
  <li>Type default (see below)</li>
</ol>

<p>Type defaults:
<ul>
  <li>`FeatureField.Categorical` — exact match (1.0 or 0.0)</li>
  <li>`FeatureField.Numeric` — linear decay: `1.0 - |query - case| / range`</li>
  <li>`FeatureField.Text` — exact match (1.0 or 0.0)</li>
  <li>`FeatureField.CategoricalList` — Jaccard similarity on string sets</li>
  <li>`FeatureField.NumericList` — average nearest-neighbor with linear decay</li>
  <li>`FeatureField.NestedObject` — recursive scoring with uniform weights</li>
  <li>`FeatureField.ObjectList` — greedy best-match with recursive inner scoring</li>
</ul>

<p>Pure Java, Tier 1 — zero external dependencies.

## Constructors

### `private CbrSimilarityScorer()`

## Methods

### `private static double categoricalListSimilarity(io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal)`

#### Parameters

- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

### `private static double categoricalSimilarity(io.casehub.neocortex.memory.cbr.FeatureField.Categorical field, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal)`

#### Parameters

- `field` (`io.casehub.neocortex.memory.cbr.FeatureField.Categorical`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

### `private static double computeNormalizedDistance(io.casehub.neocortex.memory.cbr.FeatureField.Numeric field, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal)`

#### Parameters

- `field` (`io.casehub.neocortex.memory.cbr.FeatureField.Numeric`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

### `private static double dtwSimilarity(io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries ts, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal, double abandonCostThreshold)`

#### Parameters

- `ts` (`io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `abandonCostThreshold` (`double`)

### `private static double editDistanceSimilarity(io.casehub.neocortex.memory.cbr.FeatureField.DiscreteSequence ds, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal)`

#### Parameters

- `ds` (`io.casehub.neocortex.memory.cbr.FeatureField.DiscreteSequence`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

### `private static io.casehub.neocortex.memory.cbr.FeatureField findField(io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema, java.lang.String name)`

#### Parameters

- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)
- `name` (`java.lang.String`)

### `private static double localSimilarity(io.casehub.neocortex.memory.cbr.FeatureField field, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction> overrides)`

#### Parameters

- `field` (`io.casehub.neocortex.memory.cbr.FeatureField`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `overrides` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction>`)

### `private static double localSimilarity(io.casehub.neocortex.memory.cbr.FeatureField field, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction> overrides, double dtwAbandonCostThreshold)`

#### Parameters

- `field` (`io.casehub.neocortex.memory.cbr.FeatureField`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `overrides` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction>`)
- `dtwAbandonCostThreshold` (`double`)

### `private static double nestedObjectSimilarity(io.casehub.neocortex.memory.cbr.FeatureField.NestedObject no, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal)`

#### Parameters

- `no` (`io.casehub.neocortex.memory.cbr.FeatureField.NestedObject`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

### `private static double numericListSimilarity(io.casehub.neocortex.memory.cbr.FeatureField.NumericList nl, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal)`

#### Parameters

- `nl` (`io.casehub.neocortex.memory.cbr.FeatureField.NumericList`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

### `private static double numericSimilarity(io.casehub.neocortex.memory.cbr.FeatureField.Numeric field, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal)`

#### Parameters

- `field` (`io.casehub.neocortex.memory.cbr.FeatureField.Numeric`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

### `private static double objectListSimilarity(io.casehub.neocortex.memory.cbr.FeatureField.ObjectList ol, io.casehub.neocortex.memory.cbr.FeatureValue queryVal, io.casehub.neocortex.memory.cbr.FeatureValue caseVal)`

#### Parameters

- `ol` (`io.casehub.neocortex.memory.cbr.FeatureField.ObjectList`)
- `queryVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `caseVal` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

### `public static double score(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> queryFeatures, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> caseFeatures, java.util.Map<java.lang.String,java.lang.Double> weights, io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema)`

#### Parameters

- `queryFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `caseFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `weights` (`java.util.Map<java.lang.String,java.lang.Double>`)
- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)

### `public static double score(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> queryFeatures, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> caseFeatures, java.util.Map<java.lang.String,java.lang.Double> weights, io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction> overrides)`

#### Parameters

- `queryFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `caseFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `weights` (`java.util.Map<java.lang.String,java.lang.Double>`)
- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)
- `overrides` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction>`)

### `public static io.casehub.neocortex.memory.cbr.CbrSimilarityScorer.SimilarityBreakdown scoreDetailed(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> queryFeatures, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> caseFeatures, java.util.Map<java.lang.String,java.lang.Double> weights, io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction> overrides)`

#### Parameters

- `queryFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `caseFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `weights` (`java.util.Map<java.lang.String,java.lang.Double>`)
- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)
- `overrides` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction>`)

### `public static io.casehub.neocortex.memory.cbr.CbrSimilarityScorer.SimilarityBreakdown scoreDetailed(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> queryFeatures, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> caseFeatures, java.util.Map<java.lang.String,java.lang.Double> weights, io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction> overrides, double dtwAbandonCostThreshold)`

#### Parameters

- `queryFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `caseFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `weights` (`java.util.Map<java.lang.String,java.lang.Double>`)
- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)
- `overrides` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.LocalSimilarityFunction>`)
- `dtwAbandonCostThreshold` (`double`)

### `private static double scoreInnerFields(java.util.List<io.casehub.neocortex.memory.cbr.FeatureField> innerFields, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> queryObj, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> caseObj)`

#### Parameters

- `innerFields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField>`)
- `queryObj` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `caseObj` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
