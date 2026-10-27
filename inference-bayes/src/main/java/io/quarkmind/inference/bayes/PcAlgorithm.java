package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class PcAlgorithm {

    private PcAlgorithm() {}

    static StructureResult learn(List<Map<String, String>> data,
                                  IndependenceTest test,
                                  StructuralConstraints constraints) {
        if (data.isEmpty()) throw new IllegalArgumentException("Data must not be empty");

        List<String> variables = discoverVariables(data);
        if (variables.size() <= 1) {
            return singleVariableResult(variables, data);
        }

        var adjacent = initAdjacency(variables, constraints);
        var sepSets = new HashMap<String, Set<String>>();

        int totalTests = 0;
        int edgesRemoved = 0;
        int maxCondSize = 0;

        // Phase 1: Skeleton discovery
        for (int d = 0; ; d++) {
            boolean anyEdgeTested = false;
            var pairs = new ArrayList<>(allAdjacentPairs(adjacent, variables));
            for (var pair : pairs) {
                String x = pair[0];
                String y = pair[1];

                if (isRequired(x, y, constraints)) continue;
                if (!adjacent.get(x).contains(y)) continue;

                var neighbors = new HashSet<>(adjacent.get(x));
                neighbors.remove(y);

                if (neighbors.size() < d) continue;
                anyEdgeTested = true;

                int maxCond = constraints.maxParents() == Integer.MAX_VALUE
                    ? d : Math.min(d, constraints.maxParents() - 1);
                if (d > maxCond) continue;

                for (var subset : subsetsOfSize(neighbors, d)) {
                    totalTests++;
                    maxCondSize = Math.max(maxCondSize, subset.size());
                    var result = test.test(x, y, subset, data);
                    if (result.independent()) {
                        adjacent.get(x).remove(y);
                        adjacent.get(y).remove(x);
                        sepSets.put(sepKey(x, y), subset);
                        edgesRemoved++;
                        break;
                    }
                }
            }
            if (!anyEdgeTested) break;
        }

        // Phase 2: V-structure detection
        var directed = new HashSet<String>();
        int vStructuresFound = 0;

        for (String z : variables) {
            var neighborsOfZ = new ArrayList<>(adjacent.get(z));
            for (int i = 0; i < neighborsOfZ.size(); i++) {
                for (int j = i + 1; j < neighborsOfZ.size(); j++) {
                    String x = neighborsOfZ.get(i);
                    String y = neighborsOfZ.get(j);
                    if (adjacent.get(x).contains(y)) continue;

                    Set<String> sep = sepSets.getOrDefault(sepKey(x, y), Set.of());
                    if (!sep.contains(z)) {
                        directed.add(x + "->" + z);
                        directed.add(y + "->" + z);
                        vStructuresFound++;
                    }
                }
            }
        }

        // Phase 3: Meek's orientation rules
        int edgesOriented = applyMeekRules(variables, adjacent, directed);

        // Build NetworkSpec
        int edgesUndirected = 0;
        var edgeSpecs = new ArrayList<EdgeSpec>();
        var processed = new HashSet<String>();
        for (String x : variables) {
            for (String y : adjacent.get(x)) {
                String pairKey = pairKey(x, y);
                if (processed.contains(pairKey)) continue;
                processed.add(pairKey);

                if (directed.contains(x + "->" + y)) {
                    edgeSpecs.add(new EdgeSpec(x, y,
                        "oriented by PC algorithm", SpecStatus.PROPOSED));
                } else if (directed.contains(y + "->" + x)) {
                    edgeSpecs.add(new EdgeSpec(y, x,
                        "oriented by PC algorithm", SpecStatus.PROPOSED));
                } else {
                    edgeSpecs.add(new EdgeSpec(x, y,
                        "orientation undetermined — equivalence class; "
                        + "either direction is consistent with the data",
                        SpecStatus.PROPOSED));
                    edgesUndirected++;
                }
            }
        }

        var varSpecs = new ArrayList<VariableSpec>();
        for (String v : variables) {
            List<String> outcomes = ContingencyTable.discoverOutcomes(v, data);
            varSpecs.add(new VariableSpec(v, outcomes, null,
                SpecStatus.PROPOSED, new CptSpec.Uniform()));
        }

        var spec = new NetworkSpec("learned-pc", varSpecs, edgeSpecs, null);
        var diagnostics = new StructureDiagnostics.PcDiagnostics(
            totalTests, edgesRemoved, vStructuresFound,
            edgesOriented, edgesUndirected, maxCondSize);

        return new StructureResult(spec, "PC", diagnostics);
    }

    private static List<String> discoverVariables(List<Map<String, String>> data) {
        var vars = new LinkedHashSet<String>();
        for (var obs : data) vars.addAll(obs.keySet());
        return List.copyOf(vars);
    }

    private static Map<String, Set<String>> initAdjacency(
            List<String> variables, StructuralConstraints constraints) {
        var forbidden = constraints.forbiddenEdges();
        var adj = new HashMap<String, Set<String>>();
        for (String v : variables) adj.put(v, new HashSet<>());

        for (int i = 0; i < variables.size(); i++) {
            for (int j = i + 1; j < variables.size(); j++) {
                String a = variables.get(i);
                String b = variables.get(j);
                boolean isForbidden = forbidden.contains(
                        new StructuralConstraints.Edge(a, b))
                    && forbidden.contains(
                        new StructuralConstraints.Edge(b, a));
                if (!isForbidden) {
                    adj.get(a).add(b);
                    adj.get(b).add(a);
                }
            }
        }
        return adj;
    }

    private static boolean isRequired(String x, String y,
                                       StructuralConstraints constraints) {
        return constraints.requiredEdges().contains(
                new StructuralConstraints.Edge(x, y))
            || constraints.requiredEdges().contains(
                new StructuralConstraints.Edge(y, x));
    }

    private static List<String[]> allAdjacentPairs(
            Map<String, Set<String>> adj, List<String> variables) {
        var pairs = new ArrayList<String[]>();
        var seen = new HashSet<String>();
        for (String x : variables) {
            for (String y : adj.get(x)) {
                String key = pairKey(x, y);
                if (seen.add(key)) pairs.add(new String[]{x, y});
            }
        }
        return pairs;
    }

    private static List<Set<String>> subsetsOfSize(Set<String> set, int size) {
        if (size == 0) return List.of(Set.of());
        var elements = new ArrayList<>(set);
        var result = new ArrayList<Set<String>>();
        generateSubsets(elements, size, 0, new ArrayList<>(), result);
        return result;
    }

    private static void generateSubsets(List<String> elements, int size,
                                         int start, List<String> current,
                                         List<Set<String>> result) {
        if (current.size() == size) {
            result.add(Set.copyOf(current));
            return;
        }
        for (int i = start; i < elements.size(); i++) {
            current.add(elements.get(i));
            generateSubsets(elements, size, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private static int applyMeekRules(List<String> variables,
                                       Map<String, Set<String>> adj,
                                       Set<String> directed) {
        int oriented = 0;
        boolean changed = true;
        while (changed) {
            changed = false;

            for (String y : variables) {
                for (String x : new ArrayList<>(adj.get(y))) {
                    if (isDirected(x, y, directed) || isDirected(y, x, directed))
                        continue;

                    // R1: W→X—Y where W and Y not adjacent → X→Y
                    for (String w : adj.get(x)) {
                        if (w.equals(y)) continue;
                        if (directed.contains(w + "->" + x)
                                && !adj.get(w).contains(y)) {
                            if (!wouldCreateCycle(x, y, adj, directed)) {
                                directed.add(x + "->" + y);
                                oriented++;
                                changed = true;
                            }
                            break;
                        }
                    }
                    if (isDirected(x, y, directed)) continue;

                    // R2: X→W→Y and X—Y → X→Y
                    for (String w : adj.get(x)) {
                        if (w.equals(y)) continue;
                        if (directed.contains(x + "->" + w)
                                && directed.contains(w + "->" + y)
                                && adj.get(x).contains(y)) {
                            if (!wouldCreateCycle(x, y, adj, directed)) {
                                directed.add(x + "->" + y);
                                oriented++;
                                changed = true;
                            }
                            break;
                        }
                    }
                    if (isDirected(x, y, directed)) continue;

                    // R4: X—Y, Y→Z→W, X—W → X→Y
                    for (String z : adj.get(y)) {
                        if (z.equals(x)) continue;
                        if (!directed.contains(y + "->" + z)) continue;
                        for (String w : adj.get(z)) {
                            if (w.equals(y) || w.equals(x)) continue;
                            if (directed.contains(z + "->" + w)
                                    && adj.get(x).contains(w)
                                    && !isDirected(x, w, directed)
                                    && !isDirected(w, x, directed)) {
                                if (!wouldCreateCycle(x, y, adj, directed)) {
                                    directed.add(x + "->" + y);
                                    oriented++;
                                    changed = true;
                                }
                                break;
                            }
                        }
                        if (isDirected(x, y, directed)) break;
                    }
                }
            }

            // R3: X—Y, X—Z, Y→W←Z, X-W not adjacent, Y-Z not adjacent → X→Y, X→Z
            for (String x : variables) {
                var undirectedNeighbors = new ArrayList<String>();
                for (String n : adj.get(x)) {
                    if (!isDirected(x, n, directed) && !isDirected(n, x, directed))
                        undirectedNeighbors.add(n);
                }
                for (int i = 0; i < undirectedNeighbors.size(); i++) {
                    for (int j = i + 1; j < undirectedNeighbors.size(); j++) {
                        String y = undirectedNeighbors.get(i);
                        String z = undirectedNeighbors.get(j);
                        if (adj.get(y).contains(z)) continue;

                        for (String w : adj.get(y)) {
                            if (w.equals(x) || w.equals(z)) continue;
                            if (!adj.get(z).contains(w)) continue;
                            if (directed.contains(y + "->" + w)
                                    && directed.contains(z + "->" + w)
                                    && !adj.get(x).contains(w)) {
                                boolean orientedAny = false;
                                if (!isDirected(x, y, directed)
                                        && !wouldCreateCycle(x, y, adj, directed)) {
                                    directed.add(x + "->" + y);
                                    oriented++;
                                    orientedAny = true;
                                }
                                if (!isDirected(x, z, directed)
                                        && !wouldCreateCycle(x, z, adj, directed)) {
                                    directed.add(x + "->" + z);
                                    oriented++;
                                    orientedAny = true;
                                }
                                if (orientedAny) changed = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return oriented;
    }

    private static boolean isDirected(String from, String to,
                                       Set<String> directed) {
        return directed.contains(from + "->" + to);
    }

    private static boolean wouldCreateCycle(String from, String to,
                                             Map<String, Set<String>> adj,
                                             Set<String> directed) {
        var visited = new HashSet<String>();
        var queue = new ArrayList<String>();
        queue.add(to);
        visited.add(to);
        while (!queue.isEmpty()) {
            String current = queue.remove(queue.size() - 1);
            for (String neighbor : adj.get(current)) {
                if (directed.contains(current + "->" + neighbor)) {
                    if (neighbor.equals(from)) return true;
                    if (visited.add(neighbor)) queue.add(neighbor);
                }
            }
        }
        return false;
    }

    private static String sepKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static String pairKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "~" + b : b + "~" + a;
    }

    private static StructureResult singleVariableResult(
            List<String> variables, List<Map<String, String>> data) {
        var varSpecs = new ArrayList<VariableSpec>();
        for (String v : variables) {
            List<String> outcomes = ContingencyTable.discoverOutcomes(v, data);
            varSpecs.add(new VariableSpec(v, outcomes, null,
                SpecStatus.PROPOSED, new CptSpec.Uniform()));
        }
        var spec = new NetworkSpec("learned-pc", varSpecs, List.of(), null);
        var diagnostics = new StructureDiagnostics.PcDiagnostics(0, 0, 0, 0, 0, 0);
        return new StructureResult(spec, "PC", diagnostics);
    }
}
