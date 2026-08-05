# io.casehub.neocortex.memory.cbr.SimilaritySpec.EditDistanceSpec

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `deleteCost` (`java.lang.Double`)

### `insertCost` (`java.lang.Double`)

### `substitutionSimilarities` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>>`)

## Record Components

### `deleteCost` (`java.lang.Double`)

### `insertCost` (`java.lang.Double`)

### `substitutionSimilarities` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>>`)

## Constructors

### `public EditDistanceSpec(java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>> substitutionSimilarities)`

#### Parameters

- `substitutionSimilarities` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>>`)

### `public EditDistanceSpec(java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>> substitutionSimilarities, java.lang.Double insertCost, java.lang.Double deleteCost)`

#### Parameters

- `substitutionSimilarities` (`java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>>`)
- `insertCost` (`java.lang.Double`)
- `deleteCost` (`java.lang.Double`)

## Methods

### `public java.lang.Double deleteCost()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Double insertCost()`

### `public java.util.Map<java.lang.String,java.util.Map<java.lang.String,java.lang.Double>> substitutionSimilarities()`

### `public final java.lang.String toString()`
