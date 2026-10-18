package io.casehub.neocortex.examples.cbr;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.casehub.neocortex.inference.DenseEmbedder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class CbrExampleProducer {

    @Produces
    @ApplicationScoped
    DenseEmbedder denseEmbedder() {
        var model = new AllMiniLmL6V2EmbeddingModel();
        return new DenseEmbedder() {
            @Override public float[] embed(String text) { return model.embed(text).content().vector(); }
            @Override public int dimension() { return model.dimension(); }
        };
    }
}
