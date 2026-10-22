package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OnlineParameterUpdater {

    private final BayesNetwork network;
    private final JunctionTree tree;
    private final Map<String, VarStats> stats;
    private final double decayFactor;
    private long observationCount;

    private OnlineParameterUpdater(BayesNetwork network, JunctionTree tree,
                                    Map<String, VarStats> stats, double decayFactor) {
        this.network = network;
        this.tree = tree;
        this.stats = stats;
        this.decayFactor = decayFactor;
    }

    public static OnlineParameterUpdater create(BayesNetwork network) {
        return create(network, 1.0);
    }

    public static OnlineParameterUpdater create(BayesNetwork network, double decayFactor) {
        JunctionTree tree = new JunctionTreeBuilder(network).build();
        Map<String, VarStats> stats = initializeStats(network, 1.0);
        return new OnlineParameterUpdater(network, tree, stats, decayFactor);
    }

    public static OnlineParameterUpdater create(BayesNetwork network,
                                                 List<Map<String, String>> historicalData) {
        return create(network, historicalData, 1.0);
    }

    public static OnlineParameterUpdater create(BayesNetwork network,
                                                 List<Map<String, String>> historicalData,
                                                 double decayFactor) {
        JunctionTree tree = new JunctionTreeBuilder(network).build();
        Map<String, VarStats> stats = initializeStats(network, 1.0);
        OnlineParameterUpdater updater = new OnlineParameterUpdater(network, tree, stats, decayFactor);
        for (Map<String, String> obs : historicalData) {
            updater.update(obs);
        }
        return updater;
    }

    public synchronized void update(Map<String, String> observation) {
        for (GraphNode<BayesVariable> node : network) {
            BayesVariable<?> var = node.getContent();
            String varName = var.getName();
            String value = observation.get(varName);
            if (value == null) {
                continue;
            }

            int stateIdx = indexOf(var.getOutcomes(), value);
            if (stateIdx < 0) {
                continue;
            }

            VarStats varStats = stats.get(varName);

            if (var.getGiven().length == 0) {
                if (decayFactor < 1.0) {
                    decay(varStats.counts[0]);
                }
                varStats.counts[0][stateIdx] += 1.0;
            } else {
                int parentConfig = parentConfigIndex(var, observation);
                if (parentConfig < 0) {
                    continue;
                }
                if (decayFactor < 1.0) {
                    decay(varStats.counts[parentConfig]);
                }
                varStats.counts[parentConfig][stateIdx] += 1.0;
            }

            updateCpt(var, varStats);
        }

        reinitializeCliquePotentials();
        observationCount++;
    }

    public synchronized BayesInstance<?> getInstance() {
        return new BayesInstance<>(tree);
    }

    public synchronized double[][] getCpt(String variableName) {
        for (GraphNode<BayesVariable> node : network) {
            if (variableName.equals(node.getContent().getName())) {
                double[][] cpt = node.getContent().getProbabilityTable();
                double[][] copy = new double[cpt.length][];
                for (int i = 0; i < cpt.length; i++) {
                    copy[i] = cpt[i].clone();
                }
                return copy;
            }
        }
        throw new IllegalArgumentException("Variable not found: " + variableName);
    }

    public synchronized long getObservationCount() {
        return observationCount;
    }

    public JunctionTree getJunctionTree() {
        return tree;
    }

    private void decay(double[] counts) {
        for (int i = 0; i < counts.length; i++) {
            counts[i] *= decayFactor;
        }
    }

    private void updateCpt(BayesVariable<?> var, VarStats varStats) {
        double[][] cpt = var.getProbabilityTable();
        for (int row = 0; row < varStats.counts.length; row++) {
            cpt[row] = normalize(varStats.counts[row]);
        }
    }

    private void reinitializeCliquePotentials() {
        for (JunctionTreeClique clique : tree.getJunctionTreeNodes()) {
            if (clique == null) continue;
            Arrays.fill(clique.getPotentials(), 0.0);
        }
        tree.recurseJTNodesAndInitialisePotentials(network, tree.getRoot());
    }

    private int parentConfigIndex(BayesVariable<?> var, Map<String, String> observation) {
        String[] parents = var.getGiven();
        int index = 0;
        for (int i = 0; i < parents.length; i++) {
            BayesVariable<?> parent = findVariable(parents[i]);
            if (parent == null) return -1;

            String parentValue = observation.get(parents[i]);
            if (parentValue == null) return -1;

            int stateIdx = indexOf(parent.getOutcomes(), parentValue);
            if (stateIdx < 0) return -1;

            int stride = 1;
            for (int j = i + 1; j < parents.length; j++) {
                BayesVariable<?> nextParent = findVariable(parents[j]);
                if (nextParent != null) {
                    stride *= nextParent.getOutcomes().length;
                }
            }
            index += stateIdx * stride;
        }
        return index;
    }

    private BayesVariable<?> findVariable(String name) {
        for (GraphNode<BayesVariable> node : network) {
            if (name.equals(node.getContent().getName())) {
                return node.getContent();
            }
        }
        return null;
    }

    private static Map<String, VarStats> initializeStats(BayesNetwork network, double alpha) {
        Map<String, VarStats> stats = new LinkedHashMap<>();
        Map<String, BayesVariable<?>> varsByName = new LinkedHashMap<>();

        for (GraphNode<BayesVariable> node : network) {
            varsByName.put(node.getContent().getName(), node.getContent());
        }

        for (GraphNode<BayesVariable> node : network) {
            BayesVariable<?> var = node.getContent();
            int numOutcomes = var.getOutcomes().length;
            int parentCombinations = 1;

            if (var.getGiven().length > 0) {
                for (String parentName : var.getGiven()) {
                    BayesVariable<?> parent = varsByName.get(parentName);
                    if (parent != null) {
                        parentCombinations *= parent.getOutcomes().length;
                    }
                }
            }

            double[][] counts = new double[parentCombinations][numOutcomes];
            for (double[] row : counts) {
                Arrays.fill(row, alpha);
            }
            stats.put(var.getName(), new VarStats(counts));
        }

        return stats;
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
            Arrays.fill(result, 1.0 / counts.length);
        }
        return result;
    }

    private static <T> int indexOf(T[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (String.valueOf(array[i]).equals(value)) return i;
        }
        return -1;
    }

    private static class VarStats {
        final double[][] counts;

        VarStats(double[][] counts) {
            this.counts = counts;
        }
    }
}
