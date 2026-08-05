# io.casehub.neocortex.rag.DocumentStats

**Package:** `io.casehub.neocortex.rag`

**Kind:** `record`

## Fields

### `averageRetrievalScore` (`double`)

### `feedbackDistribution` (`java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer>`)

### `firstRetrieved` (`java.time.Instant`)

### `lastRetrieved` (`java.time.Instant`)

### `retrievalCount` (`int`)

### `sourceDocumentId` (`java.lang.String`)

## Record Components

### `averageRetrievalScore` (`double`)

### `feedbackDistribution` (`java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer>`)

### `firstRetrieved` (`java.time.Instant`)

### `lastRetrieved` (`java.time.Instant`)

### `retrievalCount` (`int`)

### `sourceDocumentId` (`java.lang.String`)

## Constructors

### `public DocumentStats(java.lang.String sourceDocumentId, int retrievalCount, java.time.Instant firstRetrieved, java.time.Instant lastRetrieved, double averageRetrievalScore, java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer> feedbackDistribution)`

#### Parameters

- `sourceDocumentId` (`java.lang.String`)
- `retrievalCount` (`int`)
- `firstRetrieved` (`java.time.Instant`)
- `lastRetrieved` (`java.time.Instant`)
- `averageRetrievalScore` (`double`)
- `feedbackDistribution` (`java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer>`)

## Methods

### `public double averageRetrievalScore()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.Map<io.casehub.neocortex.rag.RetrievalOutcome,java.lang.Integer> feedbackDistribution()`

### `public java.time.Instant firstRetrieved()`

### `public final int hashCode()`

### `public java.time.Instant lastRetrieved()`

### `public int retrievalCount()`

### `public java.lang.String sourceDocumentId()`

### `public final java.lang.String toString()`
