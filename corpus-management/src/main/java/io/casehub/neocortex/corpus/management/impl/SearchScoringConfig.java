package io.casehub.neocortex.corpus.management.impl;

public record SearchScoringConfig(
        boolean temporalDecayEnabled,
        boolean versionScoringEnabled,
        double versionDecayFactor,
        double versionDecayFloor,
        double versionTopicWeightDefault) {

    public static SearchScoringConfig defaults() {
        return new SearchScoringConfig(true, true, 0.03, 0.5, 0.3);
    }
}
