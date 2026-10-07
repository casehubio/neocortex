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
public class ProjectHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public ProjectHandler(MindMapStore store) { this.store = store; }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.PROJECT); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        String subgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PROJECT, tenantId);
        for (var entry : profile.projects()) {

            MindMapNode existing = store.resolveNode(entry.name(), subgraphId, tenantId);
            if (existing != null) continue;

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");
            props.put("template-ref", "projects.yaml#" + entry.id());
            if (entry.sourceRef() != null) props.put("source-ref", entry.sourceRef());
            if (entry.description() != null) props.put("description", entry.description());
            if (entry.properties() != null) props.putAll(entry.properties());

            store.addNode(
                NodeInput.of(entry.name(), subgraphId)
                    .withConfidence(Confidence.stated(0.8, Instant.now()))
                    .withProvenance("biographical-import")
                    .withProperties(props),
                tenantId);
        }
    }
}
