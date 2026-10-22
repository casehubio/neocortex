package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import io.quarkmind.inference.bayes.graph.Graph;
import io.quarkmind.inference.bayes.graph.GraphNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BayesNetworkBuilderTest {

    @Test
    void twoVariableChain_matchesHandWired() {
        BayesInstance<?> builderInstance = BayesNetworkBuilder.create()
            .variable("var_0", "false", "true").prior(0.5, 0.5)
            .variable("var_1", "false", "true").given("var_0").cpt(0.7, 0.3, 0.4, 0.6)
            .buildInstance();

        builderInstance.setLikelyhood("var_1", new double[]{1.0, 0.0});
        builderInstance.globalUpdate();
        double[] builderDist = builderInstance.marginalize("var_0").getDistribution();

        JunctionTree refJt = BayesNetworkFixtures.buildChain(2);
        BayesInstance<?> refInstance = new BayesInstance<>(refJt);
        refInstance.setLikelyhood("var_1", new double[]{1.0, 0.0});
        refInstance.globalUpdate();
        double[] refDist = refInstance.marginalize("var_0").getDistribution();

        assertArrayEquals(refDist, builderDist, 1e-10);
    }

    @Test
    void diamondNetwork_matchesHandWired() {
        BayesNetwork builderNet = BayesNetworkBuilder.create()
            .variable("A", "false", "true").prior(0.5, 0.5)
            .variable("B", "false", "true").prior(0.5, 0.5)
            .variable("C", "false", "true")
                .given("A", "B")
                .cpt(0.9, 0.1,
                     0.6, 0.4,
                     0.7, 0.3,
                     0.2, 0.8)
            .variable("D", "false", "true")
                .given("C")
                .cpt(0.8, 0.2, 0.3, 0.7)
            .build();

        JunctionTree builderJt = new JunctionTreeBuilder(builderNet).build();
        BayesInstance<?> builderInst = new BayesInstance<>(builderJt);

        Graph<BayesVariable> refGraph = BayesNetworkFixtures.buildDiamondGraph();
        JunctionTree refJt = new JunctionTreeBuilder(refGraph).build();
        BayesInstance<?> refInst = new BayesInstance<>(refJt);

        builderInst.setLikelyhood("A", new double[]{1.0, 0.0});
        builderInst.globalUpdate();
        refInst.setLikelyhood("A", new double[]{1.0, 0.0});
        refInst.globalUpdate();

        for (String varName : new String[]{"A", "B", "C", "D"}) {
            double[] expected = refInst.marginalize(varName).getDistribution();
            double[] actual = builderInst.marginalize(varName).getDistribution();
            assertArrayEquals(expected, actual, 1e-10, "Mismatch for " + varName);
        }
    }

    @Test
    void loopConstructedChain_matchesFixture() {
        int n = 8;
        var b = BayesNetworkBuilder.create();
        b.variable("var_0", "false", "true").prior(0.5, 0.5);
        for (int i = 1; i < n; i++) {
            b.variable("var_" + i, "false", "true")
             .given("var_" + (i - 1))
             .cpt(0.7, 0.3, 0.4, 0.6);
        }
        BayesInstance<?> builderInst = b.buildInstance();

        BayesInstance<?> refInst = new BayesInstance<>(BayesNetworkFixtures.buildChain(n));

        builderInst.setLikelyhood("var_" + (n - 1), new double[]{1.0, 0.0});
        builderInst.globalUpdate();
        refInst.setLikelyhood("var_" + (n - 1), new double[]{1.0, 0.0});
        refInst.globalUpdate();

        for (int i = 0; i < n; i++) {
            double[] expected = refInst.marginalize("var_" + i).getDistribution();
            double[] actual = builderInst.marginalize("var_" + i).getDistribution();
            assertArrayEquals(expected, actual, 1e-10, "Mismatch for var_" + i);
        }
    }

    @Test
    void buildInstance_matchesManualConstruction() {
        BayesInstance<?> convenience = BayesNetworkBuilder.create()
            .variable("A", "yes", "no").prior(0.6, 0.4)
            .variable("B", "yes", "no").given("A").cpt(0.9, 0.1, 0.2, 0.8)
            .buildInstance();

        BayesInstance<?> manual = new BayesInstance<>(
            new JunctionTreeBuilder(
                BayesNetworkBuilder.create()
                    .variable("A", "yes", "no").prior(0.6, 0.4)
                    .variable("B", "yes", "no").given("A").cpt(0.9, 0.1, 0.2, 0.8)
                    .build()
            ).build());

        convenience.setLikelyhood("B", new double[]{1.0, 0.0});
        convenience.globalUpdate();
        manual.setLikelyhood("B", new double[]{1.0, 0.0});
        manual.globalUpdate();

        assertArrayEquals(
            manual.marginalize("A").getDistribution(),
            convenience.marginalize("A").getDistribution(),
            1e-10);
    }

    @Test
    void cptDoubleArray_matchesFlatVarargs() {
        BayesInstance<?> flat = BayesNetworkBuilder.create()
            .variable("A", "yes", "no").prior(0.5, 0.5)
            .variable("B", "yes", "no").given("A").cpt(0.9, 0.1, 0.2, 0.8)
            .buildInstance();

        BayesInstance<?> array = BayesNetworkBuilder.create()
            .variable("A", "yes", "no").prior(0.5, 0.5)
            .variable("B", "yes", "no").given("A").cpt(new double[][]{{0.9, 0.1}, {0.2, 0.8}})
            .buildInstance();

        flat.setLikelyhood("B", new double[]{1.0, 0.0});
        flat.globalUpdate();
        array.setLikelyhood("B", new double[]{1.0, 0.0});
        array.globalUpdate();

        assertArrayEquals(
            flat.marginalize("A").getDistribution(),
            array.marginalize("A").getDistribution(),
            1e-10);
    }

    @Test
    void descriptionMetadata_roundTrips() {
        BayesNetwork net = BayesNetworkBuilder.create()
            .variable("A", "yes", "no").prior(0.5, 0.5).description("Root variable")
            .build();
        BayesVariable<?> found = null;
        for (GraphNode<BayesVariable> node : net) {
            if (node.getContent().getName().equals("A")) {
                found = node.getContent();
                break;
            }
        }
        assertNotNull(found);
        assertEquals("Root variable", found.getDescription());
    }

    @Test
    void descriptionMetadata_nullByDefault() {
        BayesNetwork net = BayesNetworkBuilder.create()
            .variable("A", "yes", "no").prior(0.5, 0.5)
            .build();
        for (GraphNode<BayesVariable> node : net) {
            assertNull(node.getContent().getDescription());
        }
    }

    @Test
    void duplicateVariableName_throws() {
        assertThrows(IllegalStateException.class, () ->
            BayesNetworkBuilder.create()
                .variable("A", "yes", "no").prior(0.5, 0.5)
                .variable("A", "yes", "no").prior(0.3, 0.7)
                .build());
    }

    @Test
    void parentNotDeclared_throws() {
        assertThrows(IllegalStateException.class, () ->
            BayesNetworkBuilder.create()
                .variable("B", "yes", "no").given("A").cpt(0.5, 0.5, 0.3, 0.7)
                .build());
    }

    @Test
    void wrongCptDimensions_throws() {
        assertThrows(IllegalStateException.class, () ->
            BayesNetworkBuilder.create()
                .variable("A", "yes", "no").prior(0.5, 0.5)
                .variable("B", "yes", "no").given("A").cpt(0.5, 0.5, 0.3)
                .build());
    }

    @Test
    void wrongPriorDimensions_throws() {
        assertThrows(IllegalStateException.class, () ->
            BayesNetworkBuilder.create()
                .variable("A", "yes", "no").prior(0.5, 0.3, 0.2)
                .build());
    }

    @Test
    void emptyBuilder_throws() {
        assertThrows(IllegalStateException.class, () ->
            BayesNetworkBuilder.create().build());
    }

    @Test
    void incompleteVariable_inLoopPath_throws() {
        var b = BayesNetworkBuilder.create();
        b.variable("A", "yes", "no");
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    void networkName_propagatesToBayesNetwork() {
        BayesNetwork net = BayesNetworkBuilder.create("TestNet")
            .variable("A", "yes", "no").prior(0.5, 0.5)
            .build();
        assertEquals("TestNet", net.getName());
    }

    @Test
    void decisionNode_createsUniformPrior() {
        InfluenceDiagram diagram = BayesNetworkBuilder.create()
            .variable("weather", "sunny", "rainy").prior(0.7, 0.3)
            .decision("umbrella", "take", "leave")
            .utility("comfort").given("weather", "umbrella").utilities(
                8, 4,
                2, 7)
            .buildInfluenceDiagram();

        assertNotNull(diagram);
        assertTrue(diagram.decisionNodeNames().contains("umbrella"));
        assertEquals(1, diagram.utilityNodes().size());

        BayesInstance<?> instance = new BayesInstance<>(
            new JunctionTreeBuilder(diagram.network()).build());
        instance.globalUpdate();
        double[] umbrellaMargin = instance.marginalize("umbrella").getDistribution();
        assertEquals(0.5, umbrellaMargin[0], 0.01);
        assertEquals(0.5, umbrellaMargin[1], 0.01);
    }

    @Test
    void influenceDiagram_missingDecision_throws() {
        assertThrows(IllegalStateException.class, () ->
            BayesNetworkBuilder.create()
                .variable("A", "yes", "no").prior(0.5, 0.5)
                .utility("U").given("A").utilities(10, 5)
                .buildInfluenceDiagram());
    }

    @Test
    void influenceDiagram_missingUtility_throws() {
        assertThrows(IllegalStateException.class, () ->
            BayesNetworkBuilder.create()
                .decision("D", "yes", "no")
                .buildInfluenceDiagram());
    }

    @Test
    void influenceDiagram_utilityUndeclaredParent_throws() {
        assertThrows(IllegalStateException.class, () ->
            BayesNetworkBuilder.create()
                .decision("D", "yes", "no")
                .utility("U").given("D", "missing").utilities(1, 2, 3, 4)
                .buildInfluenceDiagram());
    }

    @Test
    void influenceDiagram_utilityWrongDimension_throws() {
        assertThrows(IllegalStateException.class, () ->
            BayesNetworkBuilder.create()
                .variable("A", "yes", "no").prior(0.5, 0.5)
                .decision("D", "yes", "no")
                .utility("U").given("A", "D").utilities(1, 2, 3)
                .buildInfluenceDiagram());
    }

    @Test
    void existingBuild_ignoresDecisionAndUtility() {
        BayesNetwork net = BayesNetworkBuilder.create()
            .variable("A", "yes", "no").prior(0.5, 0.5)
            .decision("D", "yes", "no")
            .utility("U").given("A", "D").utilities(10, 5, 2, 8)
            .build();

        int count = 0;
        for (var node : net) { count++; }
        assertEquals(2, count);
    }

    @Test
    void ternaryChain_matchesFixture() {
        int n = 4;
        var b = BayesNetworkBuilder.create();
        b.variable("var_0", "low", "med", "high").prior(0.5, 0.3, 0.2);
        for (int i = 1; i < n; i++) {
            b.variable("var_" + i, "low", "med", "high")
             .given("var_" + (i - 1))
             .cpt(0.6, 0.3, 0.1,
                  0.2, 0.5, 0.3,
                  0.1, 0.2, 0.7);
        }
        BayesInstance<?> builderInst = b.buildInstance();

        BayesInstance<?> refInst = new BayesInstance<>(BayesNetworkFixtures.buildTernaryChain(n));

        builderInst.setLikelyhood("var_" + (n - 1), new double[]{1.0, 0.0, 0.0});
        builderInst.globalUpdate();
        refInst.setLikelyhood("var_" + (n - 1), new double[]{1.0, 0.0, 0.0});
        refInst.globalUpdate();

        for (int i = 0; i < n; i++) {
            double[] expected = refInst.marginalize("var_" + i).getDistribution();
            double[] actual = builderInst.marginalize("var_" + i).getDistribution();
            assertArrayEquals(expected, actual, 1e-10, "Mismatch for var_" + i);
        }
    }
}
