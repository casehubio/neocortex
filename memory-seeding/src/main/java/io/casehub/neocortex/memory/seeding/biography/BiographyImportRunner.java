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

    private final List<BiographyHandler> handlers;
    private final Set<String> seededAgents = Collections.synchronizedSet(new HashSet<>());

    @Inject
    public BiographyImportRunner(Instance<BiographyHandler> handlers) {
        this.handlers = new ArrayList<>();
        handlers.forEach(this.handlers::add);
        this.handlers.sort(Comparator.comparingInt(h ->
            h.handledTypes().stream()
                .mapToInt(BiographyTemplateTypes::layerFor)
                .min().orElse(Integer.MAX_VALUE)));
    }

    BiographyImportRunner(List<BiographyHandler> handlers) {
        this.handlers = new ArrayList<>(handlers);
        this.handlers.sort(Comparator.comparingInt(h ->
            h.handledTypes().stream()
                .mapToInt(BiographyTemplateTypes::layerFor)
                .min().orElse(Integer.MAX_VALUE)));
    }

    public void run(BiographyProfile profile, String agentId, String tenantId) {
        if (!seededAgents.add(agentId)) {
            throw new IllegalStateException("Agent " + agentId + " already has biographical data");
        }

        for (BiographyHandler handler : handlers) {
            handler.handle(profile, agentId, tenantId);
        }
    }
}
