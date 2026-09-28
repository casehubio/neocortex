package io.casehub.neocortex.memory.reflection.runtime;

import java.util.List;

public record Trajectory(String caseId, List<TrajectoryStep> steps, TrajectoryOutcome outcome) {
    public Trajectory {
        steps = List.copyOf(steps);
    }
}
