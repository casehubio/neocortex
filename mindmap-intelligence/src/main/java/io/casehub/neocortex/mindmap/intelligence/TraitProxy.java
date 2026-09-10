package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.mindmap.MindMapNode;

public final class TraitProxy {

    private TraitProxy() {}

    @Deprecated(forRemoval = true)
    public static <T> T as(MindMapNode node, Class<T> traitInterface) {
        return node.as(traitInterface);
    }
}
