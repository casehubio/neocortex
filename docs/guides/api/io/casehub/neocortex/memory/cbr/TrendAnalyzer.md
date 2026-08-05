# io.casehub.neocortex.memory.cbr.TrendAnalyzer

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `class`

## Constructors

### `private TrendAnalyzer()`

## Methods

### `static double acceleration(double[] timestamps, double[] values)`

#### Parameters

- `timestamps` (`double[]`)
- `values` (`double[]`)

### `public static io.casehub.neocortex.memory.cbr.TrendProfile analyze(java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> observations, io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries schema)`

#### Parameters

- `observations` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `schema` (`io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries`)

### `static int changePoints(double[] values)`

#### Parameters

- `values` (`double[]`)

### `private static double computePerField(io.casehub.neocortex.memory.cbr.TrendType type, double[] timestamps, double[] values)`

#### Parameters

- `type` (`io.casehub.neocortex.memory.cbr.TrendType`)
- `timestamps` (`double[]`)
- `values` (`double[]`)

### `private static double computePerTimeSeries(io.casehub.neocortex.memory.cbr.TrendType type, double[] timestamps, int observationCount)`

#### Parameters

- `type` (`io.casehub.neocortex.memory.cbr.TrendType`)
- `timestamps` (`double[]`)
- `observationCount` (`int`)

### `static double delta(double[] values)`

#### Parameters

- `values` (`double[]`)

### `private static double duration(double[] timestamps)`

#### Parameters

- `timestamps` (`double[]`)

### `private static double durationMax(java.time.temporal.ChronoUnit timeUnit)`

#### Parameters

- `timeUnit` (`java.time.temporal.ChronoUnit`)

### `public static java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> enrichFeatures(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features, io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema)`

#### Parameters

- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)

### `public static io.casehub.neocortex.memory.cbr.CbrFeatureSchema expandSchema(io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema)`

#### Parameters

- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)

### `private static double[] extractTimestamps(java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> observations, java.lang.String timestampField)`

#### Parameters

- `observations` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `timestampField` (`java.lang.String`)

### `private static double[] extractValues(java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> observations, java.lang.String fieldName)`

#### Parameters

- `observations` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `fieldName` (`java.lang.String`)

### `private static double[] heuristicRange(io.casehub.neocortex.memory.cbr.TrendType type, io.casehub.neocortex.memory.cbr.FeatureField.Numeric inner, java.time.temporal.ChronoUnit timeUnit)`

#### Parameters

- `type` (`io.casehub.neocortex.memory.cbr.TrendType`)
- `inner` (`io.casehub.neocortex.memory.cbr.FeatureField.Numeric`)
- `timeUnit` (`java.time.temporal.ChronoUnit`)

### `private static double[] heuristicRangePerTs(io.casehub.neocortex.memory.cbr.TrendType type, java.time.temporal.ChronoUnit timeUnit)`

#### Parameters

- `type` (`io.casehub.neocortex.memory.cbr.TrendType`)
- `timeUnit` (`java.time.temporal.ChronoUnit`)

### `static double linearSlope(double[] timestamps, double[] values)`

#### Parameters

- `timestamps` (`double[]`)
- `values` (`double[]`)

### `static double volatility(double[] values)`

#### Parameters

- `values` (`double[]`)
