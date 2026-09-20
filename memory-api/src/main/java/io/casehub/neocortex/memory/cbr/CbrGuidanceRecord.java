package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.cognitive.Confidence;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CbrGuidanceRecord(String problem, String solution,
                                String outcome, Confidence confidence,
                                Map<String, FeatureValue> features,
                                List<CbrGuidanceStep> steps,
                                Double trustScore, String producerAgentId) implements CbrRecord {
    public static final String CBR_TYPE = "textual";

    @Override
    public String recordType() {return CBR_TYPE;}

    public CbrGuidanceRecord {
        Objects.requireNonNull(problem, "problem required");
        if (problem.isBlank()) {throw new IllegalArgumentException("problem must not be blank");}
        Objects.requireNonNull(solution, "solution required");
        if (solution.isBlank()) {throw new IllegalArgumentException("solution must not be blank");}
        if (trustScore != null && (trustScore < 0.0 || trustScore > 1.0)) {
            throw new IllegalArgumentException("trustScore must be in [0,1], got: " + trustScore);
        }
        Objects.requireNonNull(features, "features required");
        features = Map.copyOf(features);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public CbrGuidanceRecord(String problem, String solution,
                             String outcome, Confidence confidence,
                             Double trustScore, String producerAgentId) {
        this(problem, solution, outcome, confidence, Map.of(), List.of(), trustScore, producerAgentId);
    }

    @Override
    public CbrRecord withOutcome(String outcome, Confidence confidence) {
        return new CbrGuidanceRecord(problem(), solution(), outcome, confidence, features(), steps(), trustScore(), producerAgentId());
    }

    @Override
    public CbrRecord withFeatures(Map<String, FeatureValue> features) {
        return new CbrGuidanceRecord(problem(), solution(), outcome(), confidence(), features, steps(), trustScore(), producerAgentId());
    }

    public CbrGuidanceRecord withSteps(List<CbrGuidanceStep> steps) {
        return new CbrGuidanceRecord(problem(), solution(), outcome(), confidence(), features(), steps, trustScore(), producerAgentId());
    }
}
