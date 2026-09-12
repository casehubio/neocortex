package io.casehub.neocortex.cognitive.index;

public enum CorrelationStrength {
    STRONG, MODERATE, WEAK, NONE;

    public static CorrelationStrength fromSimilarity(double s) {
        if (s >= 0.7) {return STRONG;}
        if (s >= 0.4) {return MODERATE;}
        if (s >= 0.2) {return WEAK;}
        return NONE;
    }
}