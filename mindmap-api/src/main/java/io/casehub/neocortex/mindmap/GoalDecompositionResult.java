package io.casehub.neocortex.mindmap;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record GoalDecompositionResult(
        List<SubGoal> subGoals,
        List<GoalRelationship> relationships) {

    public static final GoalDecompositionResult EMPTY =
            new GoalDecompositionResult(List.of(), List.of());

    public GoalDecompositionResult {
        subGoals = List.copyOf(subGoals);
        relationships = List.copyOf(relationships);
    }

    public record SubGoal(
            String description,
            String suggestedHorizon,
            Map<String, String> properties) {
        public SubGoal {
            Objects.requireNonNull(description, "description");
            properties = properties != null ? Map.copyOf(properties) : Map.of();
        }
    }

    public record GoalRelationship(
            String sourceDescription,
            String targetDescription,
            String edgeType) {
        public GoalRelationship {
            Objects.requireNonNull(sourceDescription, "sourceDescription");
            Objects.requireNonNull(targetDescription, "targetDescription");
            Objects.requireNonNull(edgeType, "edgeType");
        }
    }
}
