# io.casehub.neocortex.inference.MatryoshkaMultiModalEmbedder

**Package:** `io.casehub.neocortex.inference`

**Kind:** `class`

Matryoshka decorator for multi-modal embedders — truncates dense to target dimension
and L2-re-normalizes, passing sparse and ColBERT through unchanged.
<p>
Preserves existing Matryoshka truncation capability for models trained with
Matryoshka Representation Learning.

## Fields

### `delegate` (`io.casehub.neocortex.inference.MultiModalEmbedder`)

### `targetDimension` (`int`)

## Constructors

### `public MatryoshkaMultiModalEmbedder(io.casehub.neocortex.inference.MultiModalEmbedder delegate, int targetDimension)`

#### Parameters

- `delegate` (`io.casehub.neocortex.inference.MultiModalEmbedder`) — Embedder producing full-dimension dense embeddings
- `targetDimension` (`int`) — Target dense dimension (must be ≤ delegate's dense dimension)

#### Throws

- `IllegalArgumentException` — if targetDimension > delegate.denseDimension()

## Methods

### `public java.util.OptionalInt colbertDimension()`

### `public int denseDimension()`

### `public io.casehub.neocortex.inference.MultiModalEmbedding embed(java.lang.String text)`

#### Parameters

- `text` (`java.lang.String`)

### `public java.util.List<io.casehub.neocortex.inference.MultiModalEmbedding> embedBatch(java.util.List<java.lang.String> texts)`

#### Parameters

- `texts` (`java.util.List<java.lang.String>`)

### `public int maxSequenceLength()`

### `public java.util.Set<io.casehub.neocortex.inference.EmbeddingMode> supportedModes()`

### `private io.casehub.neocortex.inference.MultiModalEmbedding truncateAndRenormalize(io.casehub.neocortex.inference.MultiModalEmbedding embedding)`

#### Parameters

- `embedding` (`io.casehub.neocortex.inference.MultiModalEmbedding`)

### `public static io.casehub.neocortex.inference.MultiModalEmbedder wrapIfNeeded(io.casehub.neocortex.inference.MultiModalEmbedder embedder, java.util.OptionalInt dimension)`

Wraps embedder with Matryoshka truncation if dimension is present and embedder
is not already wrapped. Returns embedder unchanged if dimension is empty or
embedder is already a MatryoshkaMultiModalEmbedder.

#### Parameters

- `embedder` (`io.casehub.neocortex.inference.MultiModalEmbedder`) — embedder to potentially wrap
- `dimension` (`java.util.OptionalInt`) — target dimension (empty = no wrapping)

#### Returns

wrapped embedder or original embedder
