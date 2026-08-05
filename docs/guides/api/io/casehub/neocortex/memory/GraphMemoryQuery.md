# io.casehub.neocortex.memory.GraphMemoryQuery

**Package:** `io.casehub.neocortex.memory`

**Kind:** `record`

Graph-native query parameters for `GraphCaseMemoryStore.graphQuery(GraphMemoryQuery)`.

<p>`question` is required — `graphQuery()` is purely semantic; the underlying
REST search endpoint requires a query string. For non-semantic chronological retrieval use
`CaseMemoryStore.query(MemoryQuery)` with `MemoryOrder.CHRONOLOGICAL`.

<p>`domain` is required — mirrors `MemoryQuery` and ensures `Memory.domain()`
is always constructible (non-blank `MemoryDomain`).

## Fields

### `MAX_ENTITY_IDS` (`int`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityIds` (`java.util.List<java.lang.String>`)

### `entityTypes` (`java.util.Set<java.lang.String>`)

### `limit` (`int`)

### `question` (`java.lang.String`)

### `resultType` (`io.casehub.neocortex.memory.MemoryResultType`)

### `since` (`java.time.Instant`)

### `tenantId` (`java.lang.String`)

### `validAt` (`java.time.Instant`)

## Record Components

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `entityIds` (`java.util.List<java.lang.String>`)

### `entityTypes` (`java.util.Set<java.lang.String>`)

### `limit` (`int`)

### `question` (`java.lang.String`)

### `resultType` (`io.casehub.neocortex.memory.MemoryResultType`)

### `since` (`java.time.Instant`)

### `tenantId` (`java.lang.String`)

### `validAt` (`java.time.Instant`)

## Constructors

### `public GraphMemoryQuery(java.lang.String tenantId, java.util.List<java.lang.String> entityIds, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String question, int limit, java.time.Instant since, java.time.Instant validAt, java.util.Set<java.lang.String> entityTypes, io.casehub.neocortex.memory.MemoryResultType resultType)`

#### Parameters

- `tenantId` (`java.lang.String`)
- `entityIds` (`java.util.List<java.lang.String>`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `question` (`java.lang.String`)
- `limit` (`int`)
- `since` (`java.time.Instant`)
- `validAt` (`java.time.Instant`)
- `entityTypes` (`java.util.Set<java.lang.String>`)
- `resultType` (`io.casehub.neocortex.memory.MemoryResultType`)

## Methods

### `public io.casehub.neocortex.memory.MemoryDomain domain()`

### `public java.util.List<java.lang.String> entityIds()`

### `public java.util.Set<java.lang.String> entityTypes()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public static io.casehub.neocortex.memory.GraphMemoryQuery forEntity(java.lang.String entityId, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String tenantId, java.lang.String question)`

#### Parameters

- `entityId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `tenantId` (`java.lang.String`)
- `question` (`java.lang.String`)

### `public final int hashCode()`

### `public int limit()`

### `public java.lang.String question()`

### `public io.casehub.neocortex.memory.MemoryResultType resultType()`

### `public java.time.Instant since()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`

### `public java.time.Instant validAt()`

### `public io.casehub.neocortex.memory.GraphMemoryQuery withEntityTypes(java.util.Set<java.lang.String> entityTypes)`

#### Parameters

- `entityTypes` (`java.util.Set<java.lang.String>`)

### `public io.casehub.neocortex.memory.GraphMemoryQuery withLimit(int limit)`

#### Parameters

- `limit` (`int`)

### `public io.casehub.neocortex.memory.GraphMemoryQuery withResultType(io.casehub.neocortex.memory.MemoryResultType resultType)`

#### Parameters

- `resultType` (`io.casehub.neocortex.memory.MemoryResultType`)

### `public io.casehub.neocortex.memory.GraphMemoryQuery withSince(java.time.Instant since)`

#### Parameters

- `since` (`java.time.Instant`)

### `public io.casehub.neocortex.memory.GraphMemoryQuery withValidAt(java.time.Instant validAt)`

#### Parameters

- `validAt` (`java.time.Instant`)
