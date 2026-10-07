package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashMap;
import java.util.Set;

@ApplicationScoped
public class ActivityHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public ActivityHandler(MindMapStore store) { this.store = store; }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.ACTIVITY); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        String subgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.ACTIVITY, tenantId);
        for (var entry : profile.activities()) {

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");
            props.put("template-ref", "activities.yaml#" + entry.id());
            if (entry.sourceRef() != null) props.put("source-ref", entry.sourceRef());
            if (entry.activityType() != null) props.put("activityType", entry.activityType());
            if (entry.date() != null) props.put("date", entry.date());
            if (entry.notes() != null) props.put("notes", entry.notes());

            String activityId = store.addNode(
                NodeInput.of(entry.name(), subgraphId)
                    .withConfidence(Confidence.stated(0.8, Instant.now()))
                    .withProvenance("biographical-import")
                    .withProperties(props),
                tenantId);

            if (entry.placeRef() != null) {
                String placeSubgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PLACE, tenantId);
                String placeId = resolveOrCreate(entry.placeRef(), placeSubgraphId, tenantId);
                store.addEdge(EdgeInput.of(activityId, placeId, "at")
                    .withProvenance("biographical-import"), tenantId);
            }

            if (entry.participants() != null) {
                String personSubgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, tenantId);
                for (var participant : entry.participants()) {
                    String personId = resolveOrCreate(participant.entityRef(), personSubgraphId, tenantId);
                    store.addEdge(EdgeInput.of(personId, activityId, "participated")
                        .withProvenance("biographical-import"), tenantId);
                }
            }
        }
    }

    private String resolveOrCreate(String name, String subgraphId, String tenantId) {
        MindMapNode existing = store.resolveNode(name, subgraphId, tenantId);
        if (existing != null) return existing.id();
        return store.addNode(NodeInput.of(name, subgraphId).withProvenance("biographical-import"), tenantId);
    }
}
