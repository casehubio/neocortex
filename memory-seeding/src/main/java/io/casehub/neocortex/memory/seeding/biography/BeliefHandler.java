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
public class BeliefHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public BeliefHandler(MindMapStore store) { this.store = store; }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.BELIEF); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        String subgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.COGNITIVE, tenantId);

        for (var entry : profile.beliefs()) {
            MindMapNode existing = store.resolveNode(entry.description(), subgraphId, tenantId);
            if (existing != null) continue;

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");
            props.put("template-ref", "beliefs.yaml#" + entry.id());
            if (entry.sourceRef() != null) props.put("source-ref", entry.sourceRef());
            if (entry.cognitiveKind() != null) props.put("cognitiveKind", entry.cognitiveKind());
            if (entry.properties() != null) props.putAll(entry.properties());

            double confidence = entry.confidence() != null ? entry.confidence() : 0.7;

            store.addNode(
                NodeInput.of(entry.description(), subgraphId)
                    .withConfidence(Confidence.inferred(confidence, Instant.now()))
                    .withProvenance("biographical-import")
                    .withProperties(props),
                tenantId);
        }
    }
}
