package io.casehub.neocortex.knowledge;

public enum MatchTier {
    DEFINITIVE, HIGH, MEDIUM, LOW;

    public static MatchTier fromConfidence(double confidence) {
        if (confidence >= 0.95) return DEFINITIVE;
        if (confidence >= 0.8) return HIGH;
        if (confidence >= 0.6) return MEDIUM;
        return LOW;
    }
}
