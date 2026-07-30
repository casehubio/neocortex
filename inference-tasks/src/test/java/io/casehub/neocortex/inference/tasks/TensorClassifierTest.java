package io.casehub.neocortex.inference.tasks;

import io.casehub.neocortex.inference.InferenceException;
import io.casehub.neocortex.inference.inmem.InMemoryInferenceModel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TensorClassifierTest {

    @Nested
    @DisplayName("classify()")
    class Classify {

        @Test
        void labelsMatchOutputIndices() {
            var model = InMemoryInferenceModel.returning(0.1f, 0.9f);
            var tc = new TensorClassifier(model, List.of("low", "high"));
            var inputs = Map.of("features", new float[][]{{1.0f, 2.0f}});
            ClassificationResult result = tc.classify(inputs);
            assertThat(result.label()).isEqualTo("high");
            assertThat(result.confidence()).isGreaterThan(0.5f);
        }

        @Test
        void softmaxApplied() {
            var model = InMemoryInferenceModel.returning(2.0f, 1.0f);
            var tc = new TensorClassifier(model, List.of("a", "b"));
            var inputs = Map.of("features", new float[][]{{1.0f}});
            ClassificationResult result = tc.classify(inputs);
            float sum = 0;
            for (float v : result.scores().values()) sum += v;
            assertThat(sum).isCloseTo(1.0f, within(1e-6f));
        }

        @Test
        void scoresContainsAllLabels() {
            var model = InMemoryInferenceModel.returning(1.0f, 2.0f, 3.0f);
            var tc = new TensorClassifier(model, List.of("a", "b", "c"));
            var inputs = Map.of("features", new float[][]{{0.0f}});
            ClassificationResult result = tc.classify(inputs);
            assertThat(result.scores()).hasSize(3);
            assertThat(result.scores()).containsKeys("a", "b", "c");
        }

        @Test
        void twoInputTensors() {
            var model = InMemoryInferenceModel.returning(1.0f, 0.5f);
            var tc = new TensorClassifier(model, List.of("a", "b"));
            var inputs = Map.of(
                "temporal", new float[][]{{1.0f, 2.0f, 3.0f}},
                "map", new float[][]{{0.5f, 0.3f}}
            );
            ClassificationResult result = tc.classify(inputs);
            assertThat(result.label()).isEqualTo("a");
        }
    }

    @Nested
    @DisplayName("construction validation")
    class ConstructionValidation {

        @Test
        void rejectsNullModel() {
            assertThatThrownBy(() -> new TensorClassifier(null, List.of("a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
        }

        @Test
        void rejectsNullLabels() {
            var model = InMemoryInferenceModel.returning(1.0f);
            assertThatThrownBy(() -> new TensorClassifier(model, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("labels");
        }

        @Test
        void rejectsEmptyLabels() {
            var model = InMemoryInferenceModel.returning(1.0f);
            assertThatThrownBy(() -> new TensorClassifier(model, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("labels");
        }

        @Test
        void rejectsLabelCountMismatch() {
            var model = InMemoryInferenceModel.returning(1.0f, 2.0f, 3.0f);
            assertThatThrownBy(() -> new TensorClassifier(model, List.of("a", "b")))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("argument validation")
    class ArgumentValidation {

        @Test
        void rejectsNullInputs() {
            var model = InMemoryInferenceModel.returning(1.0f);
            var tc = new TensorClassifier(model, List.of("a"));
            assertThatThrownBy(() -> tc.classify(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputs");
        }

        @Test
        void rejectsEmptyInputs() {
            var model = InMemoryInferenceModel.returning(1.0f);
            var tc = new TensorClassifier(model, List.of("a"));
            assertThatThrownBy(() -> tc.classify(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputs");
        }
    }
}
