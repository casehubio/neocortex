# io.casehub.neocortex.memory.reflection.ReflectionEvent

**Package:** `io.casehub.neocortex.memory.reflection`

**Kind:** `record`

## Fields

### `agentId` (`java.lang.String`)

### `caseId` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `insight` (`java.lang.String`)

### `level` (`int`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `sourceMemoryIds` (`java.util.List<java.lang.String>`)

### `tenantId` (`java.lang.String`)

## Record Components

### `agentId` (`java.lang.String`)

### `caseId` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `insight` (`java.lang.String`)

### `level` (`int`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `sourceMemoryIds` (`java.util.List<java.lang.String>`)

### `tenantId` (`java.lang.String`)

## Constructors

### `public ReflectionEvent(java.lang.String agentId, java.lang.String tenantId, java.lang.String caseId, java.lang.String insight, int level, java.util.List<java.lang.String> sourceMemoryIds, java.lang.Double importance, java.util.Map<java.lang.String,java.lang.String> metadata)`

#### Parameters

- `agentId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)
- `caseId` (`java.lang.String`)
- `insight` (`java.lang.String`)
- `level` (`int`)
- `sourceMemoryIds` (`java.util.List<java.lang.String>`)
- `confidence` (`java.lang.Double`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

## Methods

### `public java.lang.String agentId()`

### `public java.lang.String caseId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Double importance()`

### `public java.lang.String insight()`

### `public int level()`

### `public java.util.Map<java.lang.String,java.lang.String> metadata()`

### `public java.util.List<java.lang.String> sourceMemoryIds()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`
