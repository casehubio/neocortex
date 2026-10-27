package io.quarkmind.inference.bayes;

import java.util.Set;

public record StructuralConstraints(
    Set<Edge> requiredEdges,
    Set<Edge> forbiddenEdges,
    int maxParents
) {
    public record Edge(String from, String to) {}

    public StructuralConstraints {
        requiredEdges = Set.copyOf(requiredEdges);
        forbiddenEdges = Set.copyOf(forbiddenEdges);
    }

    public static StructuralConstraints none() {
        return new StructuralConstraints(Set.of(), Set.of(), Integer.MAX_VALUE);
    }

    public static StructuralConstraints maxParents(int max) {
        return new StructuralConstraints(Set.of(), Set.of(), max);
    }
}
