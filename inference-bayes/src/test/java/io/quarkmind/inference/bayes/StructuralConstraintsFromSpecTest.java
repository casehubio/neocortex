package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StructuralConstraintsFromSpecTest {

    @Test
    void fromSpec_lockedEdges_becomeRequired() {
        var spec = new NetworkSpec("test",
            List.of(
                new VariableSpec("A", List.of("t", "f"), null, SpecStatus.PROPOSED, null),
                new VariableSpec("B", List.of("t", "f"), null, SpecStatus.PROPOSED, null)),
            List.of(new EdgeSpec("A", "B", "locked edge", SpecStatus.LOCKED)),
            null);

        StructuralConstraints result = StructuralConstraints.fromSpec(spec);

        assertTrue(result.requiredEdges().contains(new StructuralConstraints.Edge("A", "B")));
        assertTrue(result.forbiddenEdges().isEmpty());
    }

    @Test
    void fromSpec_rejectedEdges_becomeForbidden() {
        var spec = new NetworkSpec("test",
            List.of(
                new VariableSpec("A", List.of("t", "f"), null, SpecStatus.PROPOSED, null),
                new VariableSpec("B", List.of("t", "f"), null, SpecStatus.PROPOSED, null)),
            List.of(new EdgeSpec("A", "B", "rejected", SpecStatus.REJECTED)),
            null);

        StructuralConstraints result = StructuralConstraints.fromSpec(spec);

        assertTrue(result.forbiddenEdges().contains(new StructuralConstraints.Edge("A", "B")));
        assertTrue(result.requiredEdges().isEmpty());
    }

    @Test
    void fromSpec_proposedAndAccepted_neitherRequiredNorForbidden() {
        var spec = new NetworkSpec("test",
            List.of(
                new VariableSpec("A", List.of("t", "f"), null, SpecStatus.PROPOSED, null),
                new VariableSpec("B", List.of("t", "f"), null, SpecStatus.PROPOSED, null),
                new VariableSpec("C", List.of("t", "f"), null, SpecStatus.PROPOSED, null)),
            List.of(
                new EdgeSpec("A", "B", "proposed", SpecStatus.PROPOSED),
                new EdgeSpec("B", "C", "accepted", SpecStatus.ACCEPTED)),
            null);

        StructuralConstraints result = StructuralConstraints.fromSpec(spec);

        assertTrue(result.requiredEdges().isEmpty());
        assertTrue(result.forbiddenEdges().isEmpty());
    }

    @Test
    void fromSpec_emptySpec_returnsNone() {
        var spec = new NetworkSpec("test", List.of(), List.of(), null);

        StructuralConstraints result = StructuralConstraints.fromSpec(spec);

        assertTrue(result.requiredEdges().isEmpty());
        assertTrue(result.forbiddenEdges().isEmpty());
        assertEquals(Integer.MAX_VALUE, result.maxParents());
    }

    @Test
    void fromSpec_mixedStatuses() {
        var spec = new NetworkSpec("test",
            List.of(
                new VariableSpec("A", List.of("t", "f"), null, SpecStatus.PROPOSED, null),
                new VariableSpec("B", List.of("t", "f"), null, SpecStatus.PROPOSED, null),
                new VariableSpec("C", List.of("t", "f"), null, SpecStatus.PROPOSED, null),
                new VariableSpec("D", List.of("t", "f"), null, SpecStatus.PROPOSED, null)),
            List.of(
                new EdgeSpec("A", "B", "locked", SpecStatus.LOCKED),
                new EdgeSpec("B", "C", "proposed", SpecStatus.PROPOSED),
                new EdgeSpec("C", "D", "rejected", SpecStatus.REJECTED)),
            null);

        StructuralConstraints result = StructuralConstraints.fromSpec(spec);

        assertEquals(1, result.requiredEdges().size());
        assertEquals(1, result.forbiddenEdges().size());
        assertTrue(result.requiredEdges().contains(new StructuralConstraints.Edge("A", "B")));
        assertTrue(result.forbiddenEdges().contains(new StructuralConstraints.Edge("C", "D")));
    }
}
