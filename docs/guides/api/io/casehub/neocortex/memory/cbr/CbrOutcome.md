# io.casehub.neocortex.memory.cbr.CbrOutcome

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `DEFAULT_LEARNING_RATE` (`double`)

### `detail` (`java.lang.String`)

### `observedAt` (`java.time.Instant`)

### `result` (`io.casehub.neocortex.memory.cbr.CbrOutcome.Outcome`)

### `successRate` (`double`)

## Record Components

### `detail` (`java.lang.String`)

### `observedAt` (`java.time.Instant`)

### `result` (`io.casehub.neocortex.memory.cbr.CbrOutcome.Outcome`)

### `successRate` (`double`)

## Constructors

### `public CbrOutcome(io.casehub.neocortex.memory.cbr.CbrOutcome.Outcome result, double successRate, java.lang.String detail, java.time.Instant observedAt)`

#### Parameters

- `result` (`io.casehub.neocortex.memory.cbr.CbrOutcome.Outcome`)
- `successRate` (`double`)
- `detail` (`java.lang.String`)
- `observedAt` (`java.time.Instant`)

## Methods

### `public static double adjustConfidence(java.lang.Double oldConfidence, double successRate, double learningRate)`

#### Parameters

- `oldConfidence` (`java.lang.Double`)
- `successRate` (`double`)
- `learningRate` (`double`)

### `public java.lang.String detail()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.time.Instant observedAt()`

### `public static io.casehub.neocortex.memory.cbr.CbrOutcome of(double successRate, java.lang.String detail, java.time.Instant observedAt)`

#### Parameters

- `successRate` (`double`)
- `detail` (`java.lang.String`)
- `observedAt` (`java.time.Instant`)

### `public io.casehub.neocortex.memory.cbr.CbrOutcome.Outcome result()`

### `public double successRate()`

### `public final java.lang.String toString()`
