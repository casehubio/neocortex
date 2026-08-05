# io.casehub.neocortex.memory.cbr.AdaptationTrace

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `caseType` (`java.lang.String`)

### `currentFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `retrievalTraceId` (`java.lang.String`)

### `sourceCaseId` (`java.lang.String`)

### `sourceScore` (`double`)

### `steps` (`java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep>`)

### `timestamp` (`java.time.Instant`)

### `traceId` (`java.lang.String`)

## Record Components

### `caseType` (`java.lang.String`)

### `currentFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `retrievalTraceId` (`java.lang.String`)

### `sourceCaseId` (`java.lang.String`)

### `sourceScore` (`double`)

### `steps` (`java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep>`)

### `timestamp` (`java.time.Instant`)

### `traceId` (`java.lang.String`)

## Constructors

### `public AdaptationTrace(java.lang.String traceId, java.lang.String retrievalTraceId, java.lang.String caseType, java.lang.String sourceCaseId, double sourceScore, java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep> steps, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> currentFeatures, java.time.Instant timestamp)`

#### Parameters

- `traceId` (`java.lang.String`)
- `retrievalTraceId` (`java.lang.String`)
- `caseType` (`java.lang.String`)
- `sourceCaseId` (`java.lang.String`)
- `sourceScore` (`double`)
- `steps` (`java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep>`)
- `currentFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `timestamp` (`java.time.Instant`)

## Methods

### `public java.lang.String caseType()`

### `public java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> currentFeatures()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String retrievalTraceId()`

### `public java.lang.String sourceCaseId()`

### `public double sourceScore()`

### `public java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep> steps()`

### `public java.time.Instant timestamp()`

### `public final java.lang.String toString()`

### `public java.lang.String traceId()`
