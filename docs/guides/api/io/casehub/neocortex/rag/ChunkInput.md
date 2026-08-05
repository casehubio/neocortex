# io.casehub.neocortex.rag.ChunkInput

**Package:** `io.casehub.neocortex.rag`

**Kind:** `record`

## Fields

### `RESERVED_KEYS` (`java.util.Set<java.lang.String>`)

### `content` (`java.lang.String`)

### `listMetadata` (`java.util.Map<java.lang.String,java.util.List<java.lang.String>>`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `sourceDocumentId` (`java.lang.String`)

## Record Components

### `content` (`java.lang.String`)

### `listMetadata` (`java.util.Map<java.lang.String,java.util.List<java.lang.String>>`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `sourceDocumentId` (`java.lang.String`)

## Constructors

### `public ChunkInput(java.lang.String content, java.lang.String sourceDocumentId, java.util.Map<java.lang.String,java.lang.String> metadata)`

#### Parameters

- `content` (`java.lang.String`)
- `sourceDocumentId` (`java.lang.String`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `public ChunkInput(java.lang.String content, java.lang.String sourceDocumentId, java.util.Map<java.lang.String,java.lang.String> metadata, java.util.Map<java.lang.String,java.util.List<java.lang.String>> listMetadata)`

#### Parameters

- `content` (`java.lang.String`)
- `sourceDocumentId` (`java.lang.String`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)
- `listMetadata` (`java.util.Map<java.lang.String,java.util.List<java.lang.String>>`)

## Methods

### `public java.lang.String content()`

### `private static java.util.Map<java.lang.String,java.util.List<java.lang.String>> deepCopyListMetadata(java.util.Map<java.lang.String,java.util.List<java.lang.String>> m)`

#### Parameters

- `m` (`java.util.Map<java.lang.String,java.util.List<java.lang.String>>`)

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.Map<java.lang.String,java.util.List<java.lang.String>> listMetadata()`

### `public java.util.Map<java.lang.String,java.lang.String> metadata()`

### `public java.lang.String sourceDocumentId()`

### `public final java.lang.String toString()`
