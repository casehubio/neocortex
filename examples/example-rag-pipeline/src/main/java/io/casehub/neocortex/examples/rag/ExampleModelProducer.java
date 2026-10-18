package io.casehub.neocortex.examples.rag;

import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.PoolingMode;
import io.casehub.neocortex.inference.DenseEmbedder;
import io.casehub.neocortex.inference.InferenceModel;
import io.casehub.neocortex.inference.quarkus.Inference;
import io.casehub.neocortex.inference.splade.SparseEmbedder;
import io.casehub.neocortex.inference.tasks.CrossEncoderReranker;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ExampleModelProducer {

    @Produces
    @ApplicationScoped
    DenseEmbedder denseEmbedder(
            @ConfigProperty(name = "casehub.examples.embedding.model-path") String modelPath,
            @ConfigProperty(name = "casehub.examples.embedding.tokenizer-path") String tokenizerPath) {
        var model = new OnnxEmbeddingModel(modelPath, tokenizerPath, PoolingMode.MEAN);
        return new DenseEmbedder() {
            @Override public float[] embed(String text) { return model.embed(text).content().vector(); }
            @Override public int dimension() { return model.dimension(); }
        };
    }

    @Produces
    @ApplicationScoped
    SparseEmbedder sparseEmbedder(@Inference("splade") InferenceModel model) {
        return new SparseEmbedder(model);
    }

    @Produces
    @ApplicationScoped
    CrossEncoderReranker reranker(@Inference("reranker") InferenceModel model) {
        return new CrossEncoderReranker(model);
    }
}
