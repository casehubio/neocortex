# io.casehub.neocortex.memory.cbr.FeatureField

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `interface`

## Methods

### `public static io.casehub.neocortex.memory.cbr.FeatureField categorical(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField categorical(java.lang.String name, io.casehub.neocortex.memory.cbr.SimilaritySpec similaritySpec)`

#### Parameters

- `name` (`java.lang.String`)
- `similaritySpec` (`io.casehub.neocortex.memory.cbr.SimilaritySpec`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField categoricalList(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField discreteSequence(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField discreteSequence(java.lang.String name, io.casehub.neocortex.memory.cbr.SimilaritySpec spec)`

#### Parameters

- `name` (`java.lang.String`)
- `spec` (`io.casehub.neocortex.memory.cbr.SimilaritySpec`)

### `public abstract java.lang.String name()`

### `public static io.casehub.neocortex.memory.cbr.FeatureField nestedObject(java.lang.String name, io.casehub.neocortex.memory.cbr.FeatureField[] innerFields)`

#### Parameters

- `name` (`java.lang.String`)
- `innerFields` (`io.casehub.neocortex.memory.cbr.FeatureField[]`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField numeric(java.lang.String name, double min, double max)`

#### Parameters

- `name` (`java.lang.String`)
- `min` (`double`)
- `max` (`double`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField numeric(java.lang.String name, double min, double max, io.casehub.neocortex.memory.cbr.SimilaritySpec similaritySpec)`

#### Parameters

- `name` (`java.lang.String`)
- `min` (`double`)
- `max` (`double`)
- `similaritySpec` (`io.casehub.neocortex.memory.cbr.SimilaritySpec`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField numericList(java.lang.String name, double min, double max)`

#### Parameters

- `name` (`java.lang.String`)
- `min` (`double`)
- `max` (`double`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField objectList(java.lang.String name, io.casehub.neocortex.memory.cbr.FeatureField[] innerFields)`

#### Parameters

- `name` (`java.lang.String`)
- `innerFields` (`io.casehub.neocortex.memory.cbr.FeatureField[]`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField semanticText(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField text(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField timeSeries(java.lang.String name, java.lang.String timestampField, io.casehub.neocortex.memory.cbr.FeatureField[] innerFields)`

#### Parameters

- `name` (`java.lang.String`)
- `timestampField` (`java.lang.String`)
- `innerFields` (`io.casehub.neocortex.memory.cbr.FeatureField[]`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField timeSeries(java.lang.String name, java.lang.String timestampField, io.casehub.neocortex.memory.cbr.SimilaritySpec spec, io.casehub.neocortex.memory.cbr.FeatureField[] innerFields)`

#### Parameters

- `name` (`java.lang.String`)
- `timestampField` (`java.lang.String`)
- `spec` (`io.casehub.neocortex.memory.cbr.SimilaritySpec`)
- `innerFields` (`io.casehub.neocortex.memory.cbr.FeatureField[]`)

### `public static io.casehub.neocortex.memory.cbr.FeatureField timeSeries(java.lang.String name, java.lang.String timestampField, io.casehub.neocortex.memory.cbr.SimilaritySpec spec, io.casehub.neocortex.memory.cbr.TrendSpec trendSpec, io.casehub.neocortex.memory.cbr.FeatureField[] innerFields)`

#### Parameters

- `name` (`java.lang.String`)
- `timestampField` (`java.lang.String`)
- `spec` (`io.casehub.neocortex.memory.cbr.SimilaritySpec`)
- `trendSpec` (`io.casehub.neocortex.memory.cbr.TrendSpec`)
- `innerFields` (`io.casehub.neocortex.memory.cbr.FeatureField[]`)

### `private static void validateFlatFields(java.util.List<io.casehub.neocortex.memory.cbr.FeatureField> fields)`

#### Parameters

- `fields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField>`)
