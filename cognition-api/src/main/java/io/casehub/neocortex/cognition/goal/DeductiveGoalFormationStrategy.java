package io.casehub.neocortex.cognition.goal;

import java.util.List;

@FunctionalInterface
public interface DeductiveGoalFormationStrategy {
    List<DeductiveGoalProposal> propose(DeductiveFormationContext context);
}
