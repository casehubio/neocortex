# io.casehub.neocortex.memory.cbr.CbrQuery

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `caseType` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `filters` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.CbrFilter>`)

### `fusionStrategy` (`FusionStrategy`)

### `minSimilarity` (`double`)

### `notBefore` (`java.time.Instant`)

### `problem` (`java.lang.String`)

### `retrievalMode` (`io.casehub.neocortex.memory.cbr.RetrievalMode`)

### `scope` (`io.casehub.platform.api.path.Path`)

### `scopeDecay` (`io.casehub.neocortex.memory.cbr.ScopeDecay`)

### `temporalDecay` (`io.casehub.neocortex.memory.cbr.TemporalDecay`)

### `tenantId` (`java.lang.String`)

### `topK` (`int`)

### `vectorWeight` (`double`)

### `weights` (`java.util.Map<java.lang.String,java.lang.Double>`)

## Record Components

### `caseType` (`java.lang.String`)

### `domain` (`io.casehub.neocortex.memory.MemoryDomain`)

### `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `filters` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.CbrFilter>`)

### `fusionStrategy` (`FusionStrategy`)

### `minSimilarity` (`double`)

### `notBefore` (`java.time.Instant`)

### `problem` (`java.lang.String`)

### `retrievalMode` (`io.casehub.neocortex.memory.cbr.RetrievalMode`)

### `scope` (`io.casehub.platform.api.path.Path`)

### `scopeDecay` (`io.casehub.neocortex.memory.cbr.ScopeDecay`)

### `temporalDecay` (`io.casehub.neocortex.memory.cbr.TemporalDecay`)

### `tenantId` (`java.lang.String`)

### `topK` (`int`)

### `vectorWeight` (`double`)

### `weights` (`java.util.Map<java.lang.String,java.lang.Double>`)

## Constructors

### `public CbrQuery(java.lang.String tenantId, io.casehub.neocortex.memory.MemoryDomain domain, java.lang.String caseType, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.CbrFilter> filters, java.util.Map<java.lang.String,java.lang.Double> weights, int topK, double minSimilarity, java.time.Instant notBefore, java.lang.String problem, double vectorWeight, io.casehub.neocortex.memory.cbr.RetrievalMode retrievalMode, FusionStrategy fusionStrategy, io.casehub.neocortex.memory.cbr.TemporalDecay temporalDecay, io.casehub.platform.api.path.Path scope, io.casehub.neocortex.memory.cbr.ScopeDecay scopeDecay)`

#### Parameters

- `tenantId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `caseType` (`java.lang.String`)
- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `filters` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.CbrFilter>`)
- `weights` (`java.util.Map<java.lang.String,java.lang.Double>`)
- `topK` (`int`)
- `minSimilarity` (`double`)
- `notBefore` (`java.time.Instant`)
- `problem` (`java.lang.String`)
- `vectorWeight` (`double`)
- `retrievalMode` (`io.casehub.neocortex.memory.cbr.RetrievalMode`)
- `fusionStrategy` (`FusionStrategy`)
- `temporalDecay` (`io.casehub.neocortex.memory.cbr.TemporalDecay`)
- `scope` (`io.casehub.platform.api.path.Path`)
- `scopeDecay` (`io.casehub.neocortex.memory.cbr.ScopeDecay`)

## Methods

### `public java.lang.String caseType()`

### `public io.casehub.neocortex.memory.MemoryDomain domain()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features()`

### `public java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.CbrFilter> filters()`

### `public FusionStrategy fusionStrategy()`

### `public final int hashCode()`

### `public double minSimilarity()`

### `public java.time.Instant notBefore()`

### `public static io.casehub.neocortex.memory.cbr.CbrQuery of(java.lang.String tenantId, io.casehub.neocortex.memory.MemoryDomain domain, io.casehub.platform.api.path.Path scope, java.lang.String caseType, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features, int topK)`

#### Parameters

- `tenantId` (`java.lang.String`)
- `domain` (`io.casehub.neocortex.memory.MemoryDomain`)
- `scope` (`io.casehub.platform.api.path.Path`)
- `caseType` (`java.lang.String`)
- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `topK` (`int`)

### `public java.lang.String problem()`

### `public io.casehub.neocortex.memory.cbr.RetrievalMode retrievalMode()`

### `public io.casehub.platform.api.path.Path scope()`

### `public io.casehub.neocortex.memory.cbr.ScopeDecay scopeDecay()`

### `public io.casehub.neocortex.memory.cbr.TemporalDecay temporalDecay()`

### `public java.lang.String tenantId()`

### `public final java.lang.String toString()`

### `public int topK()`

### `public double vectorWeight()`

### `public java.util.Map<java.lang.String,java.lang.Double> weights()`

### `public io.casehub.neocortex.memory.cbr.CbrQuery withFeatures(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features)`

#### Parameters

- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withFilter(java.lang.String field, io.casehub.neocortex.memory.cbr.CbrFilter filter)`

#### Parameters

- `field` (`java.lang.String`)
- `filter` (`io.casehub.neocortex.memory.cbr.CbrFilter`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withFilters(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.CbrFilter> filters)`

#### Parameters

- `filters` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.CbrFilter>`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withFusionStrategy(FusionStrategy fusionStrategy)`

#### Parameters

- `fusionStrategy` (`FusionStrategy`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withMinSimilarity(double minSimilarity)`

#### Parameters

- `minSimilarity` (`double`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withNotBefore(java.time.Instant notBefore)`

#### Parameters

- `notBefore` (`java.time.Instant`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withProblem(java.lang.String problem)`

#### Parameters

- `problem` (`java.lang.String`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withRetrievalMode(io.casehub.neocortex.memory.cbr.RetrievalMode retrievalMode)`

#### Parameters

- `retrievalMode` (`io.casehub.neocortex.memory.cbr.RetrievalMode`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withScope(io.casehub.platform.api.path.Path scope)`

#### Parameters

- `scope` (`io.casehub.platform.api.path.Path`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withScopeDecay(io.casehub.neocortex.memory.cbr.ScopeDecay scopeDecay)`

#### Parameters

- `scopeDecay` (`io.casehub.neocortex.memory.cbr.ScopeDecay`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withTemporalDecay(io.casehub.neocortex.memory.cbr.TemporalDecay temporalDecay)`

#### Parameters

- `temporalDecay` (`io.casehub.neocortex.memory.cbr.TemporalDecay`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withVectorWeight(double vectorWeight)`

#### Parameters

- `vectorWeight` (`double`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withWeight(java.lang.String field, double weight)`

#### Parameters

- `field` (`java.lang.String`)
- `weight` (`double`)

### `public io.casehub.neocortex.memory.cbr.CbrQuery withWeights(java.util.Map<java.lang.String,java.lang.Double> weights)`

#### Parameters

- `weights` (`java.util.Map<java.lang.String,java.lang.Double>`)
