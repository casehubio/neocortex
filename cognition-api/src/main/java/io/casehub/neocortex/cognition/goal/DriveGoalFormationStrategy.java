package io.casehub.neocortex.cognition.goal;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface DriveGoalFormationStrategy {
    @Nullable DriveGoalProposal propose(DriveGoalFormationContext context);
}
