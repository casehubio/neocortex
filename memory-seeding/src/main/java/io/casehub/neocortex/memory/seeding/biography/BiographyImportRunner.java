package io.casehub.neocortex.memory.seeding.biography;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class BiographyImportRunner {

    private final List<BiographyHandler>       handlers;
    private final BiographyProfilePreprocessor preprocessor;
    private final Set<String>                  seededAgents = Collections.synchronizedSet(new HashSet<>());

    @Inject
    public BiographyImportRunner(Instance<BiographyHandler> handlers,
                                 Instance<BiographyProfilePreprocessor> preprocessor) {
        this.handlers = new ArrayList<>();
        handlers.forEach(this.handlers::add);
        this.handlers.sort(Comparator.comparingInt(h ->
                                                           h.handledTypes().stream()
                                                            .mapToInt(BiographyTemplateTypes::layerFor)
                                                            .min().orElse(Integer.MAX_VALUE)));
        this.preprocessor = preprocessor.isResolvable() ? preprocessor.get() : null;
    }

    BiographyImportRunner(List<BiographyHandler> handlers) {
        this(handlers, null);
    }

    BiographyImportRunner(List<BiographyHandler> handlers,
                          BiographyProfilePreprocessor preprocessor) {
        this.handlers = new ArrayList<>(handlers);
        this.handlers.sort(Comparator.comparingInt(h ->
                                                           h.handledTypes().stream()
                                                            .mapToInt(BiographyTemplateTypes::layerFor)
                                                            .min().orElse(Integer.MAX_VALUE)));
        this.preprocessor = preprocessor;
    }

    public void run(BiographyProfile profile, String agentId, String tenantId) {
        if (seededAgents.contains(agentId)) {
            throw new IllegalStateException("Agent " + agentId + " already has biographical data");
        }

        BiographyProfile effective = preprocessor != null
                                     ? preprocessor.preprocess(profile, agentId, tenantId)
                                     : profile;

        for (BiographyHandler handler : handlers) {
            handler.handle(effective, agentId, tenantId);
        }

        if (preprocessor != null) {
            preprocessor.afterImport(agentId, tenantId);
        }

        seededAgents.add(agentId);
    }
}
