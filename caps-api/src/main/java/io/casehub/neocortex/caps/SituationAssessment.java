package io.casehub.neocortex.caps;

import java.util.List;

public record SituationAssessment(
    String reasoning,
    List<InlineActivation> activations
) {
    public record InlineActivation(
        String node,
        double confidence,
        int[] source
    ) {}
}
