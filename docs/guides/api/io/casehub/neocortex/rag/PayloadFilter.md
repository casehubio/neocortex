# io.casehub.neocortex.rag.PayloadFilter

**Package:** `io.casehub.neocortex.rag`

**Kind:** `interface`

Sealed filter algebra for payload-level filtering in vector search.

<p>Implementations translate these nodes to backend-specific conditions:
Qdrant gRPC `Condition` messages, in-memory metadata matching, etc.
The sealed hierarchy enables exhaustive `switch` expressions (Java 21+).

## Methods

### `public static io.casehub.neocortex.rag.PayloadFilter and(io.casehub.neocortex.rag.PayloadFilter[] filters)`

#### Parameters

- `filters` (`io.casehub.neocortex.rag.PayloadFilter[]`)

### `public static io.casehub.neocortex.rag.PayloadFilter eq(java.lang.String field, java.lang.String value)`

#### Parameters

- `field` (`java.lang.String`)
- `value` (`java.lang.String`)

### `public static io.casehub.neocortex.rag.PayloadFilter gte(java.lang.String field, double value)`

#### Parameters

- `field` (`java.lang.String`)
- `value` (`double`)

### `public static io.casehub.neocortex.rag.PayloadFilter in(java.lang.String field, java.util.List<java.lang.String> values)`

#### Parameters

- `field` (`java.lang.String`)
- `values` (`java.util.List<java.lang.String>`)

### `public static io.casehub.neocortex.rag.PayloadFilter lte(java.lang.String field, double value)`

#### Parameters

- `field` (`java.lang.String`)
- `value` (`double`)

### `public static io.casehub.neocortex.rag.PayloadFilter not(io.casehub.neocortex.rag.PayloadFilter inner)`

#### Parameters

- `inner` (`io.casehub.neocortex.rag.PayloadFilter`)

### `public static io.casehub.neocortex.rag.PayloadFilter or(io.casehub.neocortex.rag.PayloadFilter[] filters)`

#### Parameters

- `filters` (`io.casehub.neocortex.rag.PayloadFilter[]`)

### `public static io.casehub.neocortex.rag.PayloadFilter range(java.lang.String field, double min, double max)`

#### Parameters

- `field` (`java.lang.String`)
- `min` (`double`)
- `max` (`double`)
