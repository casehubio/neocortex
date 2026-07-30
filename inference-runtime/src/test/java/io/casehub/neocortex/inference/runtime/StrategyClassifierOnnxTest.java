package io.casehub.neocortex.inference.runtime;

import io.casehub.neocortex.inference.InferenceInput;
import io.casehub.neocortex.inference.tasks.ClassificationResult;
import io.casehub.neocortex.inference.tasks.TensorClassifier;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@Disabled("Requires trained ONNX models in src/test/resources/models/strategy/")
class StrategyClassifierOnnxTest {

    private static final int MAX_WINDOWS = 10;
    private static final int F_TEMPORAL = 101;
    private static final int F_MAP = 4;

    private static final List<String> VS_TERRAN_LABELS = List.of(
        "RUSH", "PROXY", "BANSHEE_HARASS", "AIR_SUPERIORITY",
        "MECH_PUSH", "BIO_TIMING", "MACRO_ECONOMY", "TECH_RUSH"
    );

    @Test
    void vsTerranModelLoadsAndClassifies(@TempDir Path tmpDir) throws Exception {
        Path modelPath = extractResource("/models/strategy/strategy_vs_terran.onnx", tmpDir);

        ModelConfig config = new ModelConfig(modelPath, null, 512, 4, 4, Map.of());

        try (OnnxInferenceModel model = new OnnxInferenceModel(config)) {
            TensorClassifier classifier = new TensorClassifier(model, VS_TERRAN_LABELS);

            float[][] temporal = new float[1][MAX_WINDOWS * F_TEMPORAL];
            float[][] map = new float[1][F_MAP];
            for (int i = 0; i < temporal[0].length; i++) temporal[0][i] = (float) Math.random();
            for (int i = 0; i < F_MAP; i++) map[0][i] = 0.5f;

            ClassificationResult result = classifier.classify(
                Map.of("temporal", temporal, "map", map)
            );

            assertThat(result.label()).isIn(VS_TERRAN_LABELS);
            assertThat(result.confidence()).isBetween(0.0f, 1.0f);

            float probSum = 0;
            for (float v : result.scores().values()) probSum += v;
            assertThat(probSum).isCloseTo(1.0f, within(1e-4f));
        }
    }

    @Test
    void latencyUnderThreshold(@TempDir Path tmpDir) throws Exception {
        Path modelPath = extractResource("/models/strategy/strategy_vs_terran.onnx", tmpDir);
        ModelConfig config = new ModelConfig(modelPath, null, 512, 4, 4, Map.of());

        try (OnnxInferenceModel model = new OnnxInferenceModel(config)) {
            float[][] temporal = new float[1][MAX_WINDOWS * F_TEMPORAL];
            float[][] map = new float[1][F_MAP];

            for (int i = 0; i < 100; i++) {
                model.run(InferenceInput.tensor(Map.of("temporal", temporal, "map", map)));
            }

            long[] nanos = new long[1000];
            for (int i = 0; i < 1000; i++) {
                long start = System.nanoTime();
                model.run(InferenceInput.tensor(Map.of("temporal", temporal, "map", map)));
                nanos[i] = System.nanoTime() - start;
            }

            java.util.Arrays.sort(nanos);
            double p99ms = nanos[989] / 1_000_000.0;
            assertThat(p99ms).as("p99 latency must be < 10ms").isLessThan(10.0);
        }
    }

    private Path extractResource(String resource, Path tmpDir) throws Exception {
        InputStream is = getClass().getResourceAsStream(resource);
        if (is == null) throw new IllegalStateException("Resource not found: " + resource);
        Path dest = tmpDir.resolve(Path.of(resource).getFileName());
        Files.copy(is, dest);
        return dest;
    }
}
