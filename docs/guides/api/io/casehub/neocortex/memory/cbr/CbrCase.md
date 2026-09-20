# io.casehub.neocortex.memory.cbr.CbrRecord

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `interface`

## Methods

### `public abstract java.lang.String recordType()`

### `public abstract java.lang.Double confidence()`

### `public default java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features()`

### `public abstract java.lang.String outcome()`

### `public abstract java.lang.String problem()`

### `public default java.lang.String producerAgentId()`

### `public abstract java.lang.String solution()`

### `public default java.lang.Double trustScore()`

### `public default io.casehub.neocortex.memory.cbr.CbrRecord withFeatures(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features)`

#### Parameters

- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `public abstract io.casehub.neocortex.memory.cbr.CbrRecord withOutcome(java.lang.String outcome, java.lang.Double confidence)`

#### Parameters

- `outcome` (`java.lang.String`)
- `confidence` (`java.lang.Double`)
