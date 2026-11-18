package io.casehub.neocortex.cognition.relationship;

import io.casehub.neocortex.cognitive.index.RelationalSchema;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.OverlayRef;
import org.jspecify.annotations.Nullable;

public class RelationalSchemaResolver {

    private final MindMapStore store;

    public RelationalSchemaResolver(MindMapStore store) {
        this.store = store;
    }

    public @Nullable RelationalSchema resolve(String observerAgentId, String targetAgentId, String tenantId) {
        var peopleSg = store.listSubgraphs(tenantId).stream()
                .filter(sg -> SubgraphTypes.PERSON.equals(sg.type()) || SubgraphTypes.PERSON.equals(sg.name()))
                .findFirst();
        if (peopleSg.isEmpty()) return null;

        var nodes = store.nodesIn(peopleSg.get().id(), tenantId);

        String targetNodeId = null;
        for (var node : nodes) {
            if (node.traits().contains("overlay")) continue;
            if (targetAgentId.equals(node.property("agentId").orElse(null))) {
                targetNodeId = node.id();
                break;
            }
        }
        if (targetNodeId == null) return null;

        for (var overlay : nodes) {
            if (!overlay.traits().contains("overlay")) continue;
            if (!observerAgentId.equals(overlay.property(OverlayRef.AGENT_ID).orElse(null))) continue;
            var sharedId = OverlayRef.sharedNodeId(overlay).orElse(null);
            if (targetNodeId.equals(sharedId)) {
                return RelationalSchema.fromProperties(overlay.properties());
            }
        }
        return null;
    }
}
