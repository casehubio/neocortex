package io.quarkmind.inference.bayes;

import java.util.Map;

public record NetworkSlot(
    String name,
    JunctionTree defaultTree,
    Map<String, JunctionTree> variants
) {
    public NetworkSlot {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Slot name must not be null or empty");
        }
        if (defaultTree == null) {
            throw new IllegalArgumentException("Default tree must not be null");
        }
        variants = variants == null ? Map.of() : Map.copyOf(variants);
    }

    public NetworkSlot(String name, JunctionTree defaultTree) {
        this(name, defaultTree, Map.of());
    }
}
