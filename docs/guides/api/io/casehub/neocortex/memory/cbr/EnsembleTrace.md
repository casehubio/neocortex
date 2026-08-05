# io.casehub.neocortex.memory.cbr.EnsembleTrace

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `caseType` (`java.lang.String`)

### `currentFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `ensembleConfidence` (`double`)

### `inputPlanCount` (`int`)

### `retrievalTraceId` (`java.lang.String`)

### `sourceCaseIds` (`java.util.List<java.lang.String>`)

### `stepAnalysis` (`java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus>`)

### `synthesizedSteps` (`java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep>`)

### `timestamp` (`java.time.Instant`)

### `traceId` (`java.lang.String`)

## Record Components

### `caseType` (`java.lang.String`)

### `currentFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `ensembleConfidence` (`double`)

### `inputPlanCount` (`int`)

### `retrievalTraceId` (`java.lang.String`)

### `sourceCaseIds` (`java.util.List<java.lang.String>`)

### `stepAnalysis` (`java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus>`)

### `synthesizedSteps` (`java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep>`)

### `timestamp` (`java.time.Instant`)

### `traceId` (`java.lang.String`)

## Constructors

### `public EnsembleTrace(java.lang.String traceId, java.lang.String retrievalTraceId, java.lang.String caseType, java.util.List<java.lang.String> sourceCaseIds, java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus> stepAnalysis, java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep> synthesizedSteps, int inputPlanCount, double ensembleConfidence, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> currentFeatures, java.time.Instant timestamp)`

#### Parameters

- `traceId` (`java.lang.String`)
- `retrievalTraceId` (`java.lang.String`)
- `caseType` (`java.lang.String`)
- `sourceCaseIds` (`java.util.List<java.lang.String>`)
- `stepAnalysis` (`java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus>`)
- `synthesizedSteps` (`java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep>`)
- `inputPlanCount` (`int`)
- `ensembleConfidence` (`double`)
- `currentFeatures` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `timestamp` (`java.time.Instant`)

## Methods

### `public java.lang.String caseType()`

### `public java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> currentFeatures()`

### `public double ensembleConfidence()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int inputPlanCount()`

### `public java.lang.String retrievalTraceId()`

### `public java.util.List<java.lang.String> sourceCaseIds()`

### `public java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus> stepAnalysis()`

### `public java.util.List<io.casehub.neocortex.memory.cbr.AdaptedStep> synthesizedSteps()`

### `public java.time.Instant timestamp()`

### `public final java.lang.String toString()`

### `public java.lang.String traceId()`
