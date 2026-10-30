package io.casehub.neocortex.caps;

import java.util.List;

public record ClassificationResult(
    List<SituationActivation> activations,
    String caseId
) {
    public ClassificationResult {
        activations = List.copyOf(activations);
    }

    public ClassificationResult(List<SituationActivation> activations) {
        this(activations, null);
    }
}
