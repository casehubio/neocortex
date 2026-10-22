package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CausalEngine {

    public static BayesInstance<?> intervene(BayesNetwork network,
                                             String varName, String state) {
        return intervene(network, Map.of(varName, state));
    }

    public static BayesInstance<?> intervene(BayesNetwork network,
                                             Map<String, String> interventions) {
        validateInterventions(network, interventions);

        BayesNetwork mutilated = buildMutilatedNetwork(network, interventions.keySet());
        JunctionTree tree = new JunctionTreeBuilder(mutilated).build();
        BayesInstance<?> instance = new BayesInstance<>(tree);

        for (Map.Entry<String, String> entry : interventions.entrySet()) {
            instance.setHardEvidence(entry.getKey(), entry.getValue());
        }
        instance.globalUpdate();

        return instance;
    }

    public static CounterfactualResult counterfactual(BayesNetwork network,
                                                       String varName, String state,
                                                       String targetVar) {
        validateVariable(network, varName);
        validateState(network, varName, state);
        validateVariable(network, targetVar);

        // Observational: P(target | observe(var=state))
        JunctionTree obsTree = new JunctionTreeBuilder(network).build();
        BayesInstance<?> obsInstance = new BayesInstance<>(obsTree);
        obsInstance.setHardEvidence(varName, state);
        obsInstance.globalUpdate();
        double[] observational = obsInstance.marginalize(targetVar).getDistribution().clone();

        // Interventional: P(target | do(var=state))
        BayesInstance<?> intInstance = intervene(network, varName, state);
        double[] interventional = intInstance.marginalize(targetVar).getDistribution().clone();

        String[] targetOutcomes = findTargetOutcomes(network, targetVar);

        return new CounterfactualResult(
                varName, state, targetVar,
                observational, interventional, targetOutcomes);
    }

    static BayesNetwork buildMutilatedNetwork(BayesNetwork original,
                                               Set<String> interventionVars) {
        List<VarDef> variables = extractTopological(original);

        BayesNetworkBuilder builder = original.getName() != null
                ? BayesNetworkBuilder.create(original.getName())
                : BayesNetworkBuilder.create();

        BayesNetworkBuilder.CompleteDef last = null;
        for (VarDef var : variables) {
            if (interventionVars.contains(var.name)) {
                double[] uniformPrior = new double[var.outcomes.length];
                Arrays.fill(uniformPrior, 1.0 / var.outcomes.length);
                last = builder.variable(var.name, var.outcomes).prior(uniformPrior);
            } else if (var.parents.length == 0) {
                last = builder.variable(var.name, var.outcomes).prior(var.cpt[0]);
            } else {
                last = builder.variable(var.name, var.outcomes)
                        .given(var.parents)
                        .cpt(var.cpt);
            }
        }

        return last != null ? last.build() : builder.build();
    }

    private static List<VarDef> extractTopological(BayesNetwork network) {
        Map<String, VarDef> byName = new LinkedHashMap<>();
        for (GraphNode<BayesVariable> node : network) {
            BayesVariable<?> var = node.getContent();
            byName.put(var.getName(), new VarDef(
                    var.getName(),
                    toStringArray(var.getOutcomes()),
                    var.getGiven(),
                    var.getProbabilityTable()));
        }

        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (VarDef v : byName.values()) {
            inDegree.put(v.name, v.parents.length);
            children.put(v.name, new ArrayList<>());
        }
        for (VarDef v : byName.values()) {
            for (String parent : v.parents) {
                children.get(parent).add(v.name);
            }
        }

        List<VarDef> sorted = new ArrayList<>();
        java.util.Queue<String> queue = new java.util.ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        while (!queue.isEmpty()) {
            String name = queue.poll();
            sorted.add(byName.get(name));
            for (String child : children.get(name)) {
                int remaining = inDegree.get(child) - 1;
                inDegree.put(child, remaining);
                if (remaining == 0) {
                    queue.add(child);
                }
            }
        }

        return sorted;
    }

    private static void validateInterventions(BayesNetwork network,
                                               Map<String, String> interventions) {
        for (Map.Entry<String, String> entry : interventions.entrySet()) {
            validateVariable(network, entry.getKey());
            validateState(network, entry.getKey(), entry.getValue());
        }
    }

    private static void validateVariable(BayesNetwork network, String varName) {
        boolean found = false;
        for (GraphNode<BayesVariable> node : network) {
            if (varName.equals(node.getContent().getName())) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Variable not found: " + varName);
        }
    }

    private static void validateState(BayesNetwork network, String varName, String state) {
        for (GraphNode<BayesVariable> node : network) {
            if (varName.equals(node.getContent().getName())) {
                for (Object outcome : node.getContent().getOutcomes()) {
                    if (state.equals(outcome)) {
                        return;
                    }
                }
                throw new IllegalArgumentException(
                        "State '" + state + "' not found for variable '" + varName + "'");
            }
        }
    }

    private static String[] findTargetOutcomes(BayesNetwork network, String varName) {
        for (GraphNode<BayesVariable> node : network) {
            if (varName.equals(node.getContent().getName())) {
                return toStringArray(node.getContent().getOutcomes());
            }
        }
        throw new IllegalArgumentException("Variable not found: " + varName);
    }

    private static String[] toStringArray(Object[] outcomes) {
        String[] result = new String[outcomes.length];
        for (int i = 0; i < outcomes.length; i++) {
            result[i] = String.valueOf(outcomes[i]);
        }
        return result;
    }

    private record VarDef(String name, String[] outcomes, String[] parents, double[][] cpt) {}
}
