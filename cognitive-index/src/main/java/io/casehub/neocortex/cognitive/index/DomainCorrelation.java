package io.casehub.neocortex.cognitive.index;

import java.util.List;

public record DomainCorrelation(
        double dtwSimilarity,
        List<DtwAlignment> alignment,
        int samplePairs,
        CorrelationStrength strength
) {
    public DomainCorrelation {
        alignment = List.copyOf(alignment);
    }
}