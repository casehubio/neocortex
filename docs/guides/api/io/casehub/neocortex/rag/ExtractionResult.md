# io.casehub.neocortex.rag.ExtractionResult

**Package:** `io.casehub.neocortex.rag`

**Kind:** `record`

## Fields

### `body` (`java.lang.String`)

### `listMetadata` (`java.util.Map<java.lang.String,java.util.List<java.lang.String>>`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

## Record Components

### `body` (`java.lang.String`)

### `listMetadata` (`java.util.Map<java.lang.String,java.util.List<java.lang.String>>`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

## Constructors

### `public ExtractionResult(java.lang.String body, java.util.Map<java.lang.String,java.lang.String> metadata)`

#### Parameters

- `body` (`java.lang.String`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `public ExtractionResult(java.lang.String body, java.util.Map<java.lang.String,java.lang.String> metadata, java.util.Map<java.lang.String,java.util.List<java.lang.String>> listMetadata)`

#### Parameters

- `body` (`java.lang.String`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)
- `listMetadata` (`java.util.Map<java.lang.String,java.util.List<java.lang.String>>`)

## Methods

### `public java.lang.String body()`

### `private static java.util.Map<java.lang.String,java.util.List<java.lang.String>> deepCopyListMetadata(java.util.Map<java.lang.String,java.util.List<java.lang.String>> m)`

#### Parameters

- `m` (`java.util.Map<java.lang.String,java.util.List<java.lang.String>>`)

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.Map<java.lang.String,java.util.List<java.lang.String>> listMetadata()`

### `public java.util.Map<java.lang.String,java.lang.String> metadata()`

### `public final java.lang.String toString()`
