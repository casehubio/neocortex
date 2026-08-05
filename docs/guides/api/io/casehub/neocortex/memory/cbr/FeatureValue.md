# io.casehub.neocortex.memory.cbr.FeatureValue

**Package:** `io.casehub.neocortex.memory.cbr`

**Kind:** `interface`

## Methods

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.NumberVal number(double value)`

#### Parameters

- `value` (`double`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.NumberListVal numberList(java.lang.Double[] values)`

#### Parameters

- `values` (`java.lang.Double[]`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.NumberListVal numberList(java.util.List<java.lang.Double> values)`

#### Parameters

- `values` (`java.util.List<java.lang.Double>`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue of(java.lang.Object value)`

#### Parameters

- `value` (`java.lang.Object`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.RangeVal range(double min, double max)`

#### Parameters

- `min` (`double`)
- `max` (`double`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.StringVal string(java.lang.String value)`

#### Parameters

- `value` (`java.lang.String`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.StringListVal stringList(java.lang.String[] values)`

#### Parameters

- `values` (`java.lang.String[]`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.StringListVal stringList(java.util.List<java.lang.String> values)`

#### Parameters

- `values` (`java.util.List<java.lang.String>`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.StructVal struct(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> fields)`

#### Parameters

- `fields` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.StructListVal structList(java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>> items)`

#### Parameters

- `items` (`java.util.List<java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>>`)

### `public static io.casehub.neocortex.memory.cbr.FeatureValue.StructListVal structList(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>[] items)`

#### Parameters

- `items` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>[]`)

### `public static java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> toFeatureMap(java.util.Map<java.lang.String,java.lang.Object> raw)`

#### Parameters

- `raw` (`java.util.Map<java.lang.String,java.lang.Object>`)

### `public static java.util.Map<java.lang.String,java.lang.Object> toRawMap(java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue> features)`

#### Parameters

- `features` (`java.util.Map<java.lang.String,io.casehub.neocortex.memory.cbr.FeatureValue>`)

### `public default java.lang.Object toRawValue()`
