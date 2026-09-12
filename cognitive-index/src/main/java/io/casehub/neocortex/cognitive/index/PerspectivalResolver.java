package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.OverlayRef;
import io.casehub.platform.api.identity.PrincipalId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PerspectivalResolver {

    private final MindMapStore mindMapStore;

    PerspectivalResolver(MindMapStore mindMapStore) {
        this.mindMapStore = mindMapStore;
    }

    List<MindMapNode> resolve(List<MindMapNode> sharedNodes,
                              PrincipalId principal, String tenantId) {
        if (sharedNodes.isEmpty()) {return sharedNodes;}
        if (mindMapStore == null) {return sharedNodes;}

        Map<String, MindMapNode> overlayMap = loadOverlays(tenantId, principal);

        return sharedNodes.stream()
                          .map(shared -> {
                              MindMapNode overlay = overlayMap.get(shared.id());
                              return overlay != null ? PerspectivalMerge.merge(shared, overlay) : shared;
                          })
                          .toList();
    }

    List<MindMapNode> loadAllOverlays(String tenantId) {
        MindMapQuery query = MindMapQuery.of(tenantId, 1000)
                                         .withTraits(Set.of("overlay"));
        return mindMapStore.search(query);
    }

    private Map<String, MindMapNode> loadOverlays(String tenantId, PrincipalId principal) {
        List<MindMapNode> allOverlays = loadAllOverlays(tenantId);

        Map<String, MindMapNode> map = new HashMap<>();
        for (MindMapNode node : allOverlays) {
            if (principal.value().equals(node.properties().get(OverlayRef.AGENT_ID))) {
                OverlayRef.sharedNodeId(node).ifPresent(sharedId -> map.put(sharedId, node));
            }
        }
        return map;
    }
}
