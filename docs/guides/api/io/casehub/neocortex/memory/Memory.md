# io.casehub.neocortex.memory.Memory

**Package:** `io.casehub.neocortex.memory`

**Kind:** `record`

## Fields

### `attributes` (`java.util.Map<java.lang.String,java.lang.String>`)

### `caseId` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityId` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `memoryId` (`java.lang.String`)

### `tenantId` (`java.lang.String`)

### `text` (`java.lang.String`)

## Record Components

### `attributes` (`java.util.Map<java.lang.String,java.lang.String>`)

### `caseId` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityId` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `memoryId` (`java.lang.String`)

### `tenantId` (`java.lang.String`)

### `text` (`java.lang.String`)

## Constructors

### `public Memory(java.lang.String memoryId, java.lang.String entityId, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String tenantId, java.lang.String caseId, java.lang.String text, java.util.Map<java.lang.String,java.lang.String> attributes, java.time.Instant createdAt, java.lang.Double importance)`

#### Parameters

- `memoryId` (`java.lang.String`)
- `entityId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `tenantId` (`java.lang.String`)
- `caseId` (`java.lang.String`)
- `text` (`java.lang.String`)
- `attributes` (`java.util.Map<java.lang.String,java.lang.String>`)
- `createdAt` (`java.time.Instant`)
- `confidence` (`java.lang.Double`)

## Methods

### `public java.util.Map<java.lang.String,java.lang.String> attributes()`

### `public java.lang.String caseId()`

### `public java.time.Instant createdAt()`

### `public io.casehub.neocortex.memory.MemoryDomain domain()`

### `public java.lang.String entityId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Double importance()`

### `public java.lang.String memoryId()`

### `public java.lang.String tenantId()`

### `public java.lang.String text()`

### `public final java.lang.String toString()`
