package io.casehub.neocortex.cognition.goal;

public record GoalRevision(String goalNodeId, String goalName,
                            String decaySignal, String eidosGoalName) {}
