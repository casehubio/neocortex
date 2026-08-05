# io.casehub.neocortex.inference.InferenceInput

**Package:** `io.casehub.neocortex.inference`

**Kind:** `interface`

Immutable input for inference. Sealed with two variants:
<ul>
  <li>`Text` — tokenized text input (single text or text pair)</li>
  <li>`Tensor` — raw named float tensors (bypasses tokenization)</li>
</ul>

## Methods

### `public static io.casehub.neocortex.inference.InferenceInput.Text of(java.lang.String text)`

Single-text input.

#### Parameters

- `text` (`java.lang.String`)

### `public static io.casehub.neocortex.inference.InferenceInput.Text pair(java.lang.String first, java.lang.String second)`

Text-pair input (NLI premise/hypothesis, cross-encoder query/document).

#### Parameters

- `first` (`java.lang.String`)
- `second` (`java.lang.String`)

### `public static io.casehub.neocortex.inference.InferenceInput.Tensor tensor(java.util.Map<java.lang.String,float[][]> inputs)`

Raw tensor input — named float arrays, no tokenization.

#### Parameters

- `inputs` (`java.util.Map<java.lang.String,float[][]>`)
