package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveIntensity;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface DriveGoalMapper {
    @Nullable DriveGoalProposal evaluate(String agentId, String tenantId, DriveIntensity intensity);
}
