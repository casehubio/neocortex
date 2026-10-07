package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.cognitive.Confidence;
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
public class CulturalContextHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public CulturalContextHandler(MindMapStore store) { this.store = store; }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.CULTURAL_CONTEXT); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        String subgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.CULTURAL, tenantId);
        for (var entry : profile.culturalContexts()) {

            MindMapNode existing = store.resolveNode(entry.description(), subgraphId, tenantId);
            if (existing != null) continue;

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");
            props.put("template-ref", "cultural-context.yaml#" + entry.id());
            if (entry.sourceRef() != null) props.put("source-ref", entry.sourceRef());
            if (entry.contextProperties() != null) props.putAll(entry.contextProperties());

            if (entry.norms() != null) {
                for (int i = 0; i < entry.norms().size(); i++) {
                    var norm = entry.norms().get(i);
                    props.put("norm-" + i + "-name", norm.name());
                    if (norm.strength() != null) props.put("norm-" + i + "-strength", norm.strength().toString());
                    if (norm.description() != null) props.put("norm-" + i + "-description", norm.description());
                }
                props.put("norm-count", String.valueOf(entry.norms().size()));
            }

            store.addNode(
                NodeInput.of(entry.description(), subgraphId)
                    .withConfidence(Confidence.stated(0.8, Instant.now()))
                    .withProvenance("biographical-import")
                    .withProperties(props),
                tenantId);
        }
    }
}
