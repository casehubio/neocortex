# io.casehub.neocortex.memory.cbr.TemporalDecay.Linear

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `zeroAt` (`java.time.Duration`)

## Record Components

### `zeroAt` (`java.time.Duration`)

## Constructors

### `public Linear(java.time.Duration zeroAt)`

#### Parameters

- `zeroAt` (`java.time.Duration`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public double factor(java.time.Instant storedAt, java.time.Instant now)`

#### Parameters

- `storedAt` (`java.time.Instant`)
- `now` (`java.time.Instant`)

### `public final int hashCode()`

### `public final java.lang.String toString()`

### `public java.time.Duration zeroAt()`
