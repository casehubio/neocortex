package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.Graph;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

class NetworkMutation {
    private final Map<String, BayesVariable> vars;
    private final Map<String, String[]> parentOverrides = new HashMap<>();
    private final Map<String, double[][]> cptOverrides = new HashMap<>();
    private final Set<String> removedVars = new HashSet<>();

    NetworkMutation(Graph<BayesVariable> graph, Map<String, BayesVariable> variables) {
        this.vars = new LinkedHashMap<>(variables);
    }

    void updateCpt(String varName, double[][] newCpt, String[] newParents) {
        cptOverrides.put(varName, newCpt);
        parentOverrides.put(varName, newParents);
    }

    void addEdge(String from, String to) {
        // Edge is recorded implicitly via parent overrides
    }

    void removeVar(String name) {
        removedVars.add(name);
    }

    void addVar(String name, BayesVariable var, String[] parents, double[][] cpt) {
        vars.put(name, var);
        parentOverrides.put(name, parents);
        cptOverrides.put(name, cpt);
    }

    BayesVariable getVariable(String name) {
        if (removedVars.contains(name)) return null;
        return vars.get(name);
    }

    String[] getParents(String varName) {
        if (parentOverrides.containsKey(varName)) {
            return parentOverrides.get(varName);
        }
        BayesVariable var = vars.get(varName);
        return var != null ? var.getGiven() : new String[0];
    }

    double[][] getCpt(String varName) {
        if (cptOverrides.containsKey(varName)) {
            return cptOverrides.get(varName);
        }
        BayesVariable var = vars.get(varName);
        return var != null ? var.getProbabilityTable() : new double[0][];
    }

    List<String> topologicalOrder() {
        Map<String, Set<String>> adj = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();

        for (String name : vars.keySet()) {
            if (removedVars.contains(name)) continue;
            adj.put(name, new HashSet<>());
            inDegree.put(name, 0);
        }

        for (String name : adj.keySet()) {
            for (String parent : getParents(name)) {
                if (adj.containsKey(parent)) {
                    adj.get(parent).add(name);
                    inDegree.merge(name, 1, Integer::sum);
                }
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            order.add(node);
            for (String child : adj.get(node)) {
                int newDeg = inDegree.get(child) - 1;
                inDegree.put(child, newDeg);
                if (newDeg == 0) {
                    queue.add(child);
                }
            }
        }

        return order;
    }

    void validateAcyclic() {
        int totalVars = (int) vars.keySet().stream()
                .filter(n -> !removedVars.contains(n)).count();
        List<String> order = topologicalOrder();
        if (order.size() != totalVars) {
            throw new IllegalArgumentException("Adding this edge would create a cycle");
        }
    }
}
