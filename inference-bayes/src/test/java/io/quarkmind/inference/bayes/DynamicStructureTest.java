package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class DynamicStructureTest {

    @Test
    void removeEdge_chain_marginalsMatchStandalone() {
        JunctionTree     jt       = BayesNetworkFixtures.buildChain(4);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        StructuralChangeResult result = instance.removeEdge("var_0", "var_1",
                                                            new double[][]{{0.5, 0.5}});

        assertThat(result.changeType())
                .isEqualTo(StructuralChangeResult.ChangeType.INCREMENTAL_EDGE_REMOVAL);

        instance.setLikelyhood("var_0", new double[]{0.3, 0.7});
        instance.globalUpdate();

        // Reference: chain var_1 → var_2 → var_3 (var_1 as root with prior 0.5)
        JunctionTree jtRef = BayesNetworkBuilder.create()
                                                .variable("var_1", "false", "true").prior(0.5, 0.5)
                                                .variable("var_2", "false", "true").given("var_1").cpt(0.7, 0.3, 0.4, 0.6)
                                                .variable("var_3", "false", "true").given("var_2").cpt(0.7, 0.3, 0.4, 0.6)
                                                .buildJunctionTree();
        BayesInstance<?> ref = new BayesInstance<>(jtRef);
        ref.globalUpdate();

        // var_1, var_2, var_3 should match the standalone chain
        // (var_0's evidence no longer flows through to var_1)
        for (String name : new String[]{"var_1", "var_2", "var_3"}) {
            double[] incDist = instance.marginalize(name).getDistribution();
            double[] refDist = ref.marginalize(name).getDistribution();
            for (int j = 0; j < incDist.length; j++) {
                assertThat(incDist[j]).as("%s[%d]", name, j)
                                      .isCloseTo(refDist[j], within(1e-10));
            }
        }
    }

    @Test
    void removeEdge_diamond_marginalsMatch() {
        // Build diamond: A → C, B → C, C → D
        JunctionTree jt = BayesNetworkBuilder.create()
                                             .variable("A", "false", "true").prior(0.6, 0.4)
                                             .variable("B", "false", "true").prior(0.5, 0.5)
                                             .variable("C", "false", "true").given("A", "B")
                                             .cpt(0.9, 0.1, 0.6, 0.4, 0.7, 0.3, 0.2, 0.8)
                                             .variable("D", "false", "true").given("C").cpt(0.8, 0.2, 0.3, 0.7)
                                             .buildJunctionTree();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        // Remove A → C, leaving only B → C
        instance.removeEdge("A", "C", new double[][]{{0.7, 0.3}, {0.4, 0.6}});

        instance.setLikelyhood("A", new double[]{0.3, 0.7});
        instance.setLikelyhood("B", new double[]{0.8, 0.2});
        instance.globalUpdate();

        // Reference: A→(nothing), B → C → D
        // A is still in the network but disconnected from C
        // Build ref with just B → C → D
        JunctionTree jtRef = BayesNetworkBuilder.create()
                                                .variable("B", "false", "true").prior(0.5, 0.5)
                                                .variable("C", "false", "true").given("B").cpt(0.7, 0.3, 0.4, 0.6)
                                                .variable("D", "false", "true").given("C").cpt(0.8, 0.2, 0.3, 0.7)
                                                .buildJunctionTree();
        BayesInstance<?> ref = new BayesInstance<>(jtRef);
        ref.setLikelyhood("B", new double[]{0.8, 0.2});
        ref.globalUpdate();

        for (String name : new String[]{"C", "D"}) {
            double[] incDist = instance.marginalize(name).getDistribution();
            double[] refDist = ref.marginalize(name).getDistribution();
            for (int j = 0; j < incDist.length; j++) {
                assertThat(incDist[j]).as("%s[%d]", name, j)
                                      .isCloseTo(refDist[j], within(1e-10));
            }
        }
    }

    @Test
    void removeEdge_withExistingEvidence_preserved() {
        JunctionTree     jt       = BayesNetworkFixtures.buildChain(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("var_2", new double[]{0.9, 0.1});
        instance.globalUpdate();

        // Remove var_0 → var_1
        instance.removeEdge("var_0", "var_1", new double[][]{{0.5, 0.5}});
        instance.globalUpdate();

        // var_2's evidence should still be in effect
        double[] dist = instance.marginalize("var_2").getDistribution();
        assertThat(dist[0]).as("var_2 evidence still applied").isGreaterThan(0.8);
    }

    @Test
    void removeEdge_multipleSequential() {
        JunctionTree     jt       = BayesNetworkFixtures.buildChain(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        // Remove both edges: all variables become independent
        instance.removeEdge("var_0", "var_1", new double[][]{{0.5, 0.5}});
        instance.removeEdge("var_1", "var_2", new double[][]{{0.5, 0.5}});
        instance.globalUpdate();

        for (int i = 0; i < 3; i++) {
            double[] dist = instance.marginalize(BayesNetworkFixtures.varName(i)).getDistribution();
            assertThat(dist[0]).as("var_%d prior", i).isCloseTo(0.5, within(1e-10));
            assertThat(dist[1]).as("var_%d prior", i).isCloseTo(0.5, within(1e-10));
        }
    }

    @Test
    void removeEdge_nonExistent_throws() {
        JunctionTree     jt       = BayesNetworkFixtures.buildChain(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.removeEdge("var_0", "var_2",
                                                     new double[][]{{0.5, 0.5}}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No edge");
    }

    @Test
    void removeEdge_frozenTree_throws() {
        JunctionTree jt = BayesNetworkFixtures.buildChain(3);
        jt.freeze();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.removeEdge("var_0", "var_1",
                                                     new double[][]{{0.5, 0.5}}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("frozen");
    }

    @Test
    void addEdge_createDiamond_marginalsMatchFullBuild() {
        // Start with: A → B, A → C, C → D (connected, no B→C edge)
        JunctionTree jt = BayesNetworkBuilder.create()
                                             .variable("A", "false", "true").prior(0.6, 0.4)
                                             .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                             .variable("C", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                             .variable("D", "false", "true").given("C").cpt(0.8, 0.2, 0.3, 0.7)
                                             .buildJunctionTree();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        // Add edge B → C, C now has parents A and B
        StructuralChangeResult result = instance.addEdge("B", "C",
                                                         new double[][]{{0.9, 0.1}, {0.6, 0.4}, {0.7, 0.3}, {0.2, 0.8}});

        assertThat(result.changeType()).isEqualTo(StructuralChangeResult.ChangeType.REBUILD);

        instance.setLikelyhood("A", new double[]{0.3, 0.7});
        instance.setLikelyhood("B", new double[]{0.8, 0.2});
        instance.globalUpdate();

        // Build the equivalent full network
        JunctionTree jtRef = BayesNetworkBuilder.create()
                                                .variable("A", "false", "true").prior(0.6, 0.4)
                                                .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                                .variable("C", "false", "true").given("A", "B")
                                                .cpt(0.9, 0.1, 0.6, 0.4, 0.7, 0.3, 0.2, 0.8)
                                                .variable("D", "false", "true").given("C").cpt(0.8, 0.2, 0.3, 0.7)
                                                .buildJunctionTree();
        BayesInstance<?> ref = new BayesInstance<>(jtRef);
        ref.setLikelyhood("A", new double[]{0.3, 0.7});
        ref.setLikelyhood("B", new double[]{0.8, 0.2});
        ref.globalUpdate();

        for (String name : new String[]{"A", "B", "C", "D"}) {
            double[] incDist = instance.marginalize(name).getDistribution();
            double[] refDist = ref.marginalize(name).getDistribution();
            for (int j = 0; j < incDist.length; j++) {
                assertThat(incDist[j]).as("%s[%d]", name, j)
                                      .isCloseTo(refDist[j], within(1e-10));
            }
        }
    }

    @Test
    void addEdge_cycleDetection_throws() {
        JunctionTree     jt       = BayesNetworkFixtures.buildChain(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        // var_0 → var_1 → var_2, try to add var_2 → var_0 (cycle)
        assertThatThrownBy(() -> instance.addEdge("var_2", "var_0",
                                                  new double[][]{{0.5, 0.5}, {0.5, 0.5}}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void addEdge_evidencePreservedAcrossRebuild() {
        // A → C, A → B (connected)
        JunctionTree jt = BayesNetworkBuilder.create()
                                             .variable("A", "false", "true").prior(0.5, 0.5)
                                             .variable("B", "false", "true").given("A").cpt(0.6, 0.4, 0.3, 0.7)
                                             .variable("C", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                             .buildJunctionTree();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("A", new double[]{0.3, 0.7});
        instance.globalUpdate();

        // Add B → C (evidence on A should survive the rebuild)
        StructuralChangeResult result = instance.addEdge("B", "C",
                                                         new double[][]{{0.9, 0.1}, {0.6, 0.4}, {0.7, 0.3}, {0.2, 0.8}});

        assertThat(result.evidenceRestored()).isEqualTo(1);
        assertThat(result.evidenceLost()).isEqualTo(0);

        instance.globalUpdate();

        double[] distA = instance.marginalize("A").getDistribution();
        assertThat(distA[0]).as("A evidence still applied").isLessThan(0.4);
    }

    @Test
    void addVariable_withParents_marginalsMatchFullBuild() {
        // Start: A → B (connected)
        JunctionTree jt = BayesNetworkBuilder.create()
                                             .variable("A", "false", "true").prior(0.6, 0.4)
                                             .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                             .buildJunctionTree();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        // Add C with parent A
        StructuralChangeResult result = instance.addVariable("C",
                                                             new String[]{"false", "true"},
                                                             new double[][]{{0.8, 0.2}, {0.3, 0.7}},
                                                             java.util.List.of("A"));

        assertThat(result.changeType()).isEqualTo(StructuralChangeResult.ChangeType.REBUILD);

        instance.setLikelyhood("A", new double[]{0.3, 0.7});
        instance.globalUpdate();

        // Reference
        JunctionTree jtRef = BayesNetworkBuilder.create()
                                                .variable("A", "false", "true").prior(0.6, 0.4)
                                                .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                                .variable("C", "false", "true").given("A").cpt(0.8, 0.2, 0.3, 0.7)
                                                .buildJunctionTree();
        BayesInstance<?> ref = new BayesInstance<>(jtRef);
        ref.setLikelyhood("A", new double[]{0.3, 0.7});
        ref.globalUpdate();

        for (String name : new String[]{"A", "B", "C"}) {
            double[] incDist = instance.marginalize(name).getDistribution();
            double[] refDist = ref.marginalize(name).getDistribution();
            for (int j = 0; j < incDist.length; j++) {
                assertThat(incDist[j]).as("%s[%d]", name, j)
                                      .isCloseTo(refDist[j], within(1e-10));
            }
        }
    }

    @Test
    void addVariable_noParents_withExistingParent() {
        // Adding a leaf variable via addVariable (no explicit parents, but connected via parent)
        JunctionTree jt = BayesNetworkBuilder.create()
                                             .variable("A", "false", "true").prior(0.5, 0.5)
                                             .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                             .buildJunctionTree();
        BayesInstance<?> instance = new BayesInstance<>(jt);

        // Add C as child of B
        instance.addVariable("C", new String[]{"false", "true"},
                             new double[][]{{0.6, 0.4}, {0.3, 0.7}}, java.util.List.of("B"));

        instance.globalUpdate();

        // C's marginal should be influenced by B's prior
        double[] dist = instance.marginalize("C").getDistribution();
        assertThat(dist).isNotNull();
        assertThat(instance.getVariables()).hasSize(3);
    }

    @Test
    void addVariable_duplicateName_throws() {
        JunctionTree     jt       = BayesNetworkFixtures.buildChain(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.addVariable("var_1",
                                                      new String[]{"a", "b"}, new double[][]{{0.5, 0.5}}, java.util.List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void removeVariable_leaf_marginalsCorrect() {
        JunctionTree     jt       = BayesNetworkFixtures.buildStar(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        instance.setLikelyhood("var_0", new double[]{0.3, 0.7});
        instance.setLikelyhood("var_1", new double[]{0.8, 0.2});
        instance.globalUpdate();

        StructuralChangeResult result = instance.removeVariable("var_1");

        assertThat(result.evidenceRestored()).isEqualTo(1); // var_0
        assertThat(result.evidenceLost()).isEqualTo(1);     // var_1 removed
        assertThat(instance.getVariables()).doesNotContainKey("var_1");
    }

    @Test
    void removeVariable_hasChildren_throws() {
        JunctionTree     jt       = BayesNetworkFixtures.buildChain(3);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.removeVariable("var_1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("children");
    }

    @Test
    void removeVariable_nonExistent_throws() {
        JunctionTree     jt       = BayesNetworkFixtures.buildChain(2);
        BayesInstance<?> instance = new BayesInstance<>(jt);

        assertThatThrownBy(() -> instance.removeVariable("ghost"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }


}
