package io.casehub.neocortex.inference;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces dense (float[]) embeddings from text.
 * <p>
 * This is the minimal embedding contract — text in, vector out. For multi-modal
 * embeddings (dense + sparse + ColBERT), see {@link MultiModalEmbedder}.
 */
public interface DenseEmbedder {

    float[] embed(String text);

    default List<float[]> embedBatch(List<String> texts) {
        var results = new ArrayList<float[]>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return List.copyOf(results);
    }

    int dimension();
}
