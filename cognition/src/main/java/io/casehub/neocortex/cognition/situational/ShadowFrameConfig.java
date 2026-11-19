package io.casehub.neocortex.cognition.situational;

import java.util.List;
import java.util.Map;

public record ShadowFrameConfig(
    int maxFacets,
    double minDisplacement,
    double modeRelevanceBoost,
    double attractorRelevanceBoost,
    Map<String, List<String>> attractorFacetCorrelations
) {
    public ShadowFrameConfig {
        attractorFacetCorrelations = attractorFacetCorrelations != null
            ? Map.copyOf(attractorFacetCorrelations) : Map.of();
    }

    public static ShadowFrameConfig defaults() {
        return new ShadowFrameConfig(5, 0.05, 1.5, 1.3, Map.ofEntries(
            Map.entry("withdraw", List.of("withdrawal", "anxiousness", "intimacy-avoidance")),
            Map.entry("fight", List.of("hostility", "impulsivity", "risk-taking")),
            Map.entry("flight", List.of("anxiousness", "withdrawal")),
            Map.entry("fawn", List.of("attention-seeking")),
            Map.entry("freeze", List.of("anxiousness", "emotional-lability")),
            Map.entry("trust", List.of("suspiciousness")),
            Map.entry("distrust", List.of("suspiciousness", "intimacy-avoidance")),
            Map.entry("approach", List.of("risk-taking", "grandiosity")),
            Map.entry("assert", List.of("hostility", "grandiosity")),
            Map.entry("submit", List.of("withdrawal", "anxiousness"))
        ));
    }
}
