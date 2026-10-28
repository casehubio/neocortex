package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.personality.PersonalityWeights;

import java.util.List;

public record PersonalityProfile(
        PersonalityWeights personality,
        Double personalityDominance,
        ExpressionStyleDefaults expressionStyles,
        AttachmentProfile attachment,
        List<PersonalityFacet> facets
) {
    public PersonalityProfile {
        facets = facets != null ? List.copyOf(facets) : List.of();
    }
}
