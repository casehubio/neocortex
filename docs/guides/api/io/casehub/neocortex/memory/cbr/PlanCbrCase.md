# io.casehub.neocortex.memory.cbr.PlanCbrCase

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `CBR_TYPE` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `outcome` (`java.lang.String`)

### `planTrace` (`java.util.List<io.casehub.neocortex.memory.cbr.PlanTrace>`)

### `problem` (`java.lang.String`)

### `producerAgentId` (`java.lang.String`)

### `solution` (`java.lang.String`)

### `trustScore` (`java.lang.Double`)

## Record Components

### `confidence` (`java.lang.Double`)

### `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `outcome` (`java.lang.String`)

### `planTrace` (`java.util.List<io.casehub.neocortex.memory.cbr.PlanTrace>`)

### `problem` (`java.lang.String`)

### `producerAgentId` (`java.lang.String`)

### `solution` (`java.lang.String`)

### `trustScore` (`java.lang.Double`)

## Constructors

### `public PlanCbrCase(java.lang.String problem, java.lang.String solution, java.lang.String outcome, java.lang.Double confidence, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features, java.util.List<io.casehub.neocortex.memory.cbr.PlanTrace> planTrace, java.lang.Double trustScore, java.lang.String producerAgentId)`

#### Parameters

- `problem` (`java.lang.String`)
- `solution` (`java.lang.String`)
- `outcome` (`java.lang.String`)
- `confidence` (`java.lang.Double`)
- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `planTrace` (`java.util.List<io.casehub.neocortex.memory.cbr.PlanTrace>`)
- `trustScore` (`java.lang.Double`)
- `producerAgentId` (`java.lang.String`)

## Methods

### `public java.lang.String cbrType()`

### `public java.lang.Double confidence()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features()`

### `public final int hashCode()`

### `public java.lang.String outcome()`

### `public java.util.List<io.casehub.neocortex.memory.cbr.PlanTrace> planTrace()`

### `public java.lang.String problem()`

### `public java.lang.String producerAgentId()`

### `public java.lang.String solution()`

### `public final java.lang.String toString()`

### `public java.lang.Double trustScore()`

### `public io.casehub.neocortex.memory.cbr.CbrCase withFeatures(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features)`

#### Parameters

- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `public io.casehub.neocortex.memory.cbr.CbrCase withOutcome(java.lang.String outcome, java.lang.Double confidence)`

#### Parameters

- `outcome` (`java.lang.String`)
- `confidence` (`java.lang.Double`)
