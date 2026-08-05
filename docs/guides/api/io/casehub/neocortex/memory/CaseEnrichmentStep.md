# io.casehub.neocortex.memory.CaseEnrichmentStep

**Package:** `io.casehub.neocortex.memory`

**Kind:** `interface`

SPI for enriching `MemoryInput` before storage.

<p><b>Progressive routing:</b> `appliesTo` and `enrich` receive the
progressively enriched input — the result of all prior steps (ordered by `priority()`),
not the original input.

## Methods

### `public abstract boolean appliesTo(io.casehub.neocortex.memory.MemoryInput input)`

#### Parameters

- `input` (`io.casehub.neocortex.memory.MemoryInput`)

### `public abstract io.casehub.neocortex.memory.MemoryInput enrich(io.casehub.neocortex.memory.MemoryInput input)`

#### Parameters

- `input` (`io.casehub.neocortex.memory.MemoryInput`)

### `public default int priority()`

### `public default boolean required()`
