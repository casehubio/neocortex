# io.casehub.neocortex.memory.cbr.StepConsensus

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `agreement` (`io.casehub.neocortex.memory.cbr.StepAgreement`)

### `bindingName` (`java.lang.String`)

### `capabilityName` (`java.lang.String`)

### `contributingCaseIds` (`java.util.List<java.lang.String>`)

### `occurrenceCount` (`int`)

### `outcomeDistribution` (`java.util.Map<java.lang.String,java.lang.Integer>`)

### `priorityDistribution` (`java.util.Map<java.lang.Integer,java.lang.Integer>`)

### `totalPlans` (`int`)

### `workerDistribution` (`java.util.Map<java.lang.String,java.lang.Integer>`)

## Record Components

### `agreement` (`io.casehub.neocortex.memory.cbr.StepAgreement`)

### `bindingName` (`java.lang.String`)

### `capabilityName` (`java.lang.String`)

### `contributingCaseIds` (`java.util.List<java.lang.String>`)

### `occurrenceCount` (`int`)

### `outcomeDistribution` (`java.util.Map<java.lang.String,java.lang.Integer>`)

### `priorityDistribution` (`java.util.Map<java.lang.Integer,java.lang.Integer>`)

### `totalPlans` (`int`)

### `workerDistribution` (`java.util.Map<java.lang.String,java.lang.Integer>`)

## Constructors

### `public StepConsensus(java.lang.String bindingName, java.lang.String capabilityName, int occurrenceCount, int totalPlans, java.util.Map<java.lang.String,java.lang.Integer> workerDistribution, java.util.Map<java.lang.String,java.lang.Integer> outcomeDistribution, java.util.Map<java.lang.Integer,java.lang.Integer> priorityDistribution, java.util.List<java.lang.String> contributingCaseIds, io.casehub.neocortex.memory.cbr.StepAgreement agreement)`

#### Parameters

- `bindingName` (`java.lang.String`)
- `capabilityName` (`java.lang.String`)
- `occurrenceCount` (`int`)
- `totalPlans` (`int`)
- `workerDistribution` (`java.util.Map<java.lang.String,java.lang.Integer>`)
- `outcomeDistribution` (`java.util.Map<java.lang.String,java.lang.Integer>`)
- `priorityDistribution` (`java.util.Map<java.lang.Integer,java.lang.Integer>`)
- `contributingCaseIds` (`java.util.List<java.lang.String>`)
- `agreement` (`io.casehub.neocortex.memory.cbr.StepAgreement`)

## Methods

### `public io.casehub.neocortex.memory.cbr.StepAgreement agreement()`

### `public java.lang.String bindingName()`

### `public java.lang.String capabilityName()`

### `public java.util.List<java.lang.String> contributingCaseIds()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int occurrenceCount()`

### `public java.util.Map<java.lang.String,java.lang.Integer> outcomeDistribution()`

### `public java.util.Map<java.lang.Integer,java.lang.Integer> priorityDistribution()`

### `public final java.lang.String toString()`

### `public int totalPlans()`

### `public java.util.Map<java.lang.String,java.lang.Integer> workerDistribution()`
