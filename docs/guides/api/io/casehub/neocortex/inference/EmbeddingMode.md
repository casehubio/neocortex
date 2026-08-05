# io.casehub.neocortex.inference.EmbeddingMode

**Package:** `io.casehub.neocortex.inference`

**Kind:** `enum`

Embedding output mode supported by a multi-modal embedder.

## Enum Constants

### `COLBERT` (`io.casehub.neocortex.inference.EmbeddingMode`)

ColBERT late-interaction embeddings — sequence of token-level dense vectors.

### `DENSE` (`io.casehub.neocortex.inference.EmbeddingMode`)

Dense vector embedding — fixed-dimension real-valued vector.

### `SPARSE` (`io.casehub.neocortex.inference.EmbeddingMode`)

Sparse embedding — term-weight pairs, typically SPLADE-style learned sparse representation.

## Constructors

### `private EmbeddingMode()`

## Methods

### `public static io.casehub.neocortex.inference.EmbeddingMode valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.neocortex.inference.EmbeddingMode[] values()`
