package io.casehub.neocortex.rag.crossencoder;

import io.casehub.neocortex.inference.tasks.CrossEncoderReranker;
import io.casehub.neocortex.rag.ColBertRelevanceEvaluator;
import io.casehub.neocortex.rag.RelevanceEvaluator;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.casehub.neocortex.rag.crossencoder.corrective.CragConfig;
import io.casehub.neocortex.rag.crossencoder.corrective.CrossEncoderRelevanceEvaluator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
public class CrossEncoderBeanProducer {

    private static final org.jboss.logging.Logger LOG =
            org.jboss.logging.Logger.getLogger(CrossEncoderBeanProducer.class);

    @Inject
    CragConfig                     config;
    @Inject
    Instance<CrossEncoderReranker> rerankerInstance;

    @ConfigProperty(name = "casehub.rag.retrieval.rerank-enabled",
                    defaultValue = "false")
    boolean rerankEnabled;

    @Produces
    @ApplicationScoped
    RelevanceEvaluator evaluator() {
        if (rerankerInstance.isResolvable()) {
            return new CrossEncoderRelevanceEvaluator(
                    rerankerInstance.get(),
                    config.correctThreshold(),
                    config.incorrectThreshold());
        }
        if (!rerankEnabled) {
            throw new IllegalStateException(
                    "No CrossEncoderReranker available and ColBERT reranking "
                    + "is not enabled (casehub.rag.retrieval.rerank-enabled"
                    + "=false). Configure a cross-encoder model or enable "
                    + "ColBERT reranking.");
        }
        LOG.infof("No CrossEncoderReranker — using ColBertRelevanceEvaluator "
                  + "(correct >= %.2f, incorrect <= %.2f)",
                  config.colbert().correctThreshold(),
                  config.colbert().incorrectThreshold());
        return new ColBertRelevanceEvaluator(
                config.colbert().correctThreshold(),
                config.colbert().incorrectThreshold());
    }
}
