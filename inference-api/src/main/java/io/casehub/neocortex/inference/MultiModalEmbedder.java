package io.casehub.neocortex.inference;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Embedder that produces multi-modal output — dense, sparse, and/or ColBERT representations.
 * <p>
 * All embedders produce dense vectors. Sparse and ColBERT are optional capabilities
 * reported by {@link #supportedModes()}.
 */
public interface MultiModalEmbedder {
    /**
     * Embed a single text.
     *
     * @param text Input text
     * @return Multi-modal embedding
     */
    MultiModalEmbedding embed(String text);

    /**
     * Embed a batch of texts.
     *
     * @param texts Input texts
     * @return Multi-modal embeddings in the same order as inputs
     */
    List<MultiModalEmbedding> embedBatch(List<String> texts);

    /**
     * Embed two texts for per-leg separation: dense from {@code denseText}, sparse/ColBERT
     * from {@code nonDenseText}. Preserves ONNX batch composition by delegating to
     * {@link #embedBatch} — individual {@link #embed(String)} calls (batch=1) can produce
     * different embeddings due to padding/attention mask differences in transformer models.
     *
     * @param denseText    text for the dense embedding leg
     * @param nonDenseText text for sparse and ColBERT legs
     * @return composite embedding with dense from denseText, sparse/colbert from nonDenseText
     */
    default MultiModalEmbedding embedSeparate(String denseText, String nonDenseText) {
        Objects.requireNonNull(denseText, "denseText must not be null");
        Objects.requireNonNull(nonDenseText, "nonDenseText must not be null");
        if (denseText.equals(nonDenseText)) {return embed(denseText);}
        List<MultiModalEmbedding> batch          = embedBatch(List.of(denseText, nonDenseText));
        MultiModalEmbedding       denseResult    = batch.get(0);
        MultiModalEmbedding       nonDenseResult = batch.get(1);
        return new MultiModalEmbedding(
                denseResult.dense(),
                nonDenseResult.sparse(),
                nonDenseResult.colbert());
    }

    /**
     * @return Embedding modes produced by this embedder (always includes {@code DENSE})
     */
    Set<EmbeddingMode> supportedModes();

    /**
     * @return Dense vector dimension
     */
    int denseDimension();

    /**
     * @return ColBERT token dimension (empty if ColBERT not supported)
     */
    OptionalInt colbertDimension();

    /**
     * @return Maximum token sequence length — bounds ColBERT output rows per point
     */
    int maxSequenceLength();
}
