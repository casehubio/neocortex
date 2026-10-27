package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class HillClimbingSearch {

    private HillClimbingSearch() {}

    static StructureResult learn(List<Map<String, String>> data,
                                  StructuralConstraints constraints,
                                  int restarts, long seed) {
        if (data.isEmpty()) throw new IllegalArgumentException("Data must not be empty");

        List<String> variables = discoverVariables(data);
        int n = data.size();

        if (variables.size() <= 1) {
            return singleVariableResult(variables, data);
        }

        var outcomeMaps = new HashMap<String, List<String>>();
        for (String v : variables) {
            outcomeMaps.put(v, ContingencyTable.discoverOutcomes(v, data));
        }

        var rng = new Random(seed);
        Map<String, Set<String>> bestGraph = null;
        double bestTotalScore = Double.POSITIVE_INFINITY;
        int bestRestartIdx = 0;
        var restartScores = new ArrayList<Double>();
        int totalOperations = 0;

        for (int r = 0; r < restarts; r++) {
            var parents = new HashMap<String, Set<String>>();
            for (String v : variables) parents.put(v, new HashSet<>());

            for (var edge : constraints.requiredEdges()) {
                if (parents.containsKey(edge.to()) && parents.containsKey(edge.from())) {
                    parents.get(edge.to()).add(edge.from());
                }
            }

            var localScores = new HashMap<String, Double>();
            for (String v : variables) {
                localScores.put(v, localBicScore(v,
                    new ArrayList<>(parents.get(v)), data, outcomeMaps, n));
            }

            boolean improved = true;
            while (improved) {
                improved = false;
                Operation bestOp = null;
                double bestDelta = 0.0;

                var candidates = generateCandidates(variables, parents, constraints);
                Collections.shuffle(candidates, rng);

                for (var op : candidates) {
                    double delta = evaluateOperation(op, parents, localScores,
                        data, outcomeMaps, n, variables);
                    if (delta < bestDelta) {
                        bestDelta = delta;
                        bestOp = op;
                    }
                }

                if (bestOp != null) {
                    applyOperation(bestOp, parents);
                    for (String affected : bestOp.affectedVariables()) {
                        localScores.put(affected, localBicScore(affected,
                            new ArrayList<>(parents.get(affected)),
                            data, outcomeMaps, n));
                    }
                    totalOperations++;
                    improved = true;
                }
            }

            double totalScore = localScores.values().stream()
                .mapToDouble(Double::doubleValue).sum();
            restartScores.add(totalScore);

            if (totalScore < bestTotalScore) {
                bestTotalScore = totalScore;
                bestGraph = deepCopy(parents);
                bestRestartIdx = r;
            }
        }

        return buildResult(bestGraph, variables, outcomeMaps,
            bestTotalScore, totalOperations, restarts, bestRestartIdx,
            restartScores);
    }

    private static double localBicScore(String variable, List<String> parents,
                                         List<Map<String, String>> data,
                                         Map<String, List<String>> outcomeMaps,
                                         int n) {
        var table = ContingencyTable.buildForVariable(variable, parents, data);
        int numOutcomes = outcomeMaps.get(variable).size();

        double ll = 0.0;
        for (int j = 0; j < table.counts().length; j++) {
            int configTotal = 0;
            for (int k = 0; k < numOutcomes; k++) {
                configTotal += table.counts()[j][k];
            }
            if (configTotal == 0) continue;
            for (int k = 0; k < numOutcomes; k++) {
                int count = table.counts()[j][k];
                if (count > 0) {
                    ll += count * Math.log((double) count / configTotal);
                }
            }
        }

        int parentConfigs = table.counts().length;
        int freeParams = parentConfigs * (numOutcomes - 1);
        return -2.0 * ll + freeParams * Math.log(n);
    }

    private static List<Operation> generateCandidates(
            List<String> variables,
            Map<String, Set<String>> parents,
            StructuralConstraints constraints) {
        var candidates = new ArrayList<Operation>();
        for (String from : variables) {
            for (String to : variables) {
                if (from.equals(to)) continue;
                boolean hasEdge = parents.get(to).contains(from);

                if (!hasEdge) {
                    if (!isForbidden(from, to, constraints)
                            && parents.get(to).size() < constraints.maxParents()) {
                        candidates.add(new Operation(OpType.ADD, from, to));
                    }
                } else {
                    if (!isRequired(from, to, constraints)) {
                        candidates.add(new Operation(OpType.REMOVE, from, to));
                    }
                    if (!isRequired(from, to, constraints)
                            && !isForbidden(to, from, constraints)
                            && parents.get(from).size() < constraints.maxParents()) {
                        candidates.add(new Operation(OpType.REVERSE, from, to));
                    }
                }
            }
        }
        return candidates;
    }

    private static double evaluateOperation(Operation op,
                                             Map<String, Set<String>> parents,
                                             Map<String, Double> localScores,
                                             List<Map<String, String>> data,
                                             Map<String, List<String>> outcomeMaps,
                                             int n, List<String> variables) {
        var newParents = deepCopy(parents);
        applyOperation(op, newParents);

        if (!isAcyclic(newParents, variables)) return 0.0;

        double delta = 0.0;
        for (String affected : op.affectedVariables()) {
            double newScore = localBicScore(affected,
                new ArrayList<>(newParents.get(affected)),
                data, outcomeMaps, n);
            delta += newScore - localScores.get(affected);
        }
        return delta;
    }

    private static void applyOperation(Operation op,
                                        Map<String, Set<String>> parents) {
        switch (op.type) {
            case ADD -> parents.get(op.to).add(op.from);
            case REMOVE -> parents.get(op.to).remove(op.from);
            case REVERSE -> {
                parents.get(op.to).remove(op.from);
                parents.get(op.from).add(op.to);
            }
        }
    }

    private static boolean isAcyclic(Map<String, Set<String>> parents,
                                      List<String> variables) {
        var inDegree = new HashMap<String, Integer>();
        for (String v : variables) inDegree.put(v, 0);
        for (var entry : parents.entrySet()) {
            inDegree.put(entry.getKey(),
                inDegree.get(entry.getKey()) + entry.getValue().size());
        }

        var queue = new ArrayList<String>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        int visited = 0;
        while (!queue.isEmpty()) {
            String node = queue.remove(queue.size() - 1);
            visited++;
            for (String v : variables) {
                if (parents.get(v).contains(node)) {
                    int newDegree = inDegree.get(v) - 1;
                    inDegree.put(v, newDegree);
                    if (newDegree == 0) queue.add(v);
                }
            }
        }
        return visited == variables.size();
    }

    private static boolean isForbidden(String from, String to,
                                        StructuralConstraints constraints) {
        return constraints.forbiddenEdges().contains(
            new StructuralConstraints.Edge(from, to));
    }

    private static boolean isRequired(String from, String to,
                                       StructuralConstraints constraints) {
        return constraints.requiredEdges().contains(
            new StructuralConstraints.Edge(from, to));
    }

    private static Map<String, Set<String>> deepCopy(
            Map<String, Set<String>> parents) {
        var copy = new HashMap<String, Set<String>>();
        for (var entry : parents.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    private static List<String> discoverVariables(List<Map<String, String>> data) {
        var vars = new LinkedHashSet<String>();
        for (var obs : data) vars.addAll(obs.keySet());
        return List.copyOf(vars);
    }

    private static StructureResult buildResult(
            Map<String, Set<String>> parents,
            List<String> variables,
            Map<String, List<String>> outcomeMaps,
            double bestScore, int totalOps, int restartCount,
            int bestRestartIdx, List<Double> restartScores) {

        var edgeSpecs = new ArrayList<EdgeSpec>();
        for (String to : variables) {
            for (String from : parents.get(to)) {
                edgeSpecs.add(new EdgeSpec(from, to,
                    "hill-climbing BIC", SpecStatus.PROPOSED));
            }
        }

        var varSpecs = new ArrayList<VariableSpec>();
        for (String v : variables) {
            varSpecs.add(new VariableSpec(v, outcomeMaps.get(v), null,
                SpecStatus.PROPOSED, new CptSpec.Uniform()));
        }

        var spec = new NetworkSpec("learned-hc", varSpecs, edgeSpecs, null);
        var diagnostics = new StructureDiagnostics.HillClimbingDiagnostics(
            bestScore, totalOps, restartCount, bestRestartIdx, restartScores);

        return new StructureResult(spec, "HillClimbing", diagnostics);
    }

    private static StructureResult singleVariableResult(
            List<String> variables, List<Map<String, String>> data) {
        var varSpecs = new ArrayList<VariableSpec>();
        for (String v : variables) {
            List<String> outcomes = ContingencyTable.discoverOutcomes(v, data);
            varSpecs.add(new VariableSpec(v, outcomes, null,
                SpecStatus.PROPOSED, new CptSpec.Uniform()));
        }
        var spec = new NetworkSpec("learned-hc", varSpecs, List.of(), null);
        var diagnostics = new StructureDiagnostics.HillClimbingDiagnostics(
            0.0, 0, 1, 0, List.of(0.0));
        return new StructureResult(spec, "HillClimbing", diagnostics);
    }

    private enum OpType { ADD, REMOVE, REVERSE }

    private record Operation(OpType type, String from, String to) {
        List<String> affectedVariables() {
            if (type == OpType.REVERSE) return List.of(from, to);
            return List.of(to);
        }
    }
}
