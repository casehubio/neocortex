# io.casehub.neocortex.inference.InferenceModel

**Package:** `io.casehub.neocortex.inference`

**Kind:** `interface`

SPI for text-in, tensor-out inference. Thread-safe for concurrent
`.run`/`.runBatch` calls. One-shot lifecycle: construct,
use, close. Post-close calls throw `InferenceException`.

## Methods

### `public abstract void close()`

Releases resources. Idempotent — second and subsequent calls are no-ops.
Must not throw — implementations swallow cleanup errors.

### `public default java.util.OptionalInt outputSize()`

Number of values in each output. Empty if unknown or dynamic.

### `public abstract io.casehub.neocortex.inference.InferenceOutput run(io.casehub.neocortex.inference.InferenceInput input)`

Run inference on a single input.

#### Parameters

- `input` (`io.casehub.neocortex.inference.InferenceInput`) — must not be null

#### Throws

- `InferenceException` — if model is closed or inference fails

### `public abstract java.util.List<io.casehub.neocortex.inference.InferenceOutput> runBatch(java.util.List<io.casehub.neocortex.inference.InferenceInput> inputs)`

Batch inference. Returns one output per input, in order. The returned
list is unmodifiable.

#### Parameters

- `inputs` (`java.util.List<io.casehub.neocortex.inference.InferenceInput>`)

#### Throws

- `IllegalArgumentException` — if inputs is null or contains null elements
- `InferenceException` — if model is closed or inference fails
