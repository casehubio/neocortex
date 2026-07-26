package io.casehub.neocortex.inference;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MultiModalEmbedderEmbedSeparateTest {

    @Test
    void embedSeparateUsesEmbedBatchToPreserveBatchComposition() {
        var embedder = new BatchSensitiveEmbedder();

        MultiModalEmbedding result = embedder.embedSeparate("dense-query", "sparse-query");

        List<MultiModalEmbedding> batchResult = embedder.embedBatch(
                List.of("dense-query", "sparse-query"));
        assertArrayEquals(batchResult.get(0).dense(), result.dense(), 1e-6f,
                "dense must come from embedBatch[0], not individual embed");
        assertEquals(batchResult.get(1).sparse(), result.sparse(),
                "sparse must come from embedBatch[1], not individual embed");
    }

    @Test
    void embedSeparateSameTextDelegatesToEmbed() {
        var embedder = new BatchSensitiveEmbedder();

        MultiModalEmbedding result = embedder.embedSeparate("same-text", "same-text");
        MultiModalEmbedding single = embedder.embed("same-text");

        assertArrayEquals(single.dense(), result.dense(), 1e-6f);
        assertEquals(single.sparse(), result.sparse());
    }

    /**
     * Simulates ONNX batch-dependent behavior: embedBatch produces different
     * results than individual embed calls due to padding/attention mask differences.
     */
    private static final class BatchSensitiveEmbedder implements MultiModalEmbedder {

        @Override
        public MultiModalEmbedding embed(String text) {
            float hash = text.hashCode() & 0xFFFF;
            return new MultiModalEmbedding(
                    new float[]{hash, hash + 1},
                    Map.of(0, hash / 1000f),
                    null);
        }

        @Override
        public List<MultiModalEmbedding> embedBatch(List<String> texts) {
            float batchModifier = texts.size() * 0.001f;
            return texts.stream().map(text -> {
                float hash = text.hashCode() & 0xFFFF;
                return new MultiModalEmbedding(
                        new float[]{hash + batchModifier, hash + 1 + batchModifier},
                        Map.of(0, (hash + batchModifier) / 1000f),
                        null);
            }).toList();
        }

        @Override
        public Set<EmbeddingMode> supportedModes() {
            return Set.of(EmbeddingMode.DENSE, EmbeddingMode.SPARSE);
        }

        @Override
        public int denseDimension() {return 2;}

        @Override
        public OptionalInt colbertDimension() {return OptionalInt.empty();}

        @Override
        public int maxSequenceLength() {return 512;}
    }
}
