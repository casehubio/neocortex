package io.quarkmind.inference.bayes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record ContingencyTable(int[][] counts, int[] rowTotals, int[] colTotals, int total) {

    static List<ContingencyTable> build(String x, String y, Set<String> conditioningSet,
                                         List<Map<String, String>> data) {
        var xOutcomes = discoverOutcomes(x, data);
        var yOutcomes = discoverOutcomes(y, data);

        if (conditioningSet.isEmpty()) {
            return List.of(buildTable(x, y, xOutcomes, yOutcomes, data));
        }

        var groups = new LinkedHashMap<String, List<Map<String, String>>>();
        for (var obs : data) {
            String key = conditioningKey(obs, conditioningSet);
            if (key == null) continue;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(obs);
        }

        var tables = new ArrayList<ContingencyTable>();
        for (var group : groups.values()) {
            ContingencyTable t = buildTable(x, y, xOutcomes, yOutcomes, group);
            if (t.total > 0) tables.add(t);
        }
        return List.copyOf(tables);
    }

    static ContingencyTable buildForVariable(String variable, List<String> parents,
                                              List<Map<String, String>> data) {
        var varOutcomes = discoverOutcomes(variable, data);
        int numOutcomes = varOutcomes.size();

        if (parents.isEmpty()) {
            int[][] counts = new int[1][numOutcomes];
            int total = 0;
            for (var obs : data) {
                String val = obs.get(variable);
                if (val == null) continue;
                int idx = varOutcomes.indexOf(val);
                if (idx >= 0) {
                    counts[0][idx]++;
                    total++;
                }
            }
            return fromCounts(counts, total);
        }

        var parentOutcomeLists = new ArrayList<List<String>>();
        for (String p : parents) {
            parentOutcomeLists.add(discoverOutcomes(p, data));
        }
        int numParentConfigs = 1;
        for (var po : parentOutcomeLists) numParentConfigs *= po.size();

        int[][] counts = new int[numParentConfigs][numOutcomes];
        int total = 0;
        for (var obs : data) {
            String val = obs.get(variable);
            if (val == null) continue;
            int varIdx = varOutcomes.indexOf(val);
            if (varIdx < 0) continue;

            int parentConfig = parentConfigIndex(obs, parents, parentOutcomeLists);
            if (parentConfig < 0) continue;

            counts[parentConfig][varIdx]++;
            total++;
        }
        return fromCounts(counts, total);
    }

    private static ContingencyTable buildTable(String x, String y,
                                                List<String> xOutcomes, List<String> yOutcomes,
                                                List<Map<String, String>> data) {
        int[][] counts = new int[xOutcomes.size()][yOutcomes.size()];
        int total = 0;
        for (var obs : data) {
            String xVal = obs.get(x);
            String yVal = obs.get(y);
            if (xVal == null || yVal == null) continue;
            int xi = xOutcomes.indexOf(xVal);
            int yi = yOutcomes.indexOf(yVal);
            if (xi >= 0 && yi >= 0) {
                counts[xi][yi]++;
                total++;
            }
        }
        return fromCounts(counts, total);
    }

    private static ContingencyTable fromCounts(int[][] counts, int total) {
        int rows = counts.length;
        int cols = counts[0].length;
        int[] rowTotals = new int[rows];
        int[] colTotals = new int[cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                rowTotals[i] += counts[i][j];
                colTotals[j] += counts[i][j];
            }
        }
        return new ContingencyTable(counts, rowTotals, colTotals, total);
    }

    private static int parentConfigIndex(Map<String, String> obs,
                                          List<String> parents,
                                          List<List<String>> parentOutcomes) {
        int index = 0;
        for (int i = 0; i < parents.size(); i++) {
            String val = obs.get(parents.get(i));
            if (val == null) return -1;
            int stateIdx = parentOutcomes.get(i).indexOf(val);
            if (stateIdx < 0) return -1;
            int stride = 1;
            for (int j = i + 1; j < parents.size(); j++) {
                stride *= parentOutcomes.get(j).size();
            }
            index += stateIdx * stride;
        }
        return index;
    }

    private static String conditioningKey(Map<String, String> obs,
                                           Set<String> conditioningSet) {
        var sb = new StringBuilder();
        for (String var : conditioningSet) {
            String val = obs.get(var);
            if (val == null) return null;
            if (!sb.isEmpty()) sb.append('|');
            sb.append(var).append('=').append(val);
        }
        return sb.toString();
    }

    static List<String> discoverOutcomes(String variable,
                                          List<Map<String, String>> data) {
        var outcomes = new LinkedHashSet<String>();
        for (var obs : data) {
            String val = obs.get(variable);
            if (val != null) outcomes.add(val);
        }
        return List.copyOf(outcomes);
    }
}
