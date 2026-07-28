package io.casehub.neocortex.inference;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable input for inference. Sealed with two variants:
 * <ul>
 *   <li>{@link Text} — tokenized text input (single text or text pair)</li>
 *   <li>{@link Tensor} — raw named float tensors (bypasses tokenization)</li>
 * </ul>
 */
public sealed interface InferenceInput permits InferenceInput.Text, InferenceInput.Tensor {

    /** Single-text input. */
    static Text of(String text) {
        return new Text(List.of(Objects.requireNonNull(text, "text must not be null")));
    }

    /** Text-pair input (NLI premise/hypothesis, cross-encoder query/document). */
    static Text pair(String first, String second) {
        return new Text(List.of(
            Objects.requireNonNull(first, "first must not be null"),
            Objects.requireNonNull(second, "second must not be null")));
    }

    /** Raw tensor input — named float arrays, no tokenization. */
    static Tensor tensor(Map<String, float[][]> inputs) {
        return new Tensor(inputs);
    }

    record Text(List<String> texts) implements InferenceInput {
        public Text {
            if (texts == null || texts.isEmpty())
                throw new IllegalArgumentException("texts must not be empty");
            if (texts.size() > 2)
                throw new IllegalArgumentException("at most 2 texts supported (single text or text pair)");
            texts = List.copyOf(texts);
        }
    }

    record Tensor(Map<String, float[][]> inputs) implements InferenceInput {
        public Tensor {
            Objects.requireNonNull(inputs, "inputs must not be null");
            if (inputs.isEmpty())
                throw new IllegalArgumentException("inputs must not be empty");
            Map<String, float[][]> copy = new HashMap<>();
            for (Map.Entry<String, float[][]> entry : inputs.entrySet()) {
                float[][] src = Objects.requireNonNull(entry.getValue(),
                    "tensor value for '" + entry.getKey() + "' must not be null");
                float[][] dst = new float[src.length][];
                for (int i = 0; i < src.length; i++) dst[i] = src[i].clone();
                copy.put(entry.getKey(), dst);
            }
            inputs = Collections.unmodifiableMap(copy);
        }
    }
}
