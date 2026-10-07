package io.casehub.neocortex.mindmap;

import java.util.Optional;

public final class SubThoughtRef {

    public static final String SCHEME = "sub-thought";

    public static NodeRef of(String memoryId, int subThoughtIndex) {
        return new NodeRef(SCHEME, memoryId, String.valueOf(subThoughtIndex));
    }

    public static Optional<String> memoryId(MindMapNode node) {
        return node.refs().stream()
            .filter(r -> SCHEME.equals(r.scheme()))
            .map(NodeRef::id)
            .findFirst();
    }

    public static Optional<Integer> subThoughtIndex(MindMapNode node) {
        return node.refs().stream()
            .filter(r -> SCHEME.equals(r.scheme()))
            .map(r -> Integer.parseInt(r.qualifier()))
            .findFirst();
    }

    private SubThoughtRef() {}
}
