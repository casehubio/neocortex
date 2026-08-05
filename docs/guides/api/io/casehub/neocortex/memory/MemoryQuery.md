# io.casehub.neocortex.memory.MemoryQuery

**Package:** `io.casehub.neocortex.memory`

**Kind:** `record`

## Fields

### `MAX_ENTITY_IDS` (`int`)

Maximum entities per query. Covers realistic case party counts (2–15) with headroom.

### `caseId` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityIds` (`java.util.List<java.lang.String>`)

### `limit` (`int`)

### `order` (`io.casehub.neocortex.memory.MemoryOrder`)

### `question` (`java.lang.String`)

### `since` (`java.time.Instant`)

### `tenantId` (`java.lang.String`)

## Record Components

### `caseId` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityIds` (`java.util.List<java.lang.String>`)

### `limit` (`int`)

### `order` (`io.casehub.neocortex.memory.MemoryOrder`)

### `question` (`java.lang.String`)

### `since` (`java.time.Instant`)

### `tenantId` (`java.lang.String`)

## Constructors

### `public MemoryQuery(java.util.List<java.lang.String> entityIds, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String tenantId, java.lang.String caseId, java.lang.String question, int limit, java.time.Instant since, io.casehub.neocortex.memory.MemoryOrder order)`

#### Parameters

- `entityIds` (`java.util.List<java.lang.String>`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `tenantId` (`java.lang.String`)
- `caseId` (`java.lang.String`)
- `question` (`java.lang.String`)
- `limit` (`int`)
- `since` (`java.time.Instant`)
- `order` (`io.casehub.neocortex.memory.MemoryOrder`)

## Methods

### `public java.lang.String caseId()`

### `public io.casehub.neocortex.memory.MemoryDomain domain()`

### `public java.util.List<java.lang.String> entityIds()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public static io.casehub.neocortex.memory.MemoryQuery forEntities(java.util.List<java.lang.String> entityIds, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String tenantId)`

Construct a query for multiple entities (max #MAX_ENTITY_IDS).

<p>Defaults: `limit=20`, `order=``MemoryOrder.CHRONOLOGICAL`.
`limit` applies to the combined result set, not per-entity.
Use `with*` methods to override optional fields.

#### Parameters

- `entityIds` (`java.util.List<java.lang.String>`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `tenantId` (`java.lang.String`)

### `public static io.casehub.neocortex.memory.MemoryQuery forEntity(java.lang.String entityId, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String tenantId)`

Construct a query for a single entity.

<p>Defaults: `limit=20`, `order=``MemoryOrder.CHRONOLOGICAL`.
Use `with*` methods to override optional fields.

#### Parameters

- `entityId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `tenantId` (`java.lang.String`)

### `public final int hashCode()`

### `public int limit()`

### `public io.casehub.neocortex.memory.MemoryOrder order()`

### `public java.lang.String question()`

### `public java.time.Instant since()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`

### `public io.casehub.neocortex.memory.MemoryQuery withCaseId(java.lang.String caseId)`

#### Parameters

- `caseId` (`java.lang.String`)

### `public io.casehub.neocortex.memory.MemoryQuery withLimit(int limit)`

#### Parameters

- `limit` (`int`)

### `public io.casehub.neocortex.memory.MemoryQuery withOrder(io.casehub.neocortex.memory.MemoryOrder order)`

#### Parameters

- `order` (`io.casehub.neocortex.memory.MemoryOrder`)

### `public io.casehub.neocortex.memory.MemoryQuery withQuestion(java.lang.String question)`

#### Parameters

- `question` (`java.lang.String`)

### `public io.casehub.neocortex.memory.MemoryQuery withSince(java.time.Instant since)`

#### Parameters

- `since` (`java.time.Instant`)
