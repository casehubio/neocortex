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
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class GoalHandler implements BiographyHandler {

    private final MindMapStore store;

    @Inject
    public GoalHandler(MindMapStore store) { this.store = store; }

    @Override public Set<String> handledTypes() { return Set.of(BiographyTemplateTypes.GOAL); }

    @Override
    public void handle(BiographyProfile profile, String agentId, String tenantId) {
        String subgraphId = BiographyUtils.ensureSubgraph(store, SubgraphTypes.GOAL, tenantId);

        for (var entry : profile.goals()) {
            MindMapNode existing = store.resolveNode(entry.name(), subgraphId, tenantId);
            if (existing != null) continue;

            var props = new HashMap<String, String>();
            props.put("provenance", "biographical-import");
            props.put("template-ref", "goals.yaml#" + entry.id());
            if (entry.sourceRef() != null) props.put("source-ref", entry.sourceRef());
            if (entry.tier() != null) props.put("tier", entry.tier());
            if (entry.horizon() != null) props.put("horizon", entry.horizon());

            var input = NodeInput.of(entry.name(), subgraphId)
                .withConfidence(Confidence.stated(0.8, Instant.now()))
                .withProvenance("biographical-import")
                .withProperties(props);

            if (entry.pad() != null) {
                input = input.withPad(entry.pad().pleasure(), entry.pad().arousal(), entry.pad().dominance());
            }

            String goalId = store.addNode(input, tenantId);

            if (entry.subGoals() != null) {
                for (var sub : entry.subGoals()) {
                    var subProps = new HashMap<>(Map.of(
                        "provenance", "biographical-import",
                        "tier", sub.tier() != null ? sub.tier() : ""));
                    String subGoalId = store.addNode(
                        NodeInput.of(sub.description(), subgraphId)
                            .withConfidence(Confidence.stated(0.7, Instant.now()))
                            .withProvenance("biographical-import")
                            .withProperties(subProps),
                        tenantId);
                    store.addEdge(EdgeInput.of(goalId, subGoalId, "decomposes-into")
                        .withProvenance("biographical-import"), tenantId);
                }
            }

            if (entry.dependencies() != null) {
                for (var dep : entry.dependencies()) {
                    String targetId = resolveAcrossSubgraphs(dep.ref(), tenantId);
                    if (targetId != null) {
                        store.addEdge(EdgeInput.of(goalId, targetId, dep.edgeType())
                            .withProvenance("biographical-import"), tenantId);
                    }
                }
            }
        }
    }

    private String resolveAcrossSubgraphs(String name, String tenantId) {
        for (var sg : store.listSubgraphs(tenantId)) {
            MindMapNode node = store.resolveNode(name, sg.id(), tenantId);
            if (node != null) return node.id();
        }
        return null;
    }
}
