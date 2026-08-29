# io.casehub.neocortex.memory.MemoryInput

**Package:** `io.casehub.neocortex.memory`

**Kind:** `record`

## Fields

### `attributes` (`java.util.Map<java.lang.String,java.lang.String>`)

### `caseId` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityId` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `tenantId` (`java.lang.String`)

### `text` (`java.lang.String`)

## Record Components

### `attributes` (`java.util.Map<java.lang.String,java.lang.String>`)

### `caseId` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityId` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `tenantId` (`java.lang.String`)

### `text` (`java.lang.String`)

## Constructors

### `public MemoryInput(java.lang.String entityId, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String tenantId, java.lang.String caseId, java.lang.String text, java.util.Map<java.lang.String,java.lang.String> attributes, java.lang.Double importance)`

#### Parameters

- `entityId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `tenantId` (`java.lang.String`)
- `caseId` (`java.lang.String`)
- `text` (`java.lang.String`)
- `attributes` (`java.util.Map<java.lang.String,java.lang.String>`)
- `confidence` (`java.lang.Double`)

## Methods

### `public java.util.Map<java.lang.String,java.lang.String> attributes()`

### `public java.lang.String caseId()`

### `public io.casehub.neocortex.memory.MemoryDomain domain()`

### `public java.lang.String entityId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Double importance()`

### `public java.lang.String tenantId()`

### `public java.lang.String text()`

### `public final java.lang.String toString()`

### `public io.casehub.neocortex.memory.MemoryInput withAttribute(java.lang.String key, java.lang.String value)`

#### Parameters

- `key` (`java.lang.String`)
- `value` (`java.lang.String`)

### `public io.casehub.neocortex.memory.MemoryInput withAttributes(java.util.Map<java.lang.String,java.lang.String> additional)`

#### Parameters

- `additional` (`java.util.Map<java.lang.String,java.lang.String>`)

### `public io.casehub.neocortex.memory.MemoryInput withText(java.lang.String newText)`

#### Parameters

- `newText` (`java.lang.String`)
