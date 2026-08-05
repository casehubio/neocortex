# io.casehub.neocortex.rag.DocumentImpact

**Package:** `io.casehub.neocortex.rag`

**Kind:** `record`

## Fields

### `aggregateOutcomes` (`java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer>`)

### `averageScore` (`double`)

### `distinctQueryCount` (`int`)

### `documentId` (`java.lang.String`)

### `totalRetrievals` (`int`)

## Record Components

### `aggregateOutcomes` (`java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer>`)

### `averageScore` (`double`)

### `distinctQueryCount` (`int`)

### `documentId` (`java.lang.String`)

### `totalRetrievals` (`int`)

## Constructors

### `public DocumentImpact(java.lang.String documentId, int distinctQueryCount, int totalRetrievals, double averageScore, java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer> aggregateOutcomes)`

#### Parameters

- `documentId` (`java.lang.String`)
- `distinctQueryCount` (`int`)
- `totalRetrievals` (`int`)
- `averageScore` (`double`)
- `aggregateOutcomes` (`java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer>`)

## Methods

### `public java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer> aggregateOutcomes()`

### `public double averageScore()`

### `public int distinctQueryCount()`

### `public java.lang.String documentId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`

### `public int totalRetrievals()`
