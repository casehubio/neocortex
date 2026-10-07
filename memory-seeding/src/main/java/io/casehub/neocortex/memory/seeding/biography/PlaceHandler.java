package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class PlaceHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public PlaceHandler(MindMapStore store) { this.store = store; }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.PLACE); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        String subgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PLACE, tenantId);
        for (var entry : profile.places()) {

            MindMapNode existing = store.resolveNode(entry.name(), subgraphId, tenantId);
            if (existing != null) continue;

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");
            props.put("template-ref", "places.yaml#" + entry.id());
            if (entry.sourceRef() != null) props.put("source-ref", entry.sourceRef());
            if (entry.properties() != null) props.putAll(entry.properties());

            var origin = resolveOrigin(entry.confidenceOrigin());
            var input = NodeInput.of(entry.name(), subgraphId)
                .withConfidence(new Confidence(origin, 0.8, Instant.now()))
                .withProvenance("biographical-import")
                .withProperties(props);

            if (entry.pad() != null) {
                input = input.withPad(entry.pad().pleasure(), entry.pad().arousal(), entry.pad().dominance());
            }

            String nodeId = store.addNode(input, tenantId);

            if (entry.associations() != null) {
                for (var assoc : entry.associations()) {
                    String targetId = resolveOrCreate(assoc.entityRef(), subgraphId, tenantId);
                    store.addEdge(EdgeInput.of(nodeId, targetId, assoc.edgeType())
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

    static ConfidenceOrigin resolveOrigin(String origin) {
        if (origin == null) return ConfidenceOrigin.STATED;
        try { return ConfidenceOrigin.valueOf(origin); }
        catch (IllegalArgumentException e) { return ConfidenceOrigin.STATED; }
    }
}
