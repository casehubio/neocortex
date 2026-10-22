package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParameterLearner {

    public static BayesNetwork fit(BayesNetwork structure,
                                   List<Map<String, String>> observations) {
        return fit(structure, observations, 1.0);
    }

    public static BayesNetwork fit(BayesNetwork structure,
                                   List<Map<String, String>> observations,
                                   double alpha) {
        List<VarMeta> variables = extractStructure(structure);
        Map<String, VarMeta> byName = new LinkedHashMap<>();
        for (VarMeta v : variables) {
            byName.put(v.name, v);
        }

        Map<String, double[][]> learnedCpts = new LinkedHashMap<>();
        for (VarMeta var : variables) {
            learnedCpts.put(var.name, learnCpt(var, byName, observations, alpha));
        }

        return buildNetwork(structure.getName(), variables, learnedCpts);
    }

    private static List<VarMeta> extractStructure(BayesNetwork network) {
        List<VarMeta> result = new ArrayList<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, VarMeta> byName = new LinkedHashMap<>();

        for (GraphNode<BayesVariable> node : network) {
            BayesVariable<?> var = node.getContent();
            VarMeta meta = new VarMeta(var.getName(), toStringArray(var.getOutcomes()), var.getGiven());
            byName.put(meta.name, meta);
            inDegree.put(meta.name, meta.parents.length);
        }

        // Topological sort — parents before children for the builder
        java.util.Queue<String> queue = new java.util.ArrayDeque<>();
        Map<String, List<String>> children = new LinkedHashMap<>();
        for (VarMeta v : byName.values()) {
            children.put(v.name, new ArrayList<>());
        }
        for (VarMeta v : byName.values()) {
            for (String parent : v.parents) {
                children.get(parent).add(v.name);
            }
            if (v.parents.length == 0) {
                queue.add(v.name);
            }
        }
        while (!queue.isEmpty()) {
            String name = queue.poll();
            result.add(byName.get(name));
            for (String child : children.get(name)) {
                int remaining = inDegree.get(child) - 1;
                inDegree.put(child, remaining);
                if (remaining == 0) {
                    queue.add(child);
                }
            }
        }

        return result;
    }

    private static double[][] learnCpt(VarMeta var, Map<String, VarMeta> allVars,
                                       List<Map<String, String>> observations,
                                       double alpha) {
        int numOutcomes = var.outcomes.length;

        if (var.parents.length == 0) {
            double[] counts = new double[numOutcomes];
            Arrays.fill(counts, alpha);

            for (Map<String, String> obs : observations) {
                String value = obs.get(var.name);
                if (value == null) continue;
                int stateIdx = indexOf(var.outcomes, value);
                if (stateIdx >= 0) {
                    counts[stateIdx]++;
                }
            }

            double[] row = normalize(counts);
            return new double[][]{row};
        }

        int parentCombinations = 1;
        int[] parentOutcomeCounts = new int[var.parents.length];
        for (int i = 0; i < var.parents.length; i++) {
            VarMeta parent = allVars.get(var.parents[i]);
            parentOutcomeCounts[i] = parent.outcomes.length;
            parentCombinations *= parent.outcomes.length;
        }

        double[][] counts = new double[parentCombinations][numOutcomes];
        for (double[] row : counts) {
            Arrays.fill(row, alpha);
        }

        for (Map<String, String> obs : observations) {
            String childValue = obs.get(var.name);
            if (childValue == null) continue;

            int childIdx = indexOf(var.outcomes, childValue);
            if (childIdx < 0) continue;

            int parentConfigIdx = parentConfigIndex(var.parents, allVars, obs, parentOutcomeCounts);
            if (parentConfigIdx < 0) continue;

            counts[parentConfigIdx][childIdx]++;
        }

        double[][] cpt = new double[parentCombinations][numOutcomes];
        for (int row = 0; row < parentCombinations; row++) {
            cpt[row] = normalize(counts[row]);
        }
        return cpt;
    }

    private static int parentConfigIndex(String[] parents, Map<String, VarMeta> allVars,
                                         Map<String, String> obs,
                                         int[] parentOutcomeCounts) {
        int index = 0;
        for (int i = 0; i < parents.length; i++) {
            VarMeta parent = allVars.get(parents[i]);
            String parentValue = obs.get(parents[i]);
            if (parentValue == null) return -1;

            int stateIdx = indexOf(parent.outcomes, parentValue);
            if (stateIdx < 0) return -1;

            int stride = 1;
            for (int j = i + 1; j < parents.length; j++) {
                stride *= parentOutcomeCounts[j];
            }
            index += stateIdx * stride;
        }
        return index;
    }

    private static double[] normalize(double[] counts) {
        double sum = 0;
        for (double c : counts) sum += c;
        double[] result = new double[counts.length];
        if (sum > 0) {
            for (int i = 0; i < counts.length; i++) {
                result[i] = counts[i] / sum;
            }
        } else {
            double uniform = 1.0 / counts.length;
            Arrays.fill(result, uniform);
        }
        return result;
    }

    private static int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return -1;
    }

    private static String[] toStringArray(Object[] outcomes) {
        String[] result = new String[outcomes.length];
        for (int i = 0; i < outcomes.length; i++) {
            result[i] = String.valueOf(outcomes[i]);
        }
        return result;
    }

    private static BayesNetwork buildNetwork(String name, List<VarMeta> variables,
                                             Map<String, double[][]> cpts) {
        BayesNetworkBuilder builder = name != null
            ? BayesNetworkBuilder.create(name)
            : BayesNetworkBuilder.create();

        BayesNetworkBuilder.CompleteDef last = null;
        for (VarMeta var : variables) {
            double[][] cpt = cpts.get(var.name);
            if (var.parents.length == 0) {
                last = builder.variable(var.name, var.outcomes).prior(cpt[0]);
            } else {
                last = builder.variable(var.name, var.outcomes)
                    .given(var.parents)
                    .cpt(cpt);
            }
        }

        return last != null ? last.build() : builder.build();
    }

    private record VarMeta(String name, String[] outcomes, String[] parents) {}
}
