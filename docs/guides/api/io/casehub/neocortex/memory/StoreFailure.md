# io.casehub.neocortex.memory.StoreFailure

**Package:** `io.casehub.neocortex.memory`

**Kind:** `record`

Records a single backend failure from a `CaseMemoryStore.storeAll` call.

<p>SecurityException is never wrapped here — it propagates immediately and the whole
storeAll call aborts. StoreFailure carries only non-security backend failures.

## Fields

### `cause` (`java.lang.RuntimeException`)

### `input` (`io.casehub.neocortex.memory.MemoryInput`)

### `inputIndex` (`int`)

## Record Components

### `cause` (`java.lang.RuntimeException`)

the exception thrown by the backend store operation

### `input` (`io.casehub.neocortex.memory.MemoryInput`)

the MemoryInput that failed (use for retry)

### `inputIndex` (`int`)

zero-based position of the failed input in the original list

## Constructors

### `public StoreFailure(int inputIndex, io.casehub.neocortex.memory.MemoryInput input, java.lang.RuntimeException cause)`

#### Parameters

- `inputIndex` (`int`)
- `input` (`io.casehub.neocortex.memory.MemoryInput`)
- `cause` (`java.lang.RuntimeException`)

## Methods

### `public java.lang.RuntimeException cause()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.neocortex.memory.MemoryInput input()`

### `public int inputIndex()`

### `public final java.lang.String toString()`
