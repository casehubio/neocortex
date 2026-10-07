package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.eidos.api.AgentGoal;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record DeductiveFormationContext(
        String agentId,
        String tenantId,
        DriveProfile driveProfile,
        @Nullable DispositionAxes disposition,
        List<String> beliefs,
        List<String> recentMemories,
        @Nullable MoodState currentMood,
        List<AgentGoal> existingGoals,
        int remainingCapacity) {

    public DeductiveFormationContext {
        Objects.requireNonNull(agentId, "agentId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(driveProfile, "driveProfile required");
        beliefs = beliefs != null ? List.copyOf(beliefs) : List.of();
        recentMemories = recentMemories != null ? List.copyOf(recentMemories) : List.of();
        existingGoals = existingGoals != null ? List.copyOf(existingGoals) : List.of();
    }
}
