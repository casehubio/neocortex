package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.PadProjection;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AppraisalContext(
        String tenantId,
        String agentId,
        PadProjection moodBaseline,
        int surfacingCount,
        Instant lastProgressAt,
        Instant lastSurfacedAt,
        Map<String, Double> relationshipScores,
        AppraisalWeights weights
) {
    public AppraisalContext {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(moodBaseline, "moodBaseline required");
        relationshipScores = Map.copyOf(relationshipScores);
        if (weights == null) weights = AppraisalWeights.NEUTRAL;
    }
}
