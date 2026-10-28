package io.casehub.neocortex.cognitive.index;

public record PersonalityFacet(
        String facet,
        double score,
        String origin
) {
    public static final String CALLOUSNESS = "Callousness";
    public static final String MANIPULATIVENESS = "Manipulativeness";
    public static final String GRANDIOSITY = "Grandiosity";
    public static final String DECEITFULNESS = "Deceitfulness";
    public static final String ATTENTION_SEEKING = "Attention Seeking";
    public static final String HOSTILITY = "Hostility";
    public static final String SUSPICIOUSNESS = "Suspiciousness";
    public static final String INTIMACY_AVOIDANCE = "Intimacy Avoidance";
    public static final String EMOTIONAL_LABILITY = "Emotional Lability";
    public static final String ANXIOUSNESS = "Anxiousness";
    public static final String RISK_TAKING = "Risk Taking";
    public static final String IMPULSIVITY = "Impulsivity";
    public static final String WITHDRAWAL = "Withdrawal";

    public PersonalityFacet {
        if (score < 0.0 || score > 1.0)
            throw new IllegalArgumentException("score must be in [0,1], got " + score);
    }

    public boolean isHigh() { return score >= 0.7; }
    public boolean isLow() { return score <= 0.3; }
}
