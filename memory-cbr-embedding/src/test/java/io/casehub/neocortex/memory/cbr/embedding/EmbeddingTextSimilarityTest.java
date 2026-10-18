package io.casehub.neocortex.memory.cbr.embedding;

import io.casehub.neocortex.inference.DenseEmbedder;
import org.junit.jupiter.api.Test;
import java.util.List;

import static io.casehub.neocortex.memory.cbr.FeatureValue.string;
import static org.assertj.core.api.Assertions.*;

class EmbeddingTextSimilarityTest {

    static DenseEmbedder stubModel() {
        return new DenseEmbedder() {
            @Override
            public float[] embed(String text) {
                return switch (text) {
                    case "hello" -> new float[]{1.0f, 0.0f, 0.0f};
                    case "hi" -> new float[]{0.9f, 0.1f, 0.0f};
                    case "goodbye" -> new float[]{0.0f, 1.0f, 0.0f};
                    default -> new float[]{0.0f, 0.0f, 1.0f};
                };
            }

            @Override
            public int dimension() { return 3; }
        };
    }

    @Test
    void identicalTextsScoreOne() {
        var sim = new EmbeddingTextSimilarity(stubModel());
        assertThat(sim.compute(string("hello"), string("hello"))).isEqualTo(1.0);
    }

    @Test
    void similarTextsScoreHighButNotOne() {
        var sim = new EmbeddingTextSimilarity(stubModel());
        double score = sim.compute(string("hello"), string("hi"));
        // cos(hello, hi) = (0.9)/sqrt(1*0.82) ≈ 0.9938
        assertThat(score).isGreaterThan(0.9);
        assertThat(score).isLessThan(1.0);
    }

    @Test
    void dissimilarTextsScoreLow() {
        var sim = new EmbeddingTextSimilarity(stubModel());
        double score = sim.compute(string("hello"), string("goodbye"));
        // cos([1,0,0], [0,1,0]) = 0.0
        assertThat(score).isCloseTo(0.0, offset(1e-6));
    }

    @Test
    void negativeCosineClampedToZero() {
        DenseEmbedder negModel = new DenseEmbedder() {
            @Override
            public float[] embed(String text) {
                if (text.equals("a")) return new float[]{1.0f, 0.0f};
                return new float[]{-1.0f, 0.0f};
            }

            @Override
            public int dimension() { return 2; }
        };
        var sim = new EmbeddingTextSimilarity(negModel);
        assertThat(sim.compute(string("a"), string("b"))).isEqualTo(0.0);
    }

    @Test
    void embedFailurePropagates() {
        DenseEmbedder failingModel = new DenseEmbedder() {
            @Override
            public float[] embed(String text) {
                throw new RuntimeException("Embedding service unavailable");
            }

            @Override
            public int dimension() { return 3; }
        };
        var sim = new EmbeddingTextSimilarity(failingModel);
        assertThatThrownBy(() -> sim.compute(string("a"), string("b")))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void queryEmbeddingCachedAcrossCalls() {
        int[] callCount = {0};
        DenseEmbedder countingModel = new DenseEmbedder() {
            @Override
            public float[] embed(String text) {
                callCount[0]++;
                return new float[]{1.0f, 0.0f, 0.0f};
            }

            @Override
            public int dimension() { return 3; }
        };
        var sim = new EmbeddingTextSimilarity(countingModel);
        sim.compute(string("query"), string("case1"));
        sim.compute(string("query"), string("case2"));
        // "query" embedded once (cached), "case1" once, "case2" once = 3 total
        assertThat(callCount[0]).isEqualTo(3);
    }

    @Test
    void precomputeBatchEmbedsAndWarmsCache() {
        int[] embedAllCalls = {0};
        DenseEmbedder batchModel = new DenseEmbedder() {
            @Override
            public float[] embed(String text) {
                return new float[]{1.0f, 0.0f, 0.0f};
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                embedAllCalls[0]++;
                return texts.stream().map(t -> new float[]{1.0f, 0.0f, 0.0f}).toList();
            }

            @Override
            public int dimension() { return 3; }
        };
        var sim = new EmbeddingTextSimilarity(batchModel);
        sim.precompute(List.of("a", "b", "c"));
        assertThat(embedAllCalls[0]).isEqualTo(1);

        // compute() should hit warm cache — no additional embed calls
        sim.compute(string("a"), string("b"));
        assertThat(embedAllCalls[0]).isEqualTo(1);
    }

    @Test
    void precomputeSkipsAlreadyCached() {
        int[] embedAllCalls = {0};
        DenseEmbedder batchModel = new DenseEmbedder() {
            @Override
            public float[] embed(String text) {
                return new float[]{1.0f, 0.0f, 0.0f};
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                embedAllCalls[0]++;
                return texts.stream().map(t -> new float[]{1.0f, 0.0f, 0.0f}).toList();
            }

            @Override
            public int dimension() { return 3; }
        };
        var sim = new EmbeddingTextSimilarity(batchModel);
        sim.precompute(List.of("a", "b"));
        sim.precompute(List.of("a", "c"));
        // Second call should only embed "c"
        assertThat(embedAllCalls[0]).isEqualTo(2);
    }

    private static org.assertj.core.data.Offset<Double> offset(double v) {
        return org.assertj.core.data.Offset.offset(v);
    }
}
