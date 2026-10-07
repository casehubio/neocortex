package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.eidos.api.GoalPriority;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record DeductiveGoalProposal(
        String goalName,
        String goalDescription,
        String reasoning,
        Map<DriveAxis, Double> driveContributions,
        @Nullable GoalPriority suggestedPriority,
        @Nullable Map<String, String> attributes) {

    public DeductiveGoalProposal {
        Objects.requireNonNull(goalName, "goalName required");
        Objects.requireNonNull(goalDescription, "goalDescription required");
        Objects.requireNonNull(reasoning, "reasoning required");
        driveContributions = driveContributions != null
                ? Map.copyOf(driveContributions) : Map.of();
        attributes = attributes != null ? Map.copyOf(attributes) : null;
    }

    public DriveAxis primaryAxis() {
        return driveContributions.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(DriveAxis.CURIOSITY);
    }

    public double primaryIntensity() {
        return driveContributions.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(0.5);
    }

    public DriveGoalProposal toDriveGoalProposal() {
        var attrs = new HashMap<String, String>();
        attrs.put("source", "deductive");
        attrs.put("reasoning", reasoning);
        if (attributes != null) attrs.putAll(attributes);
        return new DriveGoalProposal(
                primaryAxis(), goalName, goalDescription,
                "deductive: " + reasoning,
                primaryIntensity(), suggestedPriority, Map.copyOf(attrs));
    }
}
