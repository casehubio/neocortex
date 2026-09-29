package io.casehub.neocortex.cognition.goal;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface GoalEscalationPolicy {
    @Nullable EscalationResult evaluate(DriveGoalProposal proposal,
                                         GoalEscalationContext context);
}
