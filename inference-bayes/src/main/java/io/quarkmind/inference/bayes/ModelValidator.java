package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class ModelValidator {

    private ModelValidator() {}

    public static double logLikelihood(BayesNetwork model,
                                        List<Map<String, String>> data) {
        if (data.isEmpty()) throw new IllegalArgumentException("Data must not be empty");

        Map<String, VarInfo> varInfos = buildVarInfos(model);
        double ll = 0.0;

        for (Map<String, String> obs : data) {
            for (VarInfo info : varInfos.values()) {
                String value = obs.get(info.name);
                if (value == null) continue;
                int stateIdx = indexOf(info.outcomes, value);
                if (stateIdx < 0) continue;

                int parentConfig = parentConfigIndex(info, varInfos, obs);
                if (parentConfig < 0) continue;

                double prob = info.cpt[parentConfig][stateIdx];
                ll += prob > 0.0 ? Math.log(prob) : Double.NEGATIVE_INFINITY;
            }
        }
        return ll;
    }

    public static ValidationResult crossValidate(
            BayesNetwork structure,
            List<Map<String, String>> data,
            BiFunction<BayesNetwork, List<Map<String, String>>, BayesNetwork> fitter,
            FoldSplitter splitter,
            long seed) {
        if (data.isEmpty()) throw new IllegalArgumentException("Data must not be empty");

        List<FoldSplitter.Fold> folds = splitter.split(data, seed);
        var foldScores = new ArrayList<Double>();
        int totalObs = 0;

        for (FoldSplitter.Fold fold : folds) {
            BayesNetwork fitted = fitter.apply(structure, fold.train());
            double score = logLikelihood(fitted, fold.test());
            foldScores.add(score);
            totalObs += fold.test().size();
        }

        double mean = foldScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = foldScores.stream()
            .mapToDouble(s -> (s - mean) * (s - mean))
            .average().orElse(0.0);
        double std = Math.sqrt(variance);

        return new ValidationResult(mean, std, foldScores, folds.size(), totalObs);
    }

    static Map<String, VarInfo> buildVarInfos(BayesNetwork model) {
        Map<String, VarInfo> infos = new LinkedHashMap<>();
        for (GraphNode<BayesVariable> node : model) {
            BayesVariable<?> var = node.getContent();
            String[] outcomes = new String[var.getOutcomes().length];
            for (int i = 0; i < outcomes.length; i++) {
                outcomes[i] = String.valueOf(var.getOutcomes()[i]);
            }
            infos.put(var.getName(), new VarInfo(
                var.getName(), outcomes, var.getGiven(), var.getProbabilityTable()));
        }
        return infos;
    }

    static int parentConfigIndex(VarInfo var, Map<String, VarInfo> allVars,
                                  Map<String, String> obs) {
        if (var.parents.length == 0) return 0;
        int index = 0;
        for (int i = 0; i < var.parents.length; i++) {
            VarInfo parent = allVars.get(var.parents[i]);
            if (parent == null) return -1;
            String parentValue = obs.get(var.parents[i]);
            if (parentValue == null) return -1;
            int stateIdx = indexOf(parent.outcomes, parentValue);
            if (stateIdx < 0) return -1;
            int stride = 1;
            for (int j = i + 1; j < var.parents.length; j++) {
                VarInfo next = allVars.get(var.parents[j]);
                if (next != null) stride *= next.outcomes.length;
            }
            index += stateIdx * stride;
        }
        return index;
    }

    static int indexOf(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i].equals(value)) return i;
        }
        return -1;
    }

    record VarInfo(String name, String[] outcomes, String[] parents, double[][] cpt) {}
}
