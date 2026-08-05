# io.casehub.neocortex.inference.InferenceOutput

**Package:** `io.casehub.neocortex.inference`

**Kind:** `class`

Immutable output from inference. Supports single-output models (backward compat)
and multi-output models (e.g., BGE-M3 dense+sparse+colbert).
Deep defensive copies on construction and all access methods prevent mutation.

## Fields

### `outputs` (`java.util.Map<java.lang.String,float[][]>`)

## Constructors

### `public InferenceOutput(java.util.Map<java.lang.String,float[][]> outputs)`

Constructs a multi-output inference result.

#### Parameters

- `outputs` (`java.util.Map<java.lang.String,float[][]>`) — map from output name to float[][] (rank-1 vectors in rank-2 array)

#### Throws

- `NullPointerException` — if outputs is null
- `IllegalArgumentException` — if outputs is empty

## Methods

### `private static float[][] deepCopyArray(float[][] src)`

#### Parameters

- `src` (`float[][]`)

### `private static java.util.Map<java.lang.String,float[][]> deepCopyOutputs(java.util.Map<java.lang.String,float[][]> src)`

#### Parameters

- `src` (`java.util.Map<java.lang.String,float[][]>`)

### `public boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public int hashCode()`

### `public static io.casehub.neocortex.inference.InferenceOutput of(float[] values)`

Static factory for single-output models (backward compatibility).

#### Parameters

- `values` (`float[]`) — output vector

#### Returns

InferenceOutput with single output named "output"

### `public float[][] output(java.lang.String name)`

Returns the full output array for a named output (rank-2).

#### Parameters

- `name` (`java.lang.String`) — output name

#### Returns

cloned float[][] for the named output

#### Throws

- `IllegalArgumentException` — if name is not in outputNames()

### `public java.util.Set<java.lang.String> outputNames()`

Returns the set of output names.

#### Returns

unmodifiable set of output names

### `public java.lang.String toString()`

### `public float[] values()`

Returns the single output vector (backward compat for single-output models).

#### Returns

cloned float[] from the single output

#### Throws

- `IllegalStateException` — if this is a multi-output model

### `public float[] vector(java.lang.String name)`

Returns the first vector from a named output (convenience for single-vector outputs).

#### Parameters

- `name` (`java.lang.String`) — output name

#### Returns

cloned float[] from output(name)[0]

#### Throws

- `IllegalArgumentException` — if name is not in outputNames()
