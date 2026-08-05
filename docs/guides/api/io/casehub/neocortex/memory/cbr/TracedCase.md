# io.casehub.neocortex.memory.cbr.CbrRetrievalTrace.TracedCase

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `caseId` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `featureSimilarities` (`java.util.Map<java.lang.String,java.lang.Double>`)

### `producerAgentId` (`java.lang.String`)

### `reranked` (`boolean`)

### `score` (`double`)

### `trustScore` (`java.lang.Double`)

### `trustTrajectory` (`java.lang.String`)

## Record Components

### `caseId` (`java.lang.String`)

### `confidence` (`java.lang.Double`)

### `featureSimilarities` (`java.util.Map<java.lang.String,java.lang.Double>`)

### `producerAgentId` (`java.lang.String`)

### `reranked` (`boolean`)

### `score` (`double`)

### `trustScore` (`java.lang.Double`)

### `trustTrajectory` (`java.lang.String`)

## Constructors

### `public TracedCase(java.lang.String caseId, double score, boolean reranked, java.util.Map<java.lang.String,java.lang.Double> featureSimilarities, java.lang.Double confidence, java.lang.Double trustScore, java.lang.String producerAgentId, java.lang.String trustTrajectory)`

#### Parameters

- `caseId` (`java.lang.String`)
- `score` (`double`)
- `reranked` (`boolean`)
- `featureSimilarities` (`java.util.Map<java.lang.String,java.lang.Double>`)
- `confidence` (`java.lang.Double`)
- `trustScore` (`java.lang.Double`)
- `producerAgentId` (`java.lang.String`)
- `trustTrajectory` (`java.lang.String`)

## Methods

### `public java.lang.String caseId()`

### `public java.lang.Double confidence()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.Map<java.lang.String,java.lang.Double> featureSimilarities()`

### `public final int hashCode()`

### `public java.lang.String producerAgentId()`

### `public boolean reranked()`

### `public double score()`

### `public final java.lang.String toString()`

### `public java.lang.Double trustScore()`

### `public java.lang.String trustTrajectory()`
