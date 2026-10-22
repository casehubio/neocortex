package io.quarkmind.inference.bayes.benchmark;

import io.quarkmind.inference.bayes.BayesNetworkBuilder;
import io.quarkmind.inference.bayes.BayesVariable;
import io.quarkmind.inference.bayes.JunctionTree;
import io.quarkmind.inference.bayes.graph.Graph;

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
        if (n < 2) {throw new IllegalArgumentException("Chain must have at least 2 variables");}
        var b = BayesNetworkBuilder.create();
        b.variable(varName(0), "false", "true").prior(0.5, 0.5);
        for (int i = 1; i < n; i++) {
            b.variable(varName(i), "false", "true")
             .given(varName(i - 1))
             .cpt(0.7, 0.3, 0.4, 0.6);
        }
        return b.buildJunctionTree();
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
        var b = BayesNetworkBuilder.create();
        b.variable(varName(0), "false", "true").prior(0.5, 0.5);
        for (int i = 1; i <= nLeaves; i++) {
            b.variable(varName(i), "false", "true")
             .given(varName(0))
             .cpt(0.7, 0.3, 0.4, 0.6);
        }
        return b.buildJunctionTree();
    }

    /**
     * Builds a diamond/v-structure: A → C ← B, C → D.
     * For testing topologies where ordering matters.
     */
    public static Graph<BayesVariable> buildDiamondGraph() {
        return BayesNetworkBuilder.create()
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
    }

    /**
     * Builds a polytree that produces a junction tree with branching at depth > 1.
     * Structure: A→B, A→C, B→D, B→E, C→F, C→G.
     * After triangulation, the JT has nested branching — root clique has multiple
     * children, and those children have their own children.
     */
    public static JunctionTree buildPolyTree() {
        return BayesNetworkBuilder.create()
                                  .variable("A", "false", "true").prior(0.5, 0.5)
                                  .variable("B", "false", "true").given("A").cpt(0.7, 0.3, 0.4, 0.6)
                                  .variable("C", "false", "true").given("A").cpt(0.6, 0.4, 0.3, 0.7)
                                  .variable("D", "false", "true").given("B").cpt(0.8, 0.2, 0.2, 0.8)
                                  .variable("E", "false", "true").given("B").cpt(0.9, 0.1, 0.5, 0.5)
                                  .variable("F", "false", "true").given("C").cpt(0.7, 0.3, 0.4, 0.6)
                                  .variable("G", "false", "true").given("C").cpt(0.6, 0.4, 0.1, 0.9)
                                  .buildJunctionTree();
    }

    /**
     * Builds a chain of {@code n} ternary variables (3 states each).
     * var_0 has prior [0.5, 0.3, 0.2]. Each subsequent variable has CPT:
     * parent=0: [0.6, 0.3, 0.1], parent=1: [0.2, 0.5, 0.3], parent=2: [0.1, 0.2, 0.7].
     */
    public static JunctionTree buildTernaryChain(int n) {
        if (n < 2) {throw new IllegalArgumentException("Chain must have at least 2 variables");}
        var b = BayesNetworkBuilder.create();
        b.variable(varName(0), "low", "med", "high").prior(0.5, 0.3, 0.2);
        for (int i = 1; i < n; i++) {
            b.variable(varName(i), "low", "med", "high")
             .given(varName(i - 1))
             .cpt(0.6, 0.3, 0.1,
                  0.2, 0.5, 0.3,
                  0.1, 0.2, 0.7);
        }
        return b.buildJunctionTree();
    }


}
