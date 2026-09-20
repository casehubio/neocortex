package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.cognitive.Confidence;

import java.util.Map;
import java.util.Objects;

public record CbrFeatureRecord(String problem, String solution,
                               String outcome, Confidence confidence,
                               Map<String, FeatureValue> features,
                               Double trustScore, String producerAgentId) implements CbrRecord {
    public static final String CBR_TYPE = "feature-vector";

    @Override
    public String recordType() {return CBR_TYPE;}

    public CbrFeatureRecord {
        Objects.requireNonNull(problem, "problem required");
        if (problem.isBlank()) {throw new IllegalArgumentException("problem must not be blank");}
        Objects.requireNonNull(solution, "solution required");
        if (solution.isBlank()) {throw new IllegalArgumentException("solution must not be blank");}
        if (trustScore != null && (trustScore < 0.0 || trustScore > 1.0)) {
            throw new IllegalArgumentException("trustScore must be in [0,1], got: " + trustScore);
        }
        Objects.requireNonNull(features, "features required");
        features = Map.copyOf(features);
    }

    @Override
    public CbrRecord withOutcome(String outcome, Confidence confidence) {
        return new CbrFeatureRecord(problem(), solution(), outcome, confidence, features(), trustScore(), producerAgentId());
    }

    @Override
    public CbrRecord withFeatures(Map<String, FeatureValue> features) {
        return new CbrFeatureRecord(problem(), solution(), outcome(), confidence(), features, trustScore(), producerAgentId());
    }

}
