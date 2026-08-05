# io.casehub.neocortex.memory.cbr.SupersessionStatus

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `NOT_SUPERSEDED` (`io.casehub.neocortex.memory.cbr.SupersessionStatus`)

### `caseId` (`java.lang.String`)

### `reason` (`java.lang.String`)

### `reinstatedAt` (`java.time.Instant`)

### `superseded` (`boolean`)

### `supersededAt` (`java.time.Instant`)

### `supersedingCaseId` (`java.lang.String`)

## Record Components

### `caseId` (`java.lang.String`)

### `reason` (`java.lang.String`)

### `reinstatedAt` (`java.time.Instant`)

### `superseded` (`boolean`)

### `supersededAt` (`java.time.Instant`)

### `supersedingCaseId` (`java.lang.String`)

## Constructors

### `public SupersessionStatus(java.lang.String caseId, boolean superseded, java.time.Instant supersededAt, java.lang.String supersedingCaseId, java.lang.String reason, java.time.Instant reinstatedAt)`

#### Parameters

- `caseId` (`java.lang.String`)
- `superseded` (`boolean`)
- `supersededAt` (`java.time.Instant`)
- `supersedingCaseId` (`java.lang.String`)
- `reason` (`java.lang.String`)
- `reinstatedAt` (`java.time.Instant`)

## Methods

### `public java.lang.String caseId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String reason()`

### `public java.time.Instant reinstatedAt()`

### `public boolean superseded()`

### `public java.time.Instant supersededAt()`

### `public java.lang.String supersedingCaseId()`

### `public final java.lang.String toString()`

### `public boolean wasReinstated()`
