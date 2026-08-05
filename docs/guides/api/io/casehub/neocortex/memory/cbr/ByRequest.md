# io.casehub.neocortex.memory.cbr.CbrCasesErased.ByRequest

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `caseId` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityId` (`java.lang.String`)

### `erasedAt` (`java.time.Instant`)

### `erasedCount` (`int`)

### `tenantId` (`java.lang.String`)

## Record Components

### `caseId` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityId` (`java.lang.String`)

### `erasedAt` (`java.time.Instant`)

### `erasedCount` (`int`)

### `tenantId` (`java.lang.String`)

## Constructors

### `public ByRequest(java.lang.String tenantId, int erasedCount, java.lang.String entityId, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String caseId, java.time.Instant erasedAt)`

#### Parameters

- `tenantId` (`java.lang.String`)
- `erasedCount` (`int`)
- `entityId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `caseId` (`java.lang.String`)
- `erasedAt` (`java.time.Instant`)

## Methods

### `public java.lang.String caseId()`

### `public io.casehub.neocortex.memory.MemoryDomain domain()`

### `public java.lang.String entityId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.time.Instant erasedAt()`

### `public int erasedCount()`

### `public final int hashCode()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`
