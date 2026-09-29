package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.narrative.DerivedTheme;
import io.casehub.neocortex.cognition.narrative.NarrativeState;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface CrossAxisGoalEnricher {
    @Nullable DriveGoalProposal enrich(DriveGoalProposal heuristicProposal,
                                        NarrativeState narrative,
                                        DerivedTheme sourceTheme);
}
