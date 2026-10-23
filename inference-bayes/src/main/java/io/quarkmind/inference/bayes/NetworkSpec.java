package io.quarkmind.inference.bayes;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public record NetworkSpec(
    String name,
    List<VariableSpec> variables,
    List<EdgeSpec> edges,
    String domainDescription
) {
    public NetworkSpec {
        variables = List.copyOf(variables);
        edges = List.copyOf(edges);
    }

    public NetworkSpec withVariable(VariableSpec variable) {
        for (var v : variables) {
            if (v.name().equals(variable.name())) {
                throw new IllegalArgumentException("Duplicate variable name: '" + variable.name() + "'");
            }
        }
        var newVars = new ArrayList<>(variables);
        newVars.add(variable);
        return new NetworkSpec(name, newVars, edges, domainDescription);
    }

    public NetworkSpec withEdge(EdgeSpec edge) {
        var newEdges = new ArrayList<>(edges);
        newEdges.add(edge);
        validateAcyclic(variables, newEdges);
        return new NetworkSpec(name, variables, newEdges, domainDescription);
    }

    public NetworkSpec withVariableAccepted(String varName) {
        return withVariableStatus(varName, SpecStatus.ACCEPTED);
    }

    public NetworkSpec withVariableLocked(String varName) {
        return withVariableStatus(varName, SpecStatus.LOCKED);
    }

    public NetworkSpec withVariableRemoved(String varName) {
        var newVars = new ArrayList<VariableSpec>();
        for (var v : variables) {
            if (!v.name().equals(varName)) newVars.add(v);
        }
        var newEdges = new ArrayList<EdgeSpec>();
        for (var e : edges) {
            if (!e.from().equals(varName) && !e.to().equals(varName)) {
                newEdges.add(e);
            }
        }
        return new NetworkSpec(name, newVars, newEdges, domainDescription);
    }

    public NetworkSpec withEdgeAccepted(String from, String to) {
        return withEdgeStatus(from, to, SpecStatus.ACCEPTED);
    }

    public NetworkSpec withEdgeRejected(String from, String to) {
        return withEdgeStatus(from, to, SpecStatus.REJECTED);
    }

    public NetworkSpec withEdgeLocked(String from, String to) {
        return withEdgeStatus(from, to, SpecStatus.LOCKED);
    }

    public NetworkSpec withEdgeRemoved(String from, String to) {
        var newEdges = new ArrayList<EdgeSpec>();
        for (var e : edges) {
            if (!(e.from().equals(from) && e.to().equals(to))) {
                newEdges.add(e);
            }
        }
        return new NetworkSpec(name, variables, newEdges, domainDescription);
    }

    public BayesNetworkBuilder toBuilder() {
        List<VariableSpec> activeVars = variables.stream()
                                                 .filter(v -> v.status() != SpecStatus.REJECTED)
                                                 .toList();
        List<EdgeSpec> activeEdges = edges.stream()
                                          .filter(e -> e.status() != SpecStatus.REJECTED)
                                          .toList();

        Map<String, List<String>> parentMap = new HashMap<>();
        for (var v : activeVars) {parentMap.put(v.name(), new ArrayList<>());}
        for (var e : activeEdges) {
            parentMap.computeIfAbsent(e.to(), k -> new ArrayList<>()).add(e.from());
        }

        List<VariableSpec>        sorted = topologicalSort(activeVars, activeEdges);
        Map<String, VariableSpec> byName = new HashMap<>();
        for (var v : sorted) {byName.put(v.name(), v);}

        BayesNetworkBuilder builder = name != null
                                      ? BayesNetworkBuilder.create(name) : BayesNetworkBuilder.create();

        for (var v : sorted) {
            List<String> parents   = parentMap.getOrDefault(v.name(), List.of());
            double[][]   cptValues = resolveCpt(v, parents, byName);

            if (parents.isEmpty()) {
                builder.variable(v.name(), v.outcomes().toArray(new String[0]))
                       .prior(cptValues[0]);
            } else {
                builder.variable(v.name(), v.outcomes().toArray(new String[0]))
                       .given(parents.toArray(new String[0]))
                       .cpt(cptValues);
            }
        }
        return builder;
    }

    public BayesNetwork toBayesNetwork() {
        return toBuilder().build();
    }

    private static double[][] resolveCpt(VariableSpec var, List<String> parents,
                                         Map<String, VariableSpec> allVars) {
        CptSpec spec        = var.cptSpec();
        int     numOutcomes = var.outcomes().size();

        if (spec == null || spec instanceof CptSpec.Uniform) {
            int rows = 1;
            for (String p : parents) {rows *= allVars.get(p).outcomes().size();}
            double[][] cpt     = new double[rows][numOutcomes];
            double     uniform = 1.0 / numOutcomes;
            for (double[] row : cpt) {java.util.Arrays.fill(row, uniform);}
            return cpt;
        }
        if (spec instanceof CptSpec.DirectCpt direct) {
            return direct.values();
        }
        if (spec instanceof CptSpec.NoisyOr noisyOr) {
            return NoisyOrExpander.expand(noisyOr, numOutcomes);
        }
        throw new IllegalStateException("Unknown CptSpec type: " + spec.getClass());
    }

    private static List<VariableSpec> topologicalSort(List<VariableSpec> variables,
                                                      List<EdgeSpec> edges) {
        Map<String, VariableSpec> byName = new HashMap<>();
        for (var v : variables) {byName.put(v.name(), v);}

        Map<String, Integer>      inDegree = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (var v : variables) {
            inDegree.put(v.name(), 0);
            children.put(v.name(), new ArrayList<>());
        }
        for (var e : edges) {
            if (!byName.containsKey(e.from()) || !byName.containsKey(e.to())) {continue;}
            inDegree.merge(e.to(), 1, Integer::sum);
            children.get(e.from()).add(e.to());
        }

        Queue<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {queue.add(entry.getKey());}
        }
        List<VariableSpec> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            sorted.add(byName.get(node));
            for (String child : children.getOrDefault(node, List.of())) {
                int remaining = inDegree.get(child) - 1;
                inDegree.put(child, remaining);
                if (remaining == 0) {queue.add(child);}
            }
        }
        return sorted;
    }


    private NetworkSpec withVariableStatus(String varName, SpecStatus status) {
        var newVars = new ArrayList<VariableSpec>();
        boolean found = false;
        for (var v : variables) {
            if (v.name().equals(varName)) {
                newVars.add(new VariableSpec(v.name(), v.outcomes(), v.description(), status, v.cptSpec()));
                found = true;
            } else {
                newVars.add(v);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Variable not found: '" + varName + "'");
        }
        return new NetworkSpec(name, newVars, edges, domainDescription);
    }

    private NetworkSpec withEdgeStatus(String from, String to, SpecStatus status) {
        var newEdges = new ArrayList<EdgeSpec>();
        boolean found = false;
        for (var e : edges) {
            if (e.from().equals(from) && e.to().equals(to)) {
                newEdges.add(new EdgeSpec(e.from(), e.to(), e.rationale(), status));
                found = true;
            } else {
                newEdges.add(e);
            }
        }
        if (!found) {
            throw new IllegalArgumentException("Edge not found: '" + from + "' -> '" + to + "'");
        }
        return new NetworkSpec(name, variables, newEdges, domainDescription);
    }

    private static void validateAcyclic(List<VariableSpec> variables, List<EdgeSpec> edges) {
        Set<String> varNames = new HashSet<>();
        for (var v : variables) varNames.add(v.name());

        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (String n : varNames) {
            inDegree.put(n, 0);
            children.put(n, new ArrayList<>());
        }
        for (var edge : edges) {
            if (edge.status() == SpecStatus.REJECTED) continue;
            if (!varNames.contains(edge.from()) || !varNames.contains(edge.to())) continue;
            inDegree.merge(edge.to(), 1, Integer::sum);
            children.get(edge.from()).add(edge.to());
        }

        Queue<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }
        int visited = 0;
        while (!queue.isEmpty()) {
            String node = queue.poll();
            visited++;
            for (String child : children.getOrDefault(node, List.of())) {
                int remaining = inDegree.get(child) - 1;
                inDegree.put(child, remaining);
                if (remaining == 0) queue.add(child);
            }
        }
        if (visited != varNames.size()) {
            throw new IllegalArgumentException("Adding this edge would create a cycle — not a valid DAG");
        }
    }
}
