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
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class OrganisationHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public OrganisationHandler(MindMapStore store) { this.store = store; }

    OrganisationHandler(MindMapStore store, @SuppressWarnings("unused") boolean testMarker) {
        this.store = store;
    }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.ORGANISATION); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        String subgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.ORGANISATION, tenantId);

        for (var entry : profile.organisations()) {
            MindMapNode existing = store.resolveNode(entry.name(), subgraphId, tenantId);
            if (existing != null) continue;

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");
            props.put("template-ref", "organisations.yaml#" + entry.id());
            if (entry.sourceRef() != null) props.put("source-ref", entry.sourceRef());
            if (entry.properties() != null) props.putAll(entry.properties());
            if (entry.validFrom() != null) props.put("valid-from", entry.validFrom());
            if (entry.validUntil() != null) props.put("valid-until", entry.validUntil());

            var origin = PlaceHandler.resolveOrigin(entry.confidenceOrigin());
            var traits = entry.traits() != null ? new HashSet<>(entry.traits()) : new HashSet<String>();

            var input = NodeInput.of(entry.name(), subgraphId)
                .withConfidence(new Confidence(origin, 0.8, Instant.now()))
                .withProvenance("biographical-import")
                .withProperties(props)
                .withTraits(traits);

            if (entry.pad() != null) {
                input = input.withPad(entry.pad().pleasure(), entry.pad().arousal(), entry.pad().dominance());
            }

            String nodeId = store.addNode(input, tenantId);
            AssociationProcessor.process(entry.associations(), nodeId, store, tenantId);
        }
    }
}
