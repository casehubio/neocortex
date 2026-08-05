# io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `record`

## Fields

### `innerFields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField>`)

### `name` (`java.lang.String`)

### `similaritySpec` (`io.casehub.neocortex.memory.cbr.SimilaritySpec`)

### `timestampField` (`java.lang.String`)

### `trendSpec` (`io.casehub.neocortex.memory.cbr.TrendSpec`)

## Record Components

### `innerFields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField>`)

### `name` (`java.lang.String`)

### `similaritySpec` (`io.casehub.neocortex.memory.cbr.SimilaritySpec`)

### `timestampField` (`java.lang.String`)

### `trendSpec` (`io.casehub.neocortex.memory.cbr.TrendSpec`)

## Constructors

### `public TimeSeries(java.lang.String name, java.util.List<io.casehub.neocortex.memory.cbr.FeatureField> innerFields, java.lang.String timestampField)`

#### Parameters

- `name` (`java.lang.String`)
- `innerFields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField>`)
- `timestampField` (`java.lang.String`)

### `public TimeSeries(java.lang.String name, java.util.List<io.casehub.neocortex.memory.cbr.FeatureField> innerFields, java.lang.String timestampField, io.casehub.neocortex.memory.cbr.SimilaritySpec similaritySpec)`

#### Parameters

- `name` (`java.lang.String`)
- `innerFields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField>`)
- `timestampField` (`java.lang.String`)
- `similaritySpec` (`io.casehub.neocortex.memory.cbr.SimilaritySpec`)

### `public TimeSeries(java.lang.String name, java.util.List<io.casehub.neocortex.memory.cbr.FeatureField> innerFields, java.lang.String timestampField, io.casehub.neocortex.memory.cbr.SimilaritySpec similaritySpec, io.casehub.neocortex.memory.cbr.TrendSpec trendSpec)`

#### Parameters

- `name` (`java.lang.String`)
- `innerFields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField>`)
- `timestampField` (`java.lang.String`)
- `similaritySpec` (`io.casehub.neocortex.memory.cbr.SimilaritySpec`)
- `trendSpec` (`io.casehub.neocortex.memory.cbr.TrendSpec`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.List<io.casehub.neocortex.memory.cbr.FeatureField> innerFields()`

### `public java.lang.String name()`

### `public io.casehub.neocortex.memory.cbr.SimilaritySpec similaritySpec()`

### `public java.lang.String timestampField()`

### `public final java.lang.String toString()`

### `public io.casehub.neocortex.memory.cbr.TrendSpec trendSpec()`
