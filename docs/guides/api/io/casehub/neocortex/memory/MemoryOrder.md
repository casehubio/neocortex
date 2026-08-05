# io.casehub.neocortex.memory.MemoryOrder

**Package:** `io.casehub.neocortex.memory`

**Kind:** `enum`

Controls how `CaseMemoryStore.query` ranks results.

<p>Non-semantic adapters (in-memory, JPA without FTS) always use
`.CHRONOLOGICAL` regardless of this field.

## Enum Constants

### `CHRONOLOGICAL` (`io.casehub.neocortex.memory.MemoryOrder`)

Results ordered by creation time, newest first.
All adapters honour this mode.

### `RELEVANCE` (`io.casehub.neocortex.memory.MemoryOrder`)

Results ordered by relevance to `MemoryQuery.question()`.
Adapters with relevance ranking (JPA FTS via ts_rank, Mem0 vector search,
Graphiti temporal graph) honour this; others silently fall back to
`.CHRONOLOGICAL`.
If `question` is `null`, all adapters fall back to
`.CHRONOLOGICAL`.

### `SALIENCE` (`io.casehub.neocortex.memory.MemoryOrder`)

Results ordered by salience: recency × importance.
Non-semantic adapters compute salience from `Memory.createdAt()` and
`Memory.importance()` — null importance treated as 1.0.
Semantic adapters that already handle relevance fall back to
`.RELEVANCE` (salience is a non-semantic ranking strategy).

## Constructors

### `private MemoryOrder()`

## Methods

### `public static io.casehub.neocortex.memory.MemoryOrder valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.neocortex.memory.MemoryOrder[] values()`
