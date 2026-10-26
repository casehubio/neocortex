package io.quarkmind.inference.bayes;

import io.quarkmind.inference.bayes.graph.GraphNode;

import java.util.List;
import java.util.Map;

public final class ModelSelector {

    private ModelSelector() {}

    public static ModelScore bic(BayesNetwork model,
                                  List<Map<String, String>> data) {
        if (data.isEmpty()) throw new IllegalArgumentException("Data must not be empty");
        double ll = ModelValidator.logLikelihood(model, data);
        int k = freeParameterCount(model);
        int n = data.size();
        double score = -2.0 * ll + k * Math.log(n);
        return new ModelScore(score, ll, k, n, "BIC");
    }

    public static ModelScore aic(BayesNetwork model,
                                  List<Map<String, String>> data) {
        if (data.isEmpty()) throw new IllegalArgumentException("Data must not be empty");
        double ll = ModelValidator.logLikelihood(model, data);
        int k = freeParameterCount(model);
        int n = data.size();
        double score = -2.0 * ll + 2.0 * k;
        return new ModelScore(score, ll, k, n, "AIC");
    }

    public static int freeParameterCount(BayesNetwork model) {
        int count = 0;
        for (GraphNode<BayesVariable> node : model) {
            BayesVariable<?> var = node.getContent();
            int numOutcomes = var.getOutcomes().length;
            int parentConfigs = var.getProbabilityTable().length;
            count += parentConfigs * (numOutcomes - 1);
        }
        return count;
    }

    public static ModelComparison compare(
            BayesNetwork modelA, String nameA,
            BayesNetwork modelB, String nameB,
            List<Map<String, String>> data) {
        ModelScore scoreA = bic(modelA, data);
        ModelScore scoreB = bic(modelB, data);
        String preferred = scoreA.score() <= scoreB.score() ? nameA : nameB;
        double diff = Math.abs(scoreA.score() - scoreB.score());
        return new ModelComparison(scoreA, scoreB, preferred, diff);
    }
}
