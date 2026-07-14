package io.casehub.neocortex.memory.cbr;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AdaptationTrace(
    String traceId,
    String retrievalTraceId,
    String sourceCaseId,
    double sourceScore,
    List<AdaptedStep> steps,
    Map<String, FeatureValue> currentFeatures,
    Instant timestamp
) {
    public AdaptationTrace {
        Objects.requireNonNull(traceId, "traceId");
        Objects.requireNonNull(steps, "steps");
        steps = List.copyOf(steps);
        Objects.requireNonNull(currentFeatures, "currentFeatures");
        currentFeatures = Map.copyOf(currentFeatures);
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
