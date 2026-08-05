# io.casehub.neocortex.memory.cbr.TemporalDecay.Step

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `afterCutoff` (`double`)

### `cutoff` (`java.time.Duration`)

## Record Components

### `afterCutoff` (`double`)

### `cutoff` (`java.time.Duration`)

## Constructors

### `public Step(java.time.Duration cutoff, double afterCutoff)`

#### Parameters

- `cutoff` (`java.time.Duration`)
- `afterCutoff` (`double`)

## Methods

### `public double afterCutoff()`

### `public java.time.Duration cutoff()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public double factor(java.time.Instant storedAt, java.time.Instant now)`

#### Parameters

- `storedAt` (`java.time.Instant`)
- `now` (`java.time.Instant`)

### `public final int hashCode()`

### `public final java.lang.String toString()`
