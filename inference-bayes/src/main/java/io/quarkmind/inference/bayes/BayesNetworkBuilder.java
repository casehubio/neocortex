package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BayesNetworkBuilder {

    private final String name;
    private final List<VariableDefinition> variables = new ArrayList<>();
    private final Map<String, VariableDefinition> variablesByName = new LinkedHashMap<>();
    private final List<UtilityDefinition> utilities = new ArrayList<>();
    private final Set<String> decisionNames = new LinkedHashSet<>();
    private VariableDefinition current;

    private BayesNetworkBuilder(String name) {
        this.name = name;
    }

    public static BayesNetworkBuilder create() {
        return new BayesNetworkBuilder(null);
    }

    public static BayesNetworkBuilder create(String name) {
        return new BayesNetworkBuilder(name);
    }

    public VariableDef variable(String name, String... outcomes) {
        finalizeCurrent();
        if (variablesByName.containsKey(name)) {
            throw new IllegalStateException("Duplicate variable name: '" + name + "'");
        }
        if (outcomes.length < 2) {
            throw new IllegalStateException("Variable '" + name + "' must have at least 2 outcomes");
        }
        current = new VariableDefinition(name, outcomes);
        return new VariableDef(this);
    }

    public DecisionDef decision(String name, String... outcomes) {
        finalizeCurrent();
        if (variablesByName.containsKey(name)) {
            throw new IllegalStateException("Duplicate variable name: '" + name + "'");
        }
        if (outcomes.length < 2) {
            throw new IllegalStateException("Decision '" + name + "' must have at least 2 outcomes");
        }
        double[] uniform = new double[outcomes.length];
        Arrays.fill(uniform, 1.0 / outcomes.length);
        current = new VariableDefinition(name, outcomes);
        current.prior = uniform;
        decisionNames.add(name);
        return new DecisionDef(this);
    }

    public UtilityDef utility(String name) {
        finalizeCurrent();
        return new UtilityDef(this, name);
    }

    public BayesNetwork build() {
        return doBuild();
    }

    public InfluenceDiagram buildInfluenceDiagram() {
        return doBuildInfluenceDiagram();
    }

    public JunctionTree buildJunctionTree() {
        return doBuildJunctionTree();
    }

    public BayesInstance<?> buildInstance() {
        return doBuildInstance(true);
    }

    public BayesInstance<?> buildInstance(boolean enableFastRetract) {
        return doBuildInstance(enableFastRetract);
    }

    private void finalizeCurrent() {
        if (current != null) {
            variables.add(current);
            variablesByName.put(current.name, current);
            current = null;
        }
    }

    BayesNetwork doBuild() {
        finalizeCurrent();
        validate();

        BayesNetwork graph = name != null ? new BayesNetwork(name) : new BayesNetwork();
        Map<String, GraphNode<BayesVariable>> nodes = new LinkedHashMap<>();

        for (VariableDefinition def : variables) {
            GraphNode<BayesVariable> node = graph.addNode();
            double[][] cpt = def.parents != null ? def.cpt : new double[][]{def.prior};
            String[] given = def.parents != null ? def.parents : new String[0];
            BayesVariable<String> var = new BayesVariable<>(def.name, node.getId(), def.outcomes, cpt, given);
            if (def.description != null) {
                var.setDescription(def.description);
            }
            node.setContent(var);
            nodes.put(def.name, node);
        }

        for (VariableDefinition def : variables) {
            if (def.parents != null) {
                GraphNode<BayesVariable> childNode = nodes.get(def.name);
                for (String parentName : def.parents) {
                    GraphNode<BayesVariable> parentNode = nodes.get(parentName);
                    graph.addEdge(parentNode, childNode);
                }
            }
        }

        return graph;
    }

    JunctionTree doBuildJunctionTree() {
        return new JunctionTreeBuilder(doBuild()).build();
    }

    BayesInstance<?> doBuildInstance(boolean enableFastRetract) {
        return new BayesInstance<>(doBuildJunctionTree(), enableFastRetract);
    }

    InfluenceDiagram doBuildInfluenceDiagram() {
        finalizeCurrent();
        validateInfluenceDiagram();
        ensureUtilityParentConnectivity();
        BayesNetwork network = doBuild();
        List<UtilityNode> utilityNodes = new ArrayList<>();
        for (UtilityDefinition def : utilities) {
            utilityNodes.add(new UtilityNode(def.name, def.parents, def.utilities));
        }
        return new InfluenceDiagram(network, Set.copyOf(decisionNames), List.copyOf(utilityNodes));
    }

    private void validateInfluenceDiagram() {
        validate();
        if (decisionNames.isEmpty()) {
            throw new IllegalStateException("Influence diagram requires at least one decision node");
        }
        if (utilities.isEmpty()) {
            throw new IllegalStateException("Influence diagram requires at least one utility node");
        }
        for (UtilityDefinition util : utilities) {
            int expectedSize = 1;
            for (String parentName : util.parents) {
                VariableDefinition parentDef = variablesByName.get(parentName);
                if (parentDef == null) {
                    throw new IllegalStateException("Utility node '" + util.name
                        + "' references undeclared parent '" + parentName + "'");
                }
                expectedSize *= parentDef.outcomes.length;
            }
            if (util.utilities.length != expectedSize) {
                throw new IllegalStateException("Utility node '" + util.name
                    + "': utilities array has " + util.utilities.length
                    + " values but expected " + expectedSize
                    + " (product of parent outcome counts)");
            }
        }
    }

    private void ensureUtilityParentConnectivity() {
        for (UtilityDefinition util : utilities) {
            List<String> chanceParents = new ArrayList<>();
            List<String> decisionParents = new ArrayList<>();
            for (String parent : util.parents) {
                if (decisionNames.contains(parent)) {
                    decisionParents.add(parent);
                } else {
                    chanceParents.add(parent);
                }
            }
            if (chanceParents.isEmpty()) continue;
            for (String decName : decisionParents) {
                VariableDefinition decDef = variablesByName.get(decName);
                if (decDef == null) continue;
                List<String> newParents = new ArrayList<>();
                if (decDef.parents != null) {
                    newParents.addAll(Arrays.asList(decDef.parents));
                }
                boolean added = false;
                for (String chance : chanceParents) {
                    if (!newParents.contains(chance)) {
                        newParents.add(chance);
                        added = true;
                    }
                }
                if (added) {
                    decDef.parents = newParents.toArray(new String[0]);
                    int parentCombinations = 1;
                    for (String pName : decDef.parents) {
                        parentCombinations *= variablesByName.get(pName).outcomes.length;
                    }
                    double[][] cpt = new double[parentCombinations][decDef.outcomes.length];
                    double uniformVal = 1.0 / decDef.outcomes.length;
                    for (double[] row : cpt) {
                        Arrays.fill(row, uniformVal);
                    }
                    decDef.cpt = cpt;
                    decDef.prior = null;
                }
            }
        }
    }

    private void validate() {
        if (variables.isEmpty()) {
            throw new IllegalStateException("Cannot build an empty network — at least one variable is required");
        }

        for (VariableDefinition def : variables) {
            if (def.prior == null && def.cpt == null) {
                throw new IllegalStateException("Variable '" + def.name + "' is incomplete — must have either .prior() or .given().cpt()");
            }

            if (def.prior != null && def.prior.length != def.outcomes.length) {
                throw new IllegalStateException("Variable '" + def.name + "': prior distribution length " + def.prior.length + " does not match outcome count " + def.outcomes.length);
            }

            if (def.parents != null) {
                for (String parentName : def.parents) {
                    if (!variablesByName.containsKey(parentName)) {
                        throw new IllegalStateException("Variable '" + def.name + "' references undeclared parent '" + parentName + "'");
                    }
                }

                int parentCombinations = 1;
                for (String parentName : def.parents) {
                    parentCombinations *= variablesByName.get(parentName).outcomes.length;
                }
                int expectedRows = parentCombinations;
                int expectedCols = def.outcomes.length;
                if (def.cpt.length != expectedRows) {
                    throw new IllegalStateException("Variable '" + def.name + "': CPT has " + def.cpt.length + " rows but expected " + expectedRows + " (" + parentCombinations + " parent combinations)");
                }
                for (int r = 0; r < def.cpt.length; r++) {
                    if (def.cpt[r].length != expectedCols) {
                        throw new IllegalStateException("Variable '" + def.name + "': CPT row " + r + " has " + def.cpt[r].length + " values but expected " + expectedCols + " (outcome count)");
                    }
                }
            }
        }

        validateAcyclic();
    }

    private void validateAcyclic() {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (VariableDefinition def : variables) {
            inDegree.put(def.name, 0);
            children.put(def.name, new ArrayList<>());
        }
        for (VariableDefinition def : variables) {
            if (def.parents != null) {
                inDegree.put(def.name, def.parents.length);
                for (String parent : def.parents) {
                    children.get(parent).add(def.name);
                }
            }
        }

        java.util.Queue<String> queue = new java.util.ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String node = queue.poll();
            visited++;
            for (String child : children.get(node)) {
                int newDegree = inDegree.get(child) - 1;
                inDegree.put(child, newDegree);
                if (newDegree == 0) {
                    queue.add(child);
                }
            }
        }
        if (visited != variables.size()) {
            throw new IllegalStateException("Network contains a cycle — not a valid DAG");
        }
    }

    public static class VariableDef {
        private final BayesNetworkBuilder builder;

        VariableDef(BayesNetworkBuilder builder) {
            this.builder = builder;
        }

        public CompleteDef prior(double... distribution) {
            builder.current.prior = distribution;
            return new CompleteDef(builder);
        }

        public ParentedDef given(String... parents) {
            builder.current.parents = parents;
            return new ParentedDef(builder);
        }
    }

    public static class ParentedDef {
        private final BayesNetworkBuilder builder;

        ParentedDef(BayesNetworkBuilder builder) {
            this.builder = builder;
        }

        public CompleteDef cpt(double... values) {
            VariableDefinition def = builder.current;
            int parentCombinations = 1;
            for (String parentName : def.parents) {
                VariableDefinition parentDef = builder.variablesByName.get(parentName);
                if (parentDef != null) {
                    parentCombinations *= parentDef.outcomes.length;
                }
            }
            int childOutcomes = def.outcomes.length;
            int expectedLength = parentCombinations * childOutcomes;
            if (values.length != expectedLength) {
                throw new IllegalStateException("Variable '" + def.name + "': CPT requires " + expectedLength + " values (" + parentCombinations + " parent combinations × " + childOutcomes + " outcomes), got " + values.length);
            }
            double[][] cpt = new double[parentCombinations][childOutcomes];
            for (int row = 0; row < parentCombinations; row++) {
                System.arraycopy(values, row * childOutcomes, cpt[row], 0, childOutcomes);
            }
            def.cpt = cpt;
            return new CompleteDef(builder);
        }

        public CompleteDef cpt(double[][] values) {
            builder.current.cpt = values;
            return new CompleteDef(builder);
        }
    }

    public static class CompleteDef {
        private final BayesNetworkBuilder builder;

        CompleteDef(BayesNetworkBuilder builder) {
            this.builder = builder;
        }

        public CompleteDef description(String description) {
            builder.current.description = description;
            return this;
        }

        public VariableDef variable(String name, String... outcomes) {
            return builder.variable(name, outcomes);
        }

        public DecisionDef decision(String name, String... outcomes) {
            return builder.decision(name, outcomes);
        }

        public UtilityDef utility(String name) {
            return builder.utility(name);
        }

        public BayesNetwork build() {
            return builder.doBuild();
        }

        public JunctionTree buildJunctionTree() {
            return builder.doBuildJunctionTree();
        }

        public BayesInstance<?> buildInstance() {
            return builder.doBuildInstance(true);
        }

        public BayesInstance<?> buildInstance(boolean enableFastRetract) {
            return builder.doBuildInstance(enableFastRetract);
        }

        public InfluenceDiagram buildInfluenceDiagram() {
            return builder.doBuildInfluenceDiagram();
        }
    }

    public static class DecisionDef {
        private final BayesNetworkBuilder builder;

        DecisionDef(BayesNetworkBuilder builder) {
            this.builder = builder;
        }

        public DecisionDef description(String description) {
            builder.current.description = description;
            return this;
        }

        public VariableDef variable(String name, String... outcomes) {
            return builder.variable(name, outcomes);
        }

        public DecisionDef decision(String name, String... outcomes) {
            return builder.decision(name, outcomes);
        }

        public UtilityDef utility(String name) {
            return builder.utility(name);
        }

        public BayesNetwork build() {
            return builder.doBuild();
        }

        public JunctionTree buildJunctionTree() {
            return builder.doBuildJunctionTree();
        }

        public BayesInstance<?> buildInstance() {
            return builder.doBuildInstance(true);
        }

        public BayesInstance<?> buildInstance(boolean enableFastRetract) {
            return builder.doBuildInstance(enableFastRetract);
        }

        public InfluenceDiagram buildInfluenceDiagram() {
            return builder.doBuildInfluenceDiagram();
        }
    }

    public static class UtilityDef {
        private final BayesNetworkBuilder builder;
        private final String name;

        UtilityDef(BayesNetworkBuilder builder, String name) {
            this.builder = builder;
            this.name = name;
        }

        public UtilityParentedDef given(String... parents) {
            UtilityDefinition def = new UtilityDefinition(name);
            def.parents = parents;
            return new UtilityParentedDef(builder, def);
        }
    }

    public static class UtilityParentedDef {
        private final BayesNetworkBuilder builder;
        private final UtilityDefinition def;

        UtilityParentedDef(BayesNetworkBuilder builder, UtilityDefinition def) {
            this.builder = builder;
            this.def = def;
        }

        public CompleteDef utilities(double... values) {
            def.utilities = values;
            builder.utilities.add(def);
            return new CompleteDef(builder);
        }
    }

    private static class VariableDefinition {
        final String name;
        final String[] outcomes;
        String[] parents;
        double[] prior;
        double[][] cpt;
        String description;

        VariableDefinition(String name, String[] outcomes) {
            this.name = name;
            this.outcomes = outcomes;
        }
    }

    private static class UtilityDefinition {
        final String name;
        String[] parents;
        double[] utilities;

        UtilityDefinition(String name) {
            this.name = name;
        }
    }
}
