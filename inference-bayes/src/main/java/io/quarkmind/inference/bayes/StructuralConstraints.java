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

    public static StructuralConstraints fromSpec(NetworkSpec spec) {
        Set<Edge> required  = new java.util.HashSet<>();
        Set<Edge> forbidden = new java.util.HashSet<>();
        for (EdgeSpec e : spec.edges()) {
            if (e.status() == SpecStatus.LOCKED) {
                required.add(new Edge(e.from(), e.to()));
            } else if (e.status() == SpecStatus.REJECTED) {
                forbidden.add(new Edge(e.from(), e.to()));
            }
        }
        return new StructuralConstraints(required, forbidden, Integer.MAX_VALUE);
    }

}
