package io.casehub.neocortex.cognitive.index;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class PersonalityMetadataEnricher {

    private PersonalityMetadataEnricher() {}

    public static Map<String, String> enrich(Map<String, String> metadata,
                                              String agentId,
                                              CognitiveDefaultsRegistry registry) {
        return enrich(metadata, agentId, registry, null);
    }

    public static Map<String, String> enrich(Map<String, String> metadata,
                                              String agentId,
                                              CognitiveDefaultsRegistry registry,
                                              Set<String> allowedFacets) {
        if (registry == null) {return metadata;}
        var defaults = registry.forAgent(agentId);
        if (defaults.isEmpty() || defaults.get().descriptor() == null) {return metadata;}

        var enriched = new HashMap<>(metadata);

        var axes = defaults.get().descriptor().disposition();
        if (axes != null) {
            if (axes.socialOrient() != null) {enriched.put("disposition.socialOrient", axes.socialOrient());}
            if (axes.ruleFollowing() != null) {enriched.put("disposition.ruleFollowing", axes.ruleFollowing());}
            if (axes.riskAppetite() != null) {enriched.put("disposition.riskAppetite", axes.riskAppetite());}
            if (axes.autonomy() != null) {enriched.put("disposition.autonomy", axes.autonomy());}
            if (axes.conflictMode() != null) {enriched.put("disposition.conflictMode", axes.conflictMode());}
        }

        var facets = defaults.get().descriptor().personalityFacets();
        if (facets != null) {
            for (var facet : facets) {
                if (allowedFacets == null || allowedFacets.contains(facet.facet())) {
                    enriched.put("pid5." + facet.facet(), String.format("%.2f", facet.score()));
                }
            }
        }

        var aw = defaults.get().appraisalWeights();
        if (aw != null) {
            enriched.put("appraisal.urgencyWeight", String.format("%.2f", aw.urgencyWeight()));
            enriched.put("appraisal.relationshipWeight", String.format("%.2f", aw.relationshipWeight()));
            enriched.put("appraisal.fearOnsetThreshold", String.format("%.2f", aw.fearOnsetThreshold()));
            enriched.put("appraisal.selfStandardsStrictness", String.format("%.2f", aw.selfStandardsStrictness()));
            enriched.put("appraisal.otherStandardsStrictness", String.format("%.2f", aw.otherStandardsStrictness()));
        }

        return enriched;
    }
}
