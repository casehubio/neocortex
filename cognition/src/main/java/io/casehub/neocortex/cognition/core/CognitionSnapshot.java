package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.goal.DriveGoalProposal;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelSnapshot;
import io.casehub.neocortex.cognition.narrative.NarrativeState;
import io.casehub.neocortex.cognition.strategy.StrategyProfile;
import io.casehub.neocortex.cognition.usermodel.UserProfile;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.mindmap.AttentionBriefing;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CognitionSnapshot(
        String agentId,
        String tenantId,
        int turnNumber,
        Instant capturedAt,
        @Nullable MoodState mood,
        @Nullable DriveProfile drives,
        Map<String, MentalModelSnapshot> mentalModels,
        Map<String, UserProfile> userProfiles,
        @Nullable StrategyProfile strategy,
        @Nullable NarrativeState narrative,
        List<DriveGoalProposal> goalProposals,
        @Nullable AttentionBriefing lastBriefing
) {

    public CognitionDelta diffFrom(@Nullable CognitionSnapshot previous) {
        return CognitionDelta.compute(previous, this);
    }
}
