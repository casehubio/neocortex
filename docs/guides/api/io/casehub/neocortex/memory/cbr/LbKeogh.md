# io.casehub.neocortex.memory.cbr.LbKeogh

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `class`

## Constructors

### `private LbKeogh()`

## Methods

### `public static io.casehub.neocortex.memory.cbr.LbKeogh.Envelope computeEnvelope(java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> sequence, io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries schema, int windowSize)`

#### Parameters

- `sequence` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `schema` (`io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries`)
- `windowSize` (`int`)

### `public static double lowerBound(java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> query, io.casehub.neocortex.memory.cbr.LbKeogh.Envelope caseEnvelope, io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries schema)`

#### Parameters

- `query` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)
- `caseEnvelope` (`io.casehub.neocortex.memory.cbr.LbKeogh.Envelope`)
- `schema` (`io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries`)
