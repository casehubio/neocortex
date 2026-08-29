package io.casehub.neocortex.memory.cbr;

import io.casehub.neocortex.cognitive.Confidence;

import java.util.Map;
import java.util.Objects;

public record FeatureVectorCbrCase(String problem, String solution,
                                   String outcome, Confidence confidence,
                                   Map<String, FeatureValue> features,
                                   Double trustScore, String producerAgentId) implements CbrCase {
    public static final String CBR_TYPE = "feature-vector";

    @Override
    public String cbrType() {return CBR_TYPE;}

    public FeatureVectorCbrCase {
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
    public CbrCase withOutcome(String outcome, Confidence confidence) {
        return new FeatureVectorCbrCase(problem(), solution(), outcome, confidence, features(), trustScore(), producerAgentId());
    }

    @Override
    public CbrCase withFeatures(Map<String, FeatureValue> features) {
        return new FeatureVectorCbrCase(problem(), solution(), outcome(), confidence(), features, trustScore(), producerAgentId());
    }

}
