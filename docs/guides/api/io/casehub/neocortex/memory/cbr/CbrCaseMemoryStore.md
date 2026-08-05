# io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `interface`

## Methods

### `public default java.util.Set<java.lang.String> discoverTenants(io.casehub.neocortex.memory.MemoryDomain domain)`

#### Parameters

- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `public abstract java.lang.Integer erase(io.casehub.neocortex.memory.EraseRequest request)`

#### Parameters

- `request` (`io.casehub.neocortex.memory.EraseRequest`)

### `public abstract java.lang.Integer eraseByScope(io.casehub.platform.api.path.Path scope, java.lang.String tenantId)`

#### Parameters

- `scope` (`io.casehub.platform.api.path.Path`)
- `tenantId` (`java.lang.String`)

### `public abstract java.lang.Integer eraseEntity(java.lang.String entityId, java.lang.String tenantId)`

#### Parameters

- `entityId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.neocortex.memory.cbr.SupersessionStatus> findSupersededCases(java.lang.String tenantId, io.casehub.neocortex.memory.MemoryDomain domain)`

#### Parameters

- `tenantId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `public abstract io.casehub.neocortex.memory.cbr.SupersessionStatus getSupersessionStatus(java.lang.String caseId, java.lang.String tenantId)`

#### Parameters

- `caseId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

### `public abstract java.lang.Integer purge(io.casehub.neocortex.memory.cbr.CbrRetentionPolicy policy)`

#### Parameters

- `policy` (`io.casehub.neocortex.memory.cbr.CbrRetentionPolicy`)

### `public abstract void recordOutcome(java.lang.String caseId, java.lang.String tenantId, io.casehub.neocortex.memory.cbr.CbrOutcome outcome)`

#### Parameters

- `caseId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)
- `outcome` (`io.casehub.neocortex.memory.cbr.CbrOutcome`)

### `public abstract void registerSchema(io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema)`

#### Parameters

- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)

### `public abstract void reinstate(java.lang.String caseId, java.lang.String tenantId)`

#### Parameters

- `caseId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.neocortex.memory.cbr.ScoredCbrCase<C>> retrieveSimilar(io.casehub.neocortex.memory.cbr.CbrQuery query, java.lang.Class<C> caseType)`

#### Parameters

- `query` (`io.casehub.neocortex.memory.cbr.CbrQuery`)
- `caseType` (`java.lang.Class<C>`)

### `public default io.casehub.neocortex.memory.cbr.CbrScanResult scan(io.casehub.neocortex.memory.cbr.CbrScanRequest request)`

#### Parameters

- `request` (`io.casehub.neocortex.memory.cbr.CbrScanRequest`)

### `public abstract java.lang.String store(io.casehub.neocortex.memory.cbr.CbrCase cbrCase, java.lang.String caseType, java.lang.String entityId, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String tenantId, java.lang.String caseId, io.casehub.platform.api.path.Path scope)`

#### Parameters

- `cbrCase` (`io.casehub.neocortex.memory.cbr.CbrCase`)
- `caseType` (`java.lang.String`)
- `entityId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `tenantId` (`java.lang.String`)
- `caseId` (`java.lang.String`)
- `scope` (`io.casehub.platform.api.path.Path`)

### `public abstract void supersede(java.lang.String caseId, java.lang.String tenantId, java.lang.String supersedingCaseId, java.lang.String reason)`

#### Parameters

- `caseId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)
- `supersedingCaseId` (`java.lang.String`)
- `reason` (`java.lang.String`)
