package io.casehub.neocortex.mindmap;

import java.util.Objects;

public record SubgraphInput(String name, String type, String rootNodeId) {

    public SubgraphInput {
        Objects.requireNonNull(name, "name required");
        Objects.requireNonNull(type, "type required");
        type = type.strip().toLowerCase();
        if (type.isEmpty()) throw new IllegalArgumentException("type must not be blank");
    }
}
