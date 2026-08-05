# io.casehub.neocortex.memory.cbr.CbrFeatureValidator

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `class`

## Constructors

### `private CbrFeatureValidator()`

## Methods

### `public static io.casehub.neocortex.memory.cbr.FeatureField findField(io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema, java.lang.String name)`

#### Parameters

- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)
- `name` (`java.lang.String`)

### `private static void requireCategoricalList(java.lang.String name, io.casehub.neocortex.memory.cbr.FeatureField field)`

#### Parameters

- `name` (`java.lang.String`)
- `field` (`io.casehub.neocortex.memory.cbr.FeatureField`)

### `private static void requireNumericList(java.lang.String name, io.casehub.neocortex.memory.cbr.FeatureField field)`

#### Parameters

- `name` (`java.lang.String`)
- `field` (`io.casehub.neocortex.memory.cbr.FeatureField`)

### `private static void requireType(java.lang.String name, io.casehub.neocortex.memory.cbr.FeatureValue value, java.lang.Class<? extends io.casehub.neocortex.memory.cbr.FeatureValue> expected, java.lang.String fieldTypeName)`

#### Parameters

- `name` (`java.lang.String`)
- `value` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `expected` (`java.lang.Class<? extends io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `fieldTypeName` (`java.lang.String`)

### `private static void validateDiscreteSequence(java.lang.String fieldName, io.casehub.neocortex.memory.cbr.FeatureValue value)`

#### Parameters

- `fieldName` (`java.lang.String`)
- `value` (`io.casehub.neocortex.memory.cbr.FeatureValue`)

### `public static void validateFilters(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.CbrFilter> filters, io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema)`

#### Parameters

- `filters` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.CbrFilter>`)
- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)

### `private static void validateHasMatchSubFields(java.lang.String fieldName, io.casehub.neocortex.memory.cbr.CbrFilter.HasMatch hm, java.util.List<io.casehub.neocortex.memory.cbr.FeatureField> innerFields)`

#### Parameters

- `fieldName` (`java.lang.String`)
- `hm` (`io.casehub.neocortex.memory.cbr.CbrFilter.HasMatch`)
- `innerFields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField>`)

### `private static void validateInnerValues(java.lang.String parentName, java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> map, java.util.List<io.casehub.neocortex.memory.cbr.FeatureField> innerFields)`

#### Parameters

- `parentName` (`java.lang.String`)
- `map` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `innerFields` (`java.util.List<io.casehub.neocortex.memory.cbr.FeatureField>`)

### `public static void validateQueryFeatures(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features, io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema)`

#### Parameters

- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)

### `private static void validateSingleFilter(java.lang.String name, io.casehub.neocortex.memory.cbr.CbrFilter filter, io.casehub.neocortex.memory.cbr.FeatureField field)`

#### Parameters

- `name` (`java.lang.String`)
- `filter` (`io.casehub.neocortex.memory.cbr.CbrFilter`)
- `field` (`io.casehub.neocortex.memory.cbr.FeatureField`)

### `public static void validateStoreFeatures(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features, io.casehub.neocortex.memory.cbr.CbrFeatureSchema schema)`

#### Parameters

- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)
- `schema` (`io.casehub.neocortex.memory.cbr.CbrFeatureSchema`)

### `private static void validateTimeSeries(java.lang.String fieldName, io.casehub.neocortex.memory.cbr.FeatureValue value, io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries ts)`

#### Parameters

- `fieldName` (`java.lang.String`)
- `value` (`io.casehub.neocortex.memory.cbr.FeatureValue`)
- `ts` (`io.casehub.neocortex.memory.cbr.FeatureField.TimeSeries`)
