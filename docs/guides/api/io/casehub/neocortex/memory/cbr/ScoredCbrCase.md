# io.casehub.neocortex.memory.cbr.ScoredCbrCase

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `caseId` (`java.lang.String`)

### `cbrCase` (`C`)

### `featureSimilarities` (`java.util.Map<java.lang.String,java.lang.Double>`)

### `reranked` (`boolean`)

### `scope` (`io.casehub.platform.api.path.Path`)

### `score` (`double`)

### `storedAt` (`java.time.Instant`)

### `trustTrajectory` (`java.lang.Double`)

## Record Components

### `caseId` (`java.lang.String`)

### `cbrCase` (`C`)

### `featureSimilarities` (`java.util.Map<java.lang.String,java.lang.Double>`)

### `reranked` (`boolean`)

### `scope` (`io.casehub.platform.api.path.Path`)

### `score` (`double`)

### `storedAt` (`java.time.Instant`)

### `trustTrajectory` (`java.lang.Double`)

## Constructors

### `public ScoredCbrCase(C cbrCase, double score)`

#### Parameters

- `cbrCase` (`C`)
- `score` (`double`)

### `public ScoredCbrCase(C cbrCase, double score, boolean reranked)`

#### Parameters

- `cbrCase` (`C`)
- `score` (`double`)
- `reranked` (`boolean`)

### `public ScoredCbrCase(C cbrCase, double score, boolean reranked, java.util.Map<java.lang.String,java.lang.Double> featureSimilarities)`

#### Parameters

- `cbrCase` (`C`)
- `score` (`double`)
- `reranked` (`boolean`)
- `featureSimilarities` (`java.util.Map<java.lang.String,java.lang.Double>`)

### `public ScoredCbrCase(C cbrCase, java.lang.String caseId, double score)`

#### Parameters

- `cbrCase` (`C`)
- `caseId` (`java.lang.String`)
- `score` (`double`)

### `public ScoredCbrCase(C cbrCase, java.lang.String caseId, double score, boolean reranked, java.util.Map<java.lang.String,java.lang.Double> featureSimilarities, java.time.Instant storedAt, io.casehub.platform.api.path.Path scope, java.lang.Double trustTrajectory)`

#### Parameters

- `cbrCase` (`C`)
- `caseId` (`java.lang.String`)
- `score` (`double`)
- `reranked` (`boolean`)
- `featureSimilarities` (`java.util.Map<java.lang.String,java.lang.Double>`)
- `storedAt` (`java.time.Instant`)
- `scope` (`io.casehub.platform.api.path.Path`)
- `trustTrajectory` (`java.lang.Double`)

## Methods

### `public java.lang.String caseId()`

### `public C cbrCase()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.Map<java.lang.String,java.lang.Double> featureSimilarities()`

### `public final int hashCode()`

### `public boolean reranked()`

### `public io.casehub.platform.api.path.Path scope()`

### `public double score()`

### `public java.time.Instant storedAt()`

### `public final java.lang.String toString()`

### `public java.lang.Double trustTrajectory()`

### `public io.casehub.neocortex.memory.cbr.ScoredCbrCase<C> withReranked()`

### `public io.casehub.neocortex.memory.cbr.ScoredCbrCase<C> withScore(double newScore)`

#### Parameters

- `newScore` (`double`)

### `public io.casehub.neocortex.memory.cbr.ScoredCbrCase<C> withTrustTrajectory(java.lang.Double delta)`

#### Parameters

- `delta` (`java.lang.Double`)
