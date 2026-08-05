# io.casehub.neocortex.inference.MultiModalEmbedding

**Package:** `io.casehub.neocortex.inference`

**Kind:** `class`

Multi-modal embedding output — dense, sparse, and/or ColBERT representations from a single pass.
<p>
Dense is mandatory. Sparse and ColBERT are optional (null when not produced by the embedder).
All arrays are deep-copied on construction and access to prevent external mutation.

## Fields

### `colbert` (`float[][]`)

### `dense` (`float[]`)

### `sparse` (`java.util.Map<java.lang.Integer,java.lang.Float>`)

## Constructors

### `public MultiModalEmbedding(float[] dense, java.util.Map<java.lang.Integer,java.lang.Float> sparse, float[][] colbert)`

#### Parameters

- `dense` (`float[]`) — Dense embedding vector (mandatory, must not be null)
- `sparse` (`java.util.Map<java.lang.Integer,java.lang.Float>`) — Sparse embedding as term-index → weight map (nullable)
- `colbert` (`float[][]`) — ColBERT token embeddings as 2D array [tokens][dim] (nullable)

#### Throws

- `NullPointerException` — if dense is null

## Methods

### `public float[][] colbert()`

#### Returns

ColBERT token embeddings (defensive copy, null if not available)

### `private static float[][] deepCopy(float[][] src)`

#### Parameters

- `src` (`float[][]`)

### `public float[] dense()`

#### Returns

Dense embedding (defensive copy)

### `public java.util.Map<java.lang.Integer,java.lang.Float> sparse()`

#### Returns

Sparse embedding map (unmodifiable view, null if not available)
