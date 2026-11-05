package io.casehub.neocortex.cognition.regulation;

public record CognitiveLoadEstimate(
        CognitiveLoadLevel level,
        int activeDrives,
        int activeGoals,
        int mentalModelTargets
) {

    public int totalLoad() {
        return activeDrives + activeGoals + mentalModelTargets;
    }
}
