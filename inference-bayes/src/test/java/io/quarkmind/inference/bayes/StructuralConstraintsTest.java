package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StructuralConstraintsTest {

    @Test
    void none_returnsEmptyConstraints() {
        var c = StructuralConstraints.none();
        assertTrue(c.requiredEdges().isEmpty());
        assertTrue(c.forbiddenEdges().isEmpty());
        assertEquals(Integer.MAX_VALUE, c.maxParents());
    }

    @Test
    void maxParents_returnsConstraintWithLimit() {
        var c = StructuralConstraints.maxParents(3);
        assertTrue(c.requiredEdges().isEmpty());
        assertTrue(c.forbiddenEdges().isEmpty());
        assertEquals(3, c.maxParents());
    }

    @Test
    void defensiveCopy_requiredEdges() {
        var edges = new java.util.HashSet<>(Set.of(
            new StructuralConstraints.Edge("A", "B")));
        var c = new StructuralConstraints(edges, Set.of(), Integer.MAX_VALUE);
        edges.add(new StructuralConstraints.Edge("C", "D"));
        assertEquals(1, c.requiredEdges().size());
    }

    @Test
    void defensiveCopy_forbiddenEdges() {
        var edges = new java.util.HashSet<>(Set.of(
            new StructuralConstraints.Edge("X", "Y")));
        var c = new StructuralConstraints(Set.of(), edges, Integer.MAX_VALUE);
        edges.add(new StructuralConstraints.Edge("W", "Z"));
        assertEquals(1, c.forbiddenEdges().size());
    }
}
