package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.benchmark.BayesNetworkFixtures;
import io.quarkmind.inference.bayes.graph.Graph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static io.quarkmind.inference.bayes.IncrementalUpdateTest.assertSameMarginals;

/**
 * Refs #622 — min-fill ordering (already default) and brute-force optimal ordering.
 */
public class EliminationOrderingTest {

    @Test
    void optimalOrdering_matchesMinFillOnChain() {
        Graph<BayesVariable> graph = new BayesNetwork();
        buildChainGraph(graph, 6);

        JunctionTree minFill = new JunctionTreeBuilder(graph).build();
        JunctionTree optimal = new JunctionTreeBuilder(graph).buildOptimal();

        int minFillMax = maxCliqueSize(minFill);
        int optimalMax = maxCliqueSize(optimal);

        assertThat(optimalMax).isLessThanOrEqualTo(minFillMax);
    }

    @Test
    void optimalOrdering_matchesMinFillOnStar() {
        Graph<BayesVariable> graph = new BayesNetwork();
        buildStarGraph(graph, 5);

        JunctionTree minFill = new JunctionTreeBuilder(graph).build();
        JunctionTree optimal = new JunctionTreeBuilder(graph).buildOptimal();

        int minFillMax = maxCliqueSize(minFill);
        int optimalMax = maxCliqueSize(optimal);

        assertThat(optimalMax).isLessThanOrEqualTo(minFillMax);
    }

    @Test
    void optimalOrdering_matchesMinFillOnDiamond() {
        Graph<BayesVariable> graph = BayesNetworkFixtures.buildDiamondGraph();

        JunctionTree minFill = new JunctionTreeBuilder(graph).build();
        JunctionTree optimal = new JunctionTreeBuilder(graph).buildOptimal();

        int minFillMax = maxCliqueSize(minFill);
        int optimalMax = maxCliqueSize(optimal);

        assertThat(optimalMax).isLessThanOrEqualTo(minFillMax);
    }

    @Test
    void optimalOrdering_producesCorrectMarginals() {
        JunctionTree minFillJt = BayesNetworkFixtures.buildChain(6);
        Graph<BayesVariable> graph = new BayesNetwork();
        buildChainGraph(graph, 6);
        JunctionTree optimalJt = new JunctionTreeBuilder(graph).buildOptimal();

        BayesInstance<?> minFillInst = new BayesInstance<>(minFillJt);
        BayesInstance<?> optimalInst = new BayesInstance<>(optimalJt);

        minFillInst.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});
        optimalInst.setLikelyhood(BayesNetworkFixtures.varName(0), new double[]{0.8, 0.2});

        minFillInst.globalUpdate();
        optimalInst.globalUpdate();

        assertSameMarginals(minFillInst, optimalInst, 6);
    }

    @Test
    void optimalOrdering_rejectsLargeNetworks() {
        Graph<BayesVariable> graph = new BayesNetwork();
        buildChainGraph(graph, 11);

        JunctionTreeBuilder builder = new JunctionTreeBuilder(graph);
        assertThatThrownBy(builder::buildOptimal)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10");
    }

    private static int maxCliqueSize(JunctionTree tree) {
        int max = 0;
        for (JunctionTreeClique clique : tree.getJunctionTreeNodes()) {
            int size = clique.getValues().size();
            if (size > max) max = size;
        }
        return max;
    }

    @SuppressWarnings("unchecked")
    private static void buildChainGraph(Graph<BayesVariable> graph, int n) {
        var nodes = new io.quarkmind.inference.bayes.graph.GraphNode[n];
        for (int i = 0; i < n; i++) nodes[i] = graph.addNode();

        nodes[0].setContent(new BayesVariable<>("var_0", 0,
                new String[]{"false", "true"}, new double[][]{{0.5, 0.5}}));
        for (int i = 1; i < n; i++) {
            nodes[i].setContent(new BayesVariable<>("var_" + i, i,
                    new String[]{"false", "true"}, new double[][]{{0.7, 0.3}, {0.4, 0.6}}));
            var edge = new io.quarkmind.inference.bayes.graph.impl.EdgeImpl();
            edge.setOutGraphNode(nodes[i - 1]);
            edge.setInGraphNode(nodes[i]);
        }
    }

    @SuppressWarnings("unchecked")
    private static void buildStarGraph(Graph<BayesVariable> graph, int nLeaves) {
        int n = nLeaves + 1;
        var nodes = new io.quarkmind.inference.bayes.graph.GraphNode[n];
        for (int i = 0; i < n; i++) nodes[i] = graph.addNode();

        nodes[0].setContent(new BayesVariable<>("var_0", 0,
                new String[]{"false", "true"}, new double[][]{{0.5, 0.5}}));
        for (int i = 1; i < n; i++) {
            nodes[i].setContent(new BayesVariable<>("var_" + i, i,
                    new String[]{"false", "true"}, new double[][]{{0.7, 0.3}, {0.4, 0.6}}));
            var edge = new io.quarkmind.inference.bayes.graph.impl.EdgeImpl();
            edge.setOutGraphNode(nodes[0]);
            edge.setInGraphNode(nodes[i]);
        }
    }
}
