# io.casehub.neocortex.memory.cbr.CbrRetrievalTracker

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `interface`

## Methods

### `public abstract java.util.List<io.casehub.neocortex.memory.cbr.CbrRetrievalTrace> findTraces(java.lang.String caseType, java.lang.String tenantId, io.casehub.neocortex.memory.MemoryDomain domain, java.time.Instant since, java.time.Instant until)`

#### Parameters

- `caseType` (`java.lang.String`)
- `tenantId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `since` (`java.time.Instant`)
- `until` (`java.time.Instant`)

### `public abstract int purgeOlderThan(java.time.Instant cutoff)`

#### Parameters

- `cutoff` (`java.time.Instant`)

### `public abstract java.lang.String record(io.casehub.neocortex.memory.cbr.CbrQuery query, java.util.List<io.casehub.neocortex.memory.cbr.ScoredCbrCase<?>> results)`

#### Parameters

- `query` (`io.casehub.neocortex.memory.cbr.CbrQuery`)
- `results` (`java.util.List<io.casehub.neocortex.memory.cbr.ScoredCbrCase<?>>`)
