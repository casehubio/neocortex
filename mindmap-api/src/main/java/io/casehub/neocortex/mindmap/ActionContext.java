package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.PadProjection;

import java.time.Instant;
import java.util.Objects;

public record ActionContext(
    String actingAgentId,
    String apprasingAgentId,
    String tenantId,
    String turnId,
    String actionDescription,
    String capability,
    ActionOutcome outcome,
    double goalRelevance,
    PadProjection moodBaseline,
    AppraisalWeights weights,
    Instant timestamp
) {
    public ActionContext {
        Objects.requireNonNull(actingAgentId, "actingAgentId required");
        Objects.requireNonNull(apprasingAgentId, "apprasingAgentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(turnId, "turnId required");
        Objects.requireNonNull(actionDescription, "actionDescription required");
        Objects.requireNonNull(outcome, "outcome required");
        Objects.requireNonNull(moodBaseline, "moodBaseline required");
        if (weights == null) weights = AppraisalWeights.NEUTRAL;
        Objects.requireNonNull(timestamp, "timestamp required");
        if (goalRelevance < -1.0 || goalRelevance > 1.0)
            throw new IllegalArgumentException("goalRelevance must be in [-1, 1], got " + goalRelevance);
    }

    public boolean isSelfAction() {
        return actingAgentId.equals(apprasingAgentId);
    }
}
