# io.casehub.neocortex.inference.MultiModalEmbedder

**Package:** `io.casehub.neocortex.inference`

**Kind:** `interface`

Embedder that produces multi-modal output — dense, sparse, and/or ColBERT representations.
<p>
All embedders produce dense vectors. Sparse and ColBERT are optional capabilities
reported by `.supportedModes()`.

## Methods

### `public abstract java.util.OptionalInt colbertDimension()`

#### Returns

ColBERT token dimension (empty if ColBERT not supported)

### `public abstract int denseDimension()`

#### Returns

Dense vector dimension

### `public abstract io.casehub.neocortex.inference.MultiModalEmbedding embed(java.lang.String text)`

Embed a single text.

#### Parameters

- `text` (`java.lang.String`) — Input text

#### Returns

Multi-modal embedding

### `public abstract java.util.List<io.casehub.neocortex.inference.MultiModalEmbedding> embedBatch(java.util.List<java.lang.String> texts)`

Embed a batch of texts.

#### Parameters

- `texts` (`java.util.List<java.lang.String>`) — Input texts

#### Returns

Multi-modal embeddings in the same order as inputs

### `public default io.casehub.neocortex.inference.MultiModalEmbedding embedSeparate(java.lang.String denseText, java.lang.String nonDenseText)`

Embed two texts for per-leg separation: dense from `denseText`, sparse/ColBERT
from `nonDenseText`. Preserves ONNX batch composition by delegating to
`.embedBatch` — individual `.embed(String)` calls (batch=1) can produce
different embeddings due to padding/attention mask differences in transformer models.

#### Parameters

- `denseText` (`java.lang.String`) — text for the dense embedding leg
- `nonDenseText` (`java.lang.String`) — text for sparse and ColBERT legs

#### Returns

composite embedding with dense from denseText, sparse/colbert from nonDenseText

### `public abstract int maxSequenceLength()`

#### Returns

Maximum token sequence length — bounds ColBERT output rows per point

### `public abstract java.util.Set<io.casehub.neocortex.inference.EmbeddingMode> supportedModes()`

#### Returns

Embedding modes produced by this embedder (always includes `DENSE`)
