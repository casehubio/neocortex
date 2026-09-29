package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.eidos.api.AgentGoal;

import java.util.List;
import java.util.Objects;

public record DriveGoalFormationContext(
        String agentId,
        String tenantId,
        DriveAxis axis,
        double intensity,
        String trigger,
        List<AgentGoal> existingGoals,
        int remainingCapacity) {
    public DriveGoalFormationContext {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(axis, "axis required");
        Objects.requireNonNull(trigger, "trigger required");
        Objects.requireNonNull(existingGoals, "existingGoals required");
        existingGoals = List.copyOf(existingGoals);
        if (intensity < 0.0 || intensity > 1.0) {
            throw new IllegalArgumentException("intensity must be in [0.0, 1.0]");
        }
    }
}
