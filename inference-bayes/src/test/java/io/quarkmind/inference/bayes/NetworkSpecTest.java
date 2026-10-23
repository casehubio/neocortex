package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetworkSpecTest {

    @Test
    void specStatusHasFourValues() {
        assertThat(SpecStatus.values()).containsExactly(
            SpecStatus.PROPOSED, SpecStatus.ACCEPTED,
            SpecStatus.REJECTED, SpecStatus.LOCKED);
    }

    @Test
    void variableSpecConstruction() {
        var spec = new VariableSpec("Smoking", List.of("yes", "no"),
            "Whether patient smokes", SpecStatus.PROPOSED, null);
        assertThat(spec.name()).isEqualTo("Smoking");
        assertThat(spec.outcomes()).containsExactly("yes", "no");
        assertThat(spec.status()).isEqualTo(SpecStatus.PROPOSED);
        assertThat(spec.cptSpec()).isNull();
    }

    @Test
    void edgeSpecConstruction() {
        var edge = new EdgeSpec("Smoking", "Cancer", "Known risk factor", SpecStatus.PROPOSED);
        assertThat(edge.from()).isEqualTo("Smoking");
        assertThat(edge.to()).isEqualTo("Cancer");
        assertThat(edge.rationale()).isEqualTo("Known risk factor");
    }

    @Test
    void parentInfluenceConstruction() {
        var pi = new ParentInfluence("Smoking", 0.7);
        assertThat(pi.parentName()).isEqualTo("Smoking");
        assertThat(pi.strength()).isEqualTo(0.7);
    }

    @Test
    void directCptConstruction() {
        var cpt = new CptSpec.DirectCpt(new double[][]{{0.3, 0.7}});
        assertThat(cpt.values()).hasNumberOfRows(1);
        assertThat(cpt).isInstanceOf(CptSpec.class);
    }

    @Test
    void noisyOrConstruction() {
        var noisy = new CptSpec.NoisyOr(
            List.of(new ParentInfluence("Smoking", 0.7)),
            0.01);
        assertThat(noisy.parentInfluences()).hasSize(1);
        assertThat(noisy.leak()).isEqualTo(0.01);
    }

    @Test
    void uniformConstruction() {
        var uniform = new CptSpec.Uniform();
        assertThat(uniform).isInstanceOf(CptSpec.class);
    }


    @Test
    void networkSpecConstruction() {
        var smoking = new VariableSpec("Smoking", List.of("yes", "no"), "smoking habit", SpecStatus.PROPOSED, null);
        var spec    = new NetworkSpec("medical", List.of(smoking), List.of(), "medical domain");
        assertThat(spec.name()).isEqualTo("medical");
        assertThat(spec.variables()).hasSize(1);
        assertThat(spec.edges()).isEmpty();
    }

    @Test
    void withVariableAddsDeduplicated() {
        var spec    = new NetworkSpec("test", List.of(), List.of(), "desc");
        var v       = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var updated = spec.withVariable(v);
        assertThat(updated.variables()).hasSize(1);
        assertThatThrownBy(() -> updated.withVariable(v))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate");
    }

    @Test
    void withEdgeRejectsCycle() {
        var a = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var b = new VariableSpec("B", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var spec = new NetworkSpec("test", List.of(a, b), List.of(), "desc")
                           .withEdge(new EdgeSpec("A", "B", "causes", SpecStatus.PROPOSED));
        assertThatThrownBy(() ->
                                   spec.withEdge(new EdgeSpec("B", "A", "reverse", SpecStatus.PROPOSED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void withEdgeAllowsValidDAG() {
        var a = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var b = new VariableSpec("B", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var c = new VariableSpec("C", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var spec = new NetworkSpec("test", List.of(a, b, c), List.of(), "desc")
                           .withEdge(new EdgeSpec("A", "B", "", SpecStatus.PROPOSED))
                           .withEdge(new EdgeSpec("A", "C", "", SpecStatus.PROPOSED))
                           .withEdge(new EdgeSpec("B", "C", "", SpecStatus.PROPOSED));
        assertThat(spec.edges()).hasSize(3);
    }

    @Test
    void withVariableAcceptedChangesStatus() {
        var v = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var spec = new NetworkSpec("test", List.of(v), List.of(), "desc")
                           .withVariableAccepted("A");
        assertThat(spec.variables().get(0).status()).isEqualTo(SpecStatus.ACCEPTED);
    }

    @Test
    void withEdgeRejectedChangesStatus() {
        var a    = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var b    = new VariableSpec("B", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var edge = new EdgeSpec("A", "B", "cause", SpecStatus.PROPOSED);
        var spec = new NetworkSpec("test", List.of(a, b), List.of(edge), "desc")
                           .withEdgeRejected("A", "B");
        assertThat(spec.edges().get(0).status()).isEqualTo(SpecStatus.REJECTED);
    }

    @Test
    void withVariableRemovedRemovesVariable() {
        var v = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var spec = new NetworkSpec("test", List.of(v), List.of(), "desc")
                           .withVariableRemoved("A");
        assertThat(spec.variables()).isEmpty();
    }

    @Test
    void withVariableRemovedAlsoRemovesConnectedEdges() {
        var a    = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var b    = new VariableSpec("B", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var edge = new EdgeSpec("A", "B", "cause", SpecStatus.PROPOSED);
        var spec = new NetworkSpec("test", List.of(a, b), List.of(edge), "desc")
                           .withVariableRemoved("A");
        assertThat(spec.variables()).hasSize(1);
        assertThat(spec.edges()).isEmpty();
    }

    @Test
    void withEdgeRemovedRemovesEdge() {
        var a    = new VariableSpec("A", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var b    = new VariableSpec("B", List.of("y", "n"), "", SpecStatus.PROPOSED, null);
        var edge = new EdgeSpec("A", "B", "cause", SpecStatus.PROPOSED);
        var spec = new NetworkSpec("test", List.of(a, b), List.of(edge), "desc")
                           .withEdgeRemoved("A", "B");
        assertThat(spec.edges()).isEmpty();
    }

}
