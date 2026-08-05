# io.casehub.neocortex.memory.cbr.CbrRetrievalTrace

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `query` (`io.casehub.neocortex.memory.cbr.CbrQuery`)

### `results` (`java.util.List<io.casehub.neocortex.memory.cbr.CbrRetrievalTrace.TracedCase>`)

### `timestamp` (`java.time.Instant`)

### `traceId` (`java.lang.String`)

## Record Components

### `query` (`io.casehub.neocortex.memory.cbr.CbrQuery`)

### `results` (`java.util.List<io.casehub.neocortex.memory.cbr.CbrRetrievalTrace.TracedCase>`)

### `timestamp` (`java.time.Instant`)

### `traceId` (`java.lang.String`)

## Constructors

### `public CbrRetrievalTrace(java.lang.String traceId, io.casehub.neocortex.memory.cbr.CbrQuery query, java.util.List<io.casehub.neocortex.memory.cbr.CbrRetrievalTrace.TracedCase> results, java.time.Instant timestamp)`

#### Parameters

- `traceId` (`java.lang.String`)
- `query` (`io.casehub.neocortex.memory.cbr.CbrQuery`)
- `results` (`java.util.List<io.casehub.neocortex.memory.cbr.CbrRetrievalTrace.TracedCase>`)
- `timestamp` (`java.time.Instant`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.neocortex.memory.cbr.CbrQuery query()`

### `public java.util.List<io.casehub.neocortex.memory.cbr.CbrRetrievalTrace.TracedCase> results()`

### `public java.time.Instant timestamp()`

### `public final java.lang.String toString()`

### `public java.lang.String traceId()`
