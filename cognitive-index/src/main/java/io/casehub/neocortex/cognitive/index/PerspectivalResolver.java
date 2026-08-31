package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.OverlayRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class PerspectivalResolver {

    private final MindMapStore mindMapStore;

    @Inject
    public PerspectivalResolver(Instance<MindMapStore> mindMapStore) {
        this.mindMapStore = mindMapStore.isUnsatisfied() ? null : mindMapStore.get();
    }

    PerspectivalResolver(MindMapStore mindMapStore) {
        this.mindMapStore = mindMapStore;
    }

    public List<MindMapNode> resolve(List<MindMapNode> sharedNodes,
                                     String agentId, String tenantId) {
        if (sharedNodes.isEmpty()) {return sharedNodes;}
        if (mindMapStore == null) {return sharedNodes;}

        Map<String, MindMapNode> overlayMap = loadOverlays(tenantId, agentId);

        return sharedNodes.stream()
                          .map(shared -> {
                              MindMapNode overlay = overlayMap.get(shared.id());
                              return overlay != null ? PerspectivalMerge.merge(shared, overlay) : shared;
                          })
                          .toList();
    }

    private Map<String, MindMapNode> loadOverlays(String tenantId, String agentId) {
        MindMapQuery query = MindMapQuery.of(tenantId, 1000)
                                         .withTraits(Set.of("overlay"));
        List<MindMapNode> overlayNodes = mindMapStore.search(query);

        Map<String, MindMapNode> map = new HashMap<>();
        for (MindMapNode node : overlayNodes) {
            if (agentId.equals(node.properties().get(OverlayRef.AGENT_ID))) {
                OverlayRef.sharedNodeId(node).ifPresent(sharedId -> map.put(sharedId, node));
            }
        }
        return map;
    }
}
