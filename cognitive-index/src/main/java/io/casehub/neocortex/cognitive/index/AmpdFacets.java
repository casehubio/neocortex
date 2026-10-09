package io.casehub.neocortex.cognitive.index;

import java.util.Map;

public record AmpdFacets(Map<String, Integer> scores) {

    public static final String CALLOUSNESS = "callousness";
    public static final String MANIPULATIVENESS = "manipulativeness";
    public static final String GRANDIOSITY = "grandiosity";
    public static final String DECEITFULNESS = "deceitfulness";
    public static final String ATTENTION_SEEKING = "attention-seeking";
    public static final String HOSTILITY = "hostility";
    public static final String SUSPICIOUSNESS = "suspiciousness";
    public static final String INTIMACY_AVOIDANCE = "intimacy-avoidance";
    public static final String EMOTIONAL_LABILITY = "emotional-lability";
    public static final String ANXIOUSNESS = "anxiousness";
    public static final String RISK_TAKING = "risk-taking";
    public static final String IMPULSIVITY = "impulsivity";
    public static final String WITHDRAWAL = "withdrawal";

    public AmpdFacets {
        scores = scores != null ? Map.copyOf(scores) : Map.of();
    }

    public int score(String facet) {
        return scores.getOrDefault(facet, 50);
    }

    public boolean isHigh(String facet) {
        return score(facet) >= 70;
    }

    public boolean isLow(String facet) {
        return score(facet) <= 30;
    }

    public static AmpdFacets empty() {
        return new AmpdFacets(Map.of());
    }
}
