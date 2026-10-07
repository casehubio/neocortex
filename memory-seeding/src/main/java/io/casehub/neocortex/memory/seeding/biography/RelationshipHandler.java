package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.ExperienceEvents;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class RelationshipHandler implements BiographyHandler {

    private final MindMapStore store;
    private final CaseMemoryStore memoryStore;

    @Inject
    public RelationshipHandler(MindMapStore store, CaseMemoryStore memoryStore) {
        this.store = store;
        this.memoryStore = memoryStore;
    }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.RELATIONSHIP); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        for (var entry : profile.relationships()) {
            String subgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.PERSON, tenantId);

            MindMapNode existing = store.resolveNode(entry.name(), subgraphId, tenantId);
            if (existing != null) continue;

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");
            props.put("template-ref", "relationships.yaml#" + entry.id());
            if (entry.sourceRef() != null) props.put("source-ref", entry.sourceRef());
            if (entry.properties() != null) props.putAll(entry.properties());

            if (entry.bdi() != null) {
                if (entry.bdi().beliefs() != null) props.put("bdi-beliefs", entry.bdi().beliefs());
                if (entry.bdi().desires() != null) props.put("bdi-desires", entry.bdi().desires());
                if (entry.bdi().intentions() != null) props.put("bdi-intentions", entry.bdi().intentions());
            }

            if (entry.dynamics() != null) {
                if (entry.dynamics().trust() != null) props.put("trust", entry.dynamics().trust().toString());
                if (entry.dynamics().conflictMode() != null) props.put("conflict-mode", entry.dynamics().conflictMode());
            }

            var traits = entry.traits() != null ? new HashSet<>(entry.traits()) : new HashSet<String>();

            var input = NodeInput.of(entry.name(), subgraphId)
                .withConfidence(Confidence.stated(0.8, Instant.now()))
                .withProvenance("biographical-import")
                .withProperties(props)
                .withTraits(traits);

            if (entry.affect() != null && entry.affect().pad() != null) {
                var pad = entry.affect().pad();
                input = input.withPad(pad.pleasure(), pad.arousal(), pad.dominance());
            }

            store.addNode(input, tenantId);

            if (entry.bdi() != null) {
                var attrs = new HashMap<String, String>();
                attrs.put("provenance", "biographical-import");
                attrs.put("domain-type", "relationship-bdi");
                if (entry.bdi().beliefs() != null) attrs.put("bdi-beliefs", entry.bdi().beliefs());
                if (entry.bdi().desires() != null) attrs.put("bdi-desires", entry.bdi().desires());
                if (entry.bdi().intentions() != null) attrs.put("bdi-intentions", entry.bdi().intentions());

                memoryStore.store(MemoryInput.of(
                    Subject.of("agent", agentId), ExperienceEvents.DOMAIN, tenantId,
                    "Relationship with " + entry.name())
                    .withAttributes(attrs));
            }
        }
    }
}
