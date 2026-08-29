# io.casehub.neocortex.memory.relationship.RelationshipEvent

**Package:** `io.casehub.neocortex.memory.relationship`

**Kind:** `record`

## Fields

### `agentId` (`java.lang.String`)

### `caseId` (`java.lang.String`)

### `description` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `otherAgentId` (`java.lang.String`)

### `qualitySignal` (`io.casehub.neocortex.memory.relationship.QualitySignal`)

### `sourceEventType` (`java.lang.String`)

### `tenantId` (`java.lang.String`)

### `turnId` (`java.lang.String`)

## Record Components

### `agentId` (`java.lang.String`)

### `caseId` (`java.lang.String`)

### `description` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `otherAgentId` (`java.lang.String`)

### `qualitySignal` (`io.casehub.neocortex.memory.relationship.QualitySignal`)

### `sourceEventType` (`java.lang.String`)

### `tenantId` (`java.lang.String`)

### `turnId` (`java.lang.String`)

## Constructors

### `public RelationshipEvent(java.lang.String agentId, java.lang.String otherAgentId, java.lang.String tenantId, java.lang.String caseId, java.lang.String turnId, java.lang.String sourceEventType, io.casehub.neocortex.memory.relationship.QualitySignal qualitySignal, java.lang.String description, java.lang.Double importance, java.util.Map<java.lang.String,java.lang.String> metadata)`

#### Parameters

- `agentId` (`java.lang.String`)
- `otherAgentId` (`java.lang.String`)
- `tenantId` (`java.lang.String`)
- `caseId` (`java.lang.String`)
- `turnId` (`java.lang.String`)
- `sourceEventType` (`java.lang.String`)
- `qualitySignal` (`io.casehub.neocortex.memory.relationship.QualitySignal`)
- `description` (`java.lang.String`)
- `confidence` (`java.lang.Double`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

## Methods

### `public java.lang.String agentId()`

### `public java.lang.String caseId()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Double importance()`

### `public java.util.Map<java.lang.String,java.lang.String> metadata()`

### `public java.lang.String otherAgentId()`

### `public io.casehub.neocortex.memory.relationship.QualitySignal qualitySignal()`

### `public java.lang.String sourceEventType()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`

### `public java.lang.String turnId()`
