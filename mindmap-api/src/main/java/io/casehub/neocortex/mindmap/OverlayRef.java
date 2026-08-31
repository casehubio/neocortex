package io.casehub.neocortex.mindmap;

import java.util.Optional;

public final class OverlayRef {

    public static final String SCHEME = "overlay";
    public static final String AGENT_ID = "agentId";


    private OverlayRef() {}

    public static NodeRef of(String sharedNodeId) {
        return new NodeRef(SCHEME, sharedNodeId, null);
    }

    public static Optional<String> sharedNodeId(MindMapNode node) {
        return node.refs().stream()
            .filter(r -> SCHEME.equals(r.scheme()))
            .map(NodeRef::id)
            .findFirst();
    }
}
