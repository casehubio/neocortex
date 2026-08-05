# io.casehub.neocortex.memory.cbr.EnsemblePlan

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `ensembleConfidence` (`double`)

### `inputPlanCount` (`int`)

### `sourceCaseIds` (`java.util.List<java.lang.String>`)

### `stepAnalysis` (`java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus>`)

### `synthesizedPlan` (`io.casehub.neocortex.memory.cbr.AdaptedPlan`)

## Record Components

### `ensembleConfidence` (`double`)

### `inputPlanCount` (`int`)

### `sourceCaseIds` (`java.util.List<java.lang.String>`)

### `stepAnalysis` (`java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus>`)

### `synthesizedPlan` (`io.casehub.neocortex.memory.cbr.AdaptedPlan`)

## Constructors

### `public EnsemblePlan(io.casehub.neocortex.memory.cbr.AdaptedPlan synthesizedPlan, java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus> stepAnalysis, java.util.List<java.lang.String> sourceCaseIds, double ensembleConfidence, int inputPlanCount)`

#### Parameters

- `synthesizedPlan` (`io.casehub.neocortex.memory.cbr.AdaptedPlan`)
- `stepAnalysis` (`java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus>`)
- `sourceCaseIds` (`java.util.List<java.lang.String>`)
- `ensembleConfidence` (`double`)
- `inputPlanCount` (`int`)

## Methods

### `public double ensembleConfidence()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int inputPlanCount()`

### `public java.util.List<java.lang.String> sourceCaseIds()`

### `public java.util.List<io.casehub.neocortex.memory.cbr.StepConsensus> stepAnalysis()`

### `public io.casehub.neocortex.memory.cbr.AdaptedPlan synthesizedPlan()`

### `public final java.lang.String toString()`
