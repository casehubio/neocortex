package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;

import java.time.Instant;
import java.util.List;

public final class AssociationProcessor {

    private AssociationProcessor() {}

    public static void process(List<Association> associations, String sourceNodeId,
                               MindMapStore store, String tenantId) {
        if (associations == null) return;
        for (var assoc : associations) {
            String targetId = resolveAcrossSubgraphs(assoc.entityRef(), store, tenantId);
            if (targetId == null) continue;
            var edge = EdgeInput.of(sourceNodeId, targetId, assoc.edgeType())
                .withProvenance("biographical-import");
            if (assoc.confidence() != null) {
                edge = edge.withConfidence(
                    new Confidence(ConfidenceOrigin.STATED, assoc.confidence(), Instant.now()));
            }
            store.addEdge(edge, tenantId);
        }
    }

    public static String resolveAcrossSubgraphs(String name, MindMapStore store, String tenantId) {
        for (var sg : store.listSubgraphs(tenantId)) {
            MindMapNode node = store.resolveNode(name, sg.id(), tenantId);
            if (node != null) return node.id();
        }
        return null;
    }
}
