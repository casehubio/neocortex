package io.quarkmind.inference.bayes.benchmark;

import io.quarkmind.inference.bayes.BayesNetwork;
import io.quarkmind.inference.bayes.BayesVariable;
import io.quarkmind.inference.bayes.JunctionTree;
import io.quarkmind.inference.bayes.JunctionTreeBuilder;
import io.quarkmind.inference.bayes.graph.Graph;
import io.quarkmind.inference.bayes.graph.Graph;
import io.quarkmind.inference.bayes.graph.GraphNode;
import io.quarkmind.inference.bayes.graph.impl.EdgeImpl;

/**
 * Builds reproducible synthetic Bayesian networks for benchmarking.
 */
public class BayesNetworkFixtures {

    /**
     * Builds a chain of {@code n} binary variables: var_0 → var_1 → ... → var_{n-1}.
     * var_0 has prior [0.5, 0.5]. Each subsequent variable has its predecessor as parent
     * with CPT [[0.7, 0.3], [0.4, 0.6]].
     */
    public static JunctionTree buildChain(int n) {
        if (n < 2) throw new IllegalArgumentException("Chain must have at least 2 variables");

        Graph<BayesVariable> graph = new BayesNetwork();

        @SuppressWarnings("unchecked")
        GraphNode<BayesVariable>[] nodes = new GraphNode[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = graph.addNode();
        }

        // Root variable: no parents
        nodes[0].setContent(new BayesVariable<String>(
                varName(0), nodes[0].getId(),
                new String[]{"false", "true"},
                new double[][]{{0.5, 0.5}}));

        // Remaining variables: one parent each
        for (int i = 1; i < n; i++) {
            nodes[i].setContent(new BayesVariable<String>(
                    varName(i), nodes[i].getId(),
                    new String[]{"false", "true"},
                    new double[][]{{0.7, 0.3}, {0.4, 0.6}}));

            EdgeImpl edge = new EdgeImpl();
            edge.setOutGraphNode(nodes[i - 1]);
            edge.setInGraphNode(nodes[i]);
        }

        return new JunctionTreeBuilder(graph).build();
    }

    public static String varName(int index) {
        return "var_" + index;
    }

    /**
     * Builds a star topology: one central hub with {@code nLeaves} children.
     * Hub has prior [0.5, 0.5]. Each leaf has the hub as parent with CPT [[0.7, 0.3], [0.4, 0.6]].
     */
    public static JunctionTree buildStar(int nLeaves) {
        if (nLeaves < 1) {throw new IllegalArgumentException("Star must have at least 1 leaf");}
        int                  n     = nLeaves + 1;
        Graph<BayesVariable> graph = new BayesNetwork();

        @SuppressWarnings("unchecked")
        GraphNode<BayesVariable>[] nodes = new GraphNode[n];
        for (int i = 0; i < n; i++) {
            nodes[i] = graph.addNode();
        }

        nodes[0].setContent(new BayesVariable<String>(
                varName(0), nodes[0].getId(),
                new String[]{"false", "true"},
                new double[][]{{0.5, 0.5}}));

        for (int i = 1; i < n; i++) {
            nodes[i].setContent(new BayesVariable<String>(
                    varName(i), nodes[i].getId(),
                    new String[]{"false", "true"},
                    new double[][]{{0.7, 0.3}, {0.4, 0.6}}));

            EdgeImpl edge = new EdgeImpl();
            edge.setOutGraphNode(nodes[0]);
            edge.setInGraphNode(nodes[i]);
        }

        return new JunctionTreeBuilder(graph).build();
    }

    /**
     * Builds a diamond/v-structure: A → C ← B, C → D.
     * For testing topologies where ordering matters.
     */
    public static Graph<BayesVariable> buildDiamondGraph() {
        Graph<BayesVariable> graph = new BayesNetwork();

        @SuppressWarnings("unchecked")
        GraphNode<BayesVariable>[] nodes = new GraphNode[4];
        for (int i = 0; i < 4; i++) {
            nodes[i] = graph.addNode();
        }

        nodes[0].setContent(new BayesVariable<String>("A", 0,
                                                      new String[]{"false", "true"}, new double[][]{{0.5, 0.5}}));
        nodes[1].setContent(new BayesVariable<String>("B", 1,
                                                      new String[]{"false", "true"}, new double[][]{{0.5, 0.5}}));
        nodes[2].setContent(new BayesVariable<String>("C", 2,
                                                      new String[]{"false", "true"},
                                                      new double[][]{{0.9, 0.1}, {0.6, 0.4}, {0.7, 0.3}, {0.2, 0.8}}));
        nodes[3].setContent(new BayesVariable<String>("D", 3,
                                                      new String[]{"false", "true"},
                                                      new double[][]{{0.8, 0.2}, {0.3, 0.7}}));

        EdgeImpl e1 = new EdgeImpl();
        e1.setOutGraphNode(nodes[0]);
        e1.setInGraphNode(nodes[2]);
        EdgeImpl e2 = new EdgeImpl();
        e2.setOutGraphNode(nodes[1]);
        e2.setInGraphNode(nodes[2]);
        EdgeImpl e3 = new EdgeImpl();
        e3.setOutGraphNode(nodes[2]);
        e3.setInGraphNode(nodes[3]);

        return graph;
    }

}
