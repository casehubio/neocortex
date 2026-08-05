# io.casehub.neocortex.memory.StoreAllResult

**Package:** `io.casehub.neocortex.memory`

**Kind:** `record`

Result of `CaseMemoryStore.storeAll`, carrying both the IDs assigned to
successfully stored inputs and any backend failures for inputs that could not be stored.

<p><strong>Security exceptions are never collected here.</strong> A
`SecurityException` from a tenant-mismatch check propagates immediately and
aborts the entire storeAll call — no partial result is returned.

<p><strong>Ordering invariant:</strong> `.stored()` lists IDs in the order the
corresponding inputs appeared in the original list. For inputs that failed, no ID is
present — correlate by `StoreFailure.inputIndex()`.

## Fields

### `failures` (`java.util.List<io.casehub.neocortex.memory.StoreFailure>`)

### `stored` (`java.util.List<java.lang.String>`)

## Record Components

### `failures` (`java.util.List<io.casehub.neocortex.memory.StoreFailure>`)

backend failures for inputs that could not be stored (never security failures)

### `stored` (`java.util.List<java.lang.String>`)

assigned memory IDs for all successfully stored inputs, in input order

## Constructors

### `public StoreAllResult(java.util.List<java.lang.String> stored, java.util.List<io.casehub.neocortex.memory.StoreFailure> failures)`

#### Parameters

- `stored` (`java.util.List<java.lang.String>`)
- `failures` (`java.util.List<io.casehub.neocortex.memory.StoreFailure>`)

## Methods

### `public boolean allSucceeded()`

Returns true if every input was stored successfully (no backend failures).

### `public static io.casehub.neocortex.memory.StoreAllResult empty()`

Returns an empty result (no successful stores, no failures).

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.List<io.casehub.neocortex.memory.StoreFailure> failures()`

### `public final int hashCode()`

### `public java.util.List<java.lang.String> stored()`

### `public final java.lang.String toString()`
