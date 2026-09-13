package io.casehub.neocortex.cognitive.index;

import java.util.Map;

public record EventImpact(
        Map<PadDimension, Double> meanDelta,
        Map<PadDimension, ConfidenceInterval> confidenceInterval,
        int eventCount,
        int totalEvents,
        Map<String, EventTypeImpact> byType
) {
    public EventImpact {
        meanDelta = Map.copyOf(meanDelta);
        confidenceInterval = Map.copyOf(confidenceInterval);
        byType = Map.copyOf(byType);
    }

    public static EventImpact empty() {
        return new EventImpact(Map.of(), Map.of(), 0, 0, Map.of());
    }
}
