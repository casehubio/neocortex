# io.casehub.neocortex.rag.RetrievalQuery

**Package:** `io.casehub.neocortex.rag`

**Kind:** `record`

## Fields

### `expandedText` (`java.lang.String`)

### `text` (`java.lang.String`)

### `weightMultipliers` (`java.util.Map<java.lang.String,java.lang.Double>`)

## Record Components

### `expandedText` (`java.lang.String`)

### `text` (`java.lang.String`)

### `weightMultipliers` (`java.util.Map<java.lang.String,java.lang.Double>`)

## Constructors

### `public RetrievalQuery(java.lang.String text, java.lang.String expandedText, java.util.Map<java.lang.String,java.lang.Double> weightMultipliers)`

#### Parameters

- `text` (`java.lang.String`)
- `expandedText` (`java.lang.String`)
- `weightMultipliers` (`java.util.Map<java.lang.String,java.lang.Double>`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String expandedText()`

### `public final int hashCode()`

### `public static io.casehub.neocortex.rag.RetrievalQuery of(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)

### `public java.lang.String searchText()`

### `public java.lang.String text()`

### `public final java.lang.String toString()`

### `public java.util.Map<java.lang.String,java.lang.Double> weightMultipliers()`

### `public io.casehub.neocortex.rag.RetrievalQuery withBm25Boost(double multiplier)`

#### Parameters

- `multiplier` (`double`)

### `public io.casehub.neocortex.rag.RetrievalQuery withExpansion(java.lang.String expandedText)`

#### Parameters

- `expandedText` (`java.lang.String`)

### `public io.casehub.neocortex.rag.RetrievalQuery withWeightMultiplier(java.lang.String leg, double multiplier)`

#### Parameters

- `leg` (`java.lang.String`)
- `multiplier` (`double`)
