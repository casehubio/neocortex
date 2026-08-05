# io.casehub.neocortex.memory.cbr.CbrRetentionPolicy

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `caseType` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `maxAgeDays` (`java.lang.Integer`)

### `maxCasesPerType` (`java.lang.Integer`)

### `minTrustScore` (`java.lang.Double`)

### `tenantId` (`java.lang.String`)

## Record Components

### `caseType` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `maxAgeDays` (`java.lang.Integer`)

### `maxCasesPerType` (`java.lang.Integer`)

### `minTrustScore` (`java.lang.Double`)

### `tenantId` (`java.lang.String`)

## Constructors

### `public CbrRetentionPolicy(java.lang.String tenantId, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String caseType, java.lang.Integer maxAgeDays, java.lang.Integer maxCasesPerType, java.lang.Double minTrustScore)`

#### Parameters

- `tenantId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `caseType` (`java.lang.String`)
- `maxAgeDays` (`java.lang.Integer`)
- `maxCasesPerType` (`java.lang.Integer`)
- `minTrustScore` (`java.lang.Double`)

## Methods

### `public java.lang.String caseType()`

### `public io.casehub.neocortex.memory.MemoryDomain domain()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Integer maxAgeDays()`

### `public java.lang.Integer maxCasesPerType()`

### `public java.lang.Double minTrustScore()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`
