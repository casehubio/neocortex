package io.casehub.neocortex.rag.expansion;

import dev.langchain4j.model.embedding.EmbeddingModel;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.logging.Logger;

@ApplicationScoped
@IfBuildProperty(name = "casehub.rag.expansion.enabled", stringValue = "true")
public class ExpansionConfigValidator {

    private static final Logger LOG = Logger.getLogger(ExpansionConfigValidator.class.getName());

    @Inject
    ExpansionConfig config;

    @Inject
    @Any
    Instance<EmbeddingModel> embeddingModel;

    void onStartup(@Observes StartupEvent event) {
        if (config.mode().isEmpty()) {
            LOG.warning("Query expansion is enabled but no mode is set"
                        + " — queries will pass through unchanged."
                        + " Set casehub.rag.expansion.mode to llm, template, or step-back.");
        }

        ExpansionConfig.DriftConfig drift = config.drift();
        if (drift.enabled()) {
            if (drift.threshold() < 0.0 || drift.threshold() > 1.0) {
                throw new IllegalStateException(
                        "casehub.rag.expansion.drift.threshold must be in [0.0, 1.0], got " + drift.threshold());
            }
            if (!embeddingModel.isResolvable()) {
                LOG.warning("Drift detection enabled but no EmbeddingModel available"
                            + " — drift detection will be inactive.");
            }
        }
    }
}
