package io.casehub.neocortex.cognitive.index;

import java.util.Map;

public record EventTypeImpact(
        Map<PadDimension, Double> meanDelta,
        Map<PadDimension, ConfidenceInterval> confidenceInterval,
        int eventCount
) {
    public EventTypeImpact {
        meanDelta = Map.copyOf(meanDelta);
        confidenceInterval = Map.copyOf(confidenceInterval);
    }
}
