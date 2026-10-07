package io.casehub.neocortex.cognition.goal;

import java.util.List;

public class NoOpDeductiveGoalFormationStrategy implements DeductiveGoalFormationStrategy {
    @Override
    public List<DeductiveGoalProposal> propose(DeductiveFormationContext context) {
        return List.of();
    }
}
