package io.casehub.neocortex.memory.seeding.biography;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphInput;

final class BiographyUtils {

    static String ensureSubgraph(MindMapStore store, String type, String tenantId) {
        return store.listSubgraphs(tenantId).stream()
            .filter(s -> type.equals(s.type()))
            .map(s -> s.id())
            .findFirst()
            .orElseGet(() -> store.createSubgraph(
                new SubgraphInput(type, type, null), tenantId));
    }

    private BiographyUtils() {}
}
