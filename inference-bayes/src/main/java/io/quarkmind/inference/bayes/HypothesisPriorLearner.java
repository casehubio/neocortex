package io.quarkmind.inference.bayes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HypothesisPriorLearner {

    private final Map<String, Double> counts;

    public HypothesisPriorLearner(Set<String> hypotheses, double smoothing) {
        if (hypotheses == null || hypotheses.isEmpty()) {
            throw new IllegalArgumentException("At least one hypothesis is required");
        }
        if (smoothing < 0) {
            throw new IllegalArgumentException("Smoothing must be non-negative: " + smoothing);
        }
        this.counts = new LinkedHashMap<>();
        for (String h : hypotheses) {
            counts.put(h, smoothing);
        }
    }

    public void update(String outcome) {
        if (!counts.containsKey(outcome)) {
            throw new IllegalArgumentException("Unknown hypothesis: " + outcome);
        }
        counts.put(outcome, counts.get(outcome) + 1);
    }

    public Map<String, Double> priors() {
        double sum = 0;
        for (double c : counts.values()) {
            sum += c;
        }
        var result = new LinkedHashMap<String, Double>();
        for (var entry : counts.entrySet()) {
            result.put(entry.getKey(), entry.getValue() / sum);
        }
        return Map.copyOf(result);
    }

    public static Map<String, Double> fromOutcomes(List<String> outcomes) {
        if (outcomes.isEmpty()) {
            throw new IllegalArgumentException("Outcomes list must not be empty");
        }
        var counts = new LinkedHashMap<String, Double>();
        for (String o : outcomes) {
            counts.merge(o, 1.0, Double::sum);
        }
        double sum = outcomes.size();
        var result = new LinkedHashMap<String, Double>();
        for (var entry : counts.entrySet()) {
            result.put(entry.getKey(), entry.getValue() / sum);
        }
        return Map.copyOf(result);
    }

    public static Map<String, Double> fromOutcomes(List<String> outcomes,
                                                    Set<String> allHypotheses,
                                                    double smoothing) {
        var learner = new HypothesisPriorLearner(allHypotheses, smoothing);
        for (String o : outcomes) {
            learner.update(o);
        }
        return learner.priors();
    }
}
