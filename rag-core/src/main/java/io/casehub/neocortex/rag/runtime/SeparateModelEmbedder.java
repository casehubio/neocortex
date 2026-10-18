package io.casehub.neocortex.rag.runtime;

import io.casehub.neocortex.inference.DenseEmbedder;
import io.casehub.neocortex.inference.EmbeddingMode;
import io.casehub.neocortex.inference.MultiModalEmbedder;
import io.casehub.neocortex.inference.MultiModalEmbedding;
import io.casehub.neocortex.inference.splade.SparseEmbedder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Adapts separate dense ({@link DenseEmbedder}) and optional sparse ({@link SparseEmbedder}) models
 * into the MultiModalEmbedder contract.
 * <p>
 * This is the default CDI-produced MultiModalEmbedder when no native multi-modal
 * model (e.g. BGE-M3) is available.
 */
public final class SeparateModelEmbedder implements MultiModalEmbedder {

    private final DenseEmbedder      denseModel;
    private final SparseEmbedder     sparseEmbedder;
    private final int                maxSequenceLength;
    private final Set<EmbeddingMode> modes;

    public SeparateModelEmbedder(DenseEmbedder denseModel, int maxSequenceLength) {
        this(denseModel, null, maxSequenceLength);
    }

    public SeparateModelEmbedder(DenseEmbedder denseModel, SparseEmbedder sparseEmbedder,
                                 int maxSequenceLength) {
        this.denseModel     = Objects.requireNonNull(denseModel, "denseModel");
        this.sparseEmbedder = sparseEmbedder;
        if (maxSequenceLength <= 0) {throw new IllegalArgumentException("maxSequenceLength must be positive");}
        this.maxSequenceLength = maxSequenceLength;
        this.modes             = sparseEmbedder != null
                                 ? Set.of(EmbeddingMode.DENSE, EmbeddingMode.SPARSE)
                                 : Set.of(EmbeddingMode.DENSE);
    }

    @Override
    public MultiModalEmbedding embed(String text) {
        float[]             dense  = denseModel.embed(text);
        Map<Integer, Float> sparse = sparseEmbedder != null ? sparseEmbedder.embed(text) : null;
        return new MultiModalEmbedding(dense, sparse, null);
    }


    @Override
    public List<MultiModalEmbedding> embedBatch(List<String> texts) {
        List<float[]>             denseResults  = denseModel.embedBatch(texts);
        List<Map<Integer, Float>> sparseResults = sparseEmbedder != null
                                                  ? sparseEmbedder.embedBatch(texts) : null;

        List<MultiModalEmbedding> results = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            float[]             dense  = denseResults.get(i);
            Map<Integer, Float> sparse = sparseResults != null ? sparseResults.get(i) : null;
            results.add(new MultiModalEmbedding(dense, sparse, null));
        }
        return List.copyOf(results);
    }

    @Override
    public Set<EmbeddingMode> supportedModes() {return modes;}

    @Override
    public int denseDimension()                {return denseModel.dimension();}

    @Override
    public OptionalInt colbertDimension()      {return OptionalInt.empty();}

    @Override
    public int maxSequenceLength()             {return maxSequenceLength;}
}
