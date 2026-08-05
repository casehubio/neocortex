# io.casehub.neocortex.memory.cbr.DtwSimilarity

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `class`

## Constructors

### `private DtwSimilarity()`

## Methods

### `private static java.util.List<io.casehub.neocortex.memory.cbr.AlignmentPair> backtrace(double[][] cost, int n, int m, io.casehub.neocortex.memory.cbr.WarpingConstraint constraint)`

#### Parameters

- `cost` (`double[][]`)
- `n` (`int`)
- `m` (`int`)
- `constraint` (`io.casehub.neocortex.memory.cbr.WarpingConstraint`)

### `public static io.casehub.neocortex.memory.cbr.DtwResult compute(java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> query, java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> caseSeq, io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries schema)`

#### Parameters

- `query` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `caseSeq` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `schema` (`io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries`)

### `public static io.casehub.neocortex.memory.cbr.DtwResult compute(java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> query, java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> caseSeq, io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries schema, io.casehub.neocortex.memory.cbr.WarpingConstraint constraint)`

#### Parameters

- `query` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `caseSeq` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `schema` (`io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries`)
- `constraint` (`io.casehub.neocortex.memory.cbr.WarpingConstraint`)

### `public static io.casehub.neocortex.memory.cbr.DtwResult compute(java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> query, java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> caseSeq, io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries schema, io.casehub.neocortex.memory.cbr.WarpingConstraint constraint, double abandonCostThreshold)`

#### Parameters

- `query` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `caseSeq` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `schema` (`io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries`)
- `constraint` (`io.casehub.neocortex.memory.cbr.WarpingConstraint`)
- `abandonCostThreshold` (`double`)

### `private static int computeJEnd(int i, int n, int m, io.casehub.neocortex.memory.cbr.WarpingConstraint constraint)`

#### Parameters

- `i` (`int`)
- `n` (`int`)
- `m` (`int`)
- `constraint` (`io.casehub.neocortex.memory.cbr.WarpingConstraint`)

### `private static int computeJStart(int i, int n, int m, io.casehub.neocortex.memory.cbr.WarpingConstraint constraint)`

#### Parameters

- `i` (`int`)
- `n` (`int`)
- `m` (`int`)
- `constraint` (`io.casehub.neocortex.memory.cbr.WarpingConstraint`)

### `private static double observationDistance(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> a, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> b, java.util.List<io.casehub.neocortex.memory.cbr.FeatureField.Numeric> fields)`

#### Parameters

- `a` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `b` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `fields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField.Numeric>`)

### `static java.util.List<io.casehub.neocortex.memory.cbr.FeatureField.Numeric> scorableNumericFields(io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries schema)`

#### Parameters

- `schema` (`io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries`)
