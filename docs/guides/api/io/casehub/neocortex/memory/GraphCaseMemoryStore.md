# io.casehub.neocortex.memory.GraphCaseMemoryStore

**Package:** `io.casehub.neocortex.memory`

**Kind:** `interface`

Graph-native extension of CaseMemoryStore. Implemented by adapters backed by
temporal knowledge graph engines (Graphiti, Neo4j-direct, FalkorDB-direct, etc.).

<p>Callers needing temporal graph queries inject `GraphCaseMemoryStore` directly.
Callers needing only basic storage inject `CaseMemoryStore` — unaffected by
graph-specific parameters.

<p>`NoOpCaseMemoryStore` implements this interface; `UnsatisfiedResolutionException`
will not occur when no graph adapter is deployed.

## Methods

### `public abstract java.util.List<io.casehub.neocortex.memory.Memory> graphQuery(io.casehub.neocortex.memory.GraphMemoryQuery query)`

Semantic graph query. Uses the adapter's native search endpoint with graph-specific
parameters. `Memory.text()` carries LLM-extracted fact descriptions — not the
original stored text.

<p>The caller must supply a non-blank `question` — this path is purely semantic.
For chronological (non-semantic) retrieval use the base
`CaseMemoryStore.query(MemoryQuery)` with `MemoryOrder.CHRONOLOGICAL`.

#### Parameters

- `query` (`io.casehub.neocortex.memory.GraphMemoryQuery`)
