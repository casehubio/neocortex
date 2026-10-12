package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeUpdate;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Set;

@ApplicationScoped
public class EntityEnrichmentHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public EntityEnrichmentHandler(MindMapStore store) { this.store = store; }

    EntityEnrichmentHandler(MindMapStore store, @SuppressWarnings("unused") boolean testMarker) {
        this.store = store;
    }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.ENTITY_ENRICHMENT); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        for (var entry : profile.entityEnrichments()) {
            String nodeId = AssociationProcessor.resolveAcrossSubgraphs(entry.entityRef(), store, tenantId);
            if (nodeId == null) continue;

            var props = new HashMap<String, String>();

            if (entry.driveBaselines() != null) {
                for (var e : entry.driveBaselines().entrySet()) {
                    props.put("drive-baseline-" + e.getKey(), String.valueOf(e.getValue().baseline()));
                    if (e.getValue().justification() != null) {
                        props.put("drive-baseline-" + e.getKey() + "-justification", e.getValue().justification());
                    }
                }
            }

            if (entry.capsPatterns() != null) {
                props.put("caps-pattern-count", String.valueOf(entry.capsPatterns().size()));
                for (int i = 0; i < entry.capsPatterns().size(); i++) {
                    var p = entry.capsPatterns().get(i);
                    props.put("caps-pattern-" + i + "-pattern", p.pattern());
                    if (p.frequency() != null) {
                        props.put("caps-pattern-" + i + "-frequency", String.valueOf(p.frequency()));
                    }
                }
            }

            if (entry.behaviouralAttractors() != null) {
                props.put("behavioural-attractor-count", String.valueOf(entry.behaviouralAttractors().size()));
                for (int i = 0; i < entry.behaviouralAttractors().size(); i++) {
                    var a = entry.behaviouralAttractors().get(i);
                    props.put("behavioural-attractor-" + i + "-name", a.name());
                    if (a.description() != null) {
                        props.put("behavioural-attractor-" + i + "-description", a.description());
                    }
                }
            }

            store.updateNode(nodeId, NodeUpdate.empty().withPropertiesToSet(props), tenantId);
        }
    }
}
