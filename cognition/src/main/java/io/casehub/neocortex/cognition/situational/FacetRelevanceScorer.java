package io.casehub.neocortex.cognition.situational;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FacetRelevanceScorer {

    private FacetRelevanceScorer() {}

    public record ScoredFacet(
        String facet, double displacement, double relevance,
        boolean modeCharacteristic, boolean attractorCorrelated
    ) {}

    public static List<ScoredFacet> score(
            Map<String, Double> displacements,
            String activeMode,
            Set<String> modeProfileFacets,
            String dominantAttractor,
            ShadowFrameConfig config) {

        Set<String> attractorFacets = dominantAttractor != null
            ? Set.copyOf(config.attractorFacetCorrelations().getOrDefault(dominantAttractor, List.of()))
            : Set.of();

        return displacements.entrySet().stream()
            .filter(e -> Math.abs(e.getValue()) >= config.minDisplacement())
            .map(e -> {
                boolean modeBoosted = modeProfileFacets != null && modeProfileFacets.contains(e.getKey());
                boolean attractorBoosted = attractorFacets.contains(e.getKey());
                double modeBoost = modeBoosted ? config.modeRelevanceBoost() : 1.0;
                double attractorBoost = attractorBoosted ? config.attractorRelevanceBoost() : 1.0;
                double relevance = Math.abs(e.getValue()) * modeBoost * attractorBoost;
                return new ScoredFacet(e.getKey(), e.getValue(), relevance, modeBoosted, attractorBoosted);
            })
            .sorted(Comparator.comparingDouble(ScoredFacet::relevance).reversed())
            .limit(config.maxFacets())
            .toList();
    }
}
