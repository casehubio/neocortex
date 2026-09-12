package io.casehub.neocortex.rag.spring;

import io.casehub.neocortex.fusion.FusionStrategy;
import io.casehub.neocortex.rag.runtime.DenseQuantization;
import io.casehub.neocortex.rag.runtime.RagConfig;
import io.casehub.neocortex.rag.runtime.TenancyStrategy;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

public record SpringRagConfig(
        QdrantConfig qdrant,
        TenancyStrategy tenancyStrategy,
        String denseVectorName,
        String sparseVectorName,
        boolean bm25Enabled,
        String bm25VectorName,
        String colbertVectorName,
        RetrievalConfig retrieval,
        int embeddingBatchSize,
        int maxMultivectorFloats,
        Optional<Integer> maxSequenceLength,
        MatryoshkaConfig matryoshka,
        QuantizationConfig quantization,
        ColbertQuantizationConfig colbertQuantization
) implements RagConfig {

    public record QdrantConfigImpl(String host, int port, Optional<String> apiKey, boolean useTls)
            implements RagConfig.QdrantConfig {}

    public record MatryoshkaConfigImpl(OptionalInt dimension) implements RagConfig.MatryoshkaConfig {}

    public record QuantizationConfigImpl(DenseQuantization type, boolean alwaysRam, OptionalDouble oversampling)
            implements RagConfig.QuantizationConfig {}

    public record ColbertQuantizationConfigImpl(DenseQuantization type, boolean alwaysRam)
            implements RagConfig.ColbertQuantizationConfig {}

    public record RetrievalConfigImpl(
            FusionStrategy fusionStrategy,
            int denseTopK,
            int sparseTopK,
            int bm25TopK,
            int rrfK,
            boolean rerankEnabled,
            int rerankTopN,
            FusionWeightsConfig weights,
            Optional<String> qualityPayloadField,
            double qualityMax
    ) implements RagConfig.RetrievalConfig {}

    public record FusionWeightsConfigImpl(double dense, double sparse, double bm25, double quality)
            implements RagConfig.FusionWeightsConfig {}
}
