package io.casehub.neocortex.memory.reflection.runtime;

import io.casehub.neocortex.memory.Memory;
import java.time.Instant;
import java.util.List;

public record TrajectoryStep(String turnId, List<Memory> events, Instant timestamp) {
    public TrajectoryStep {
        events = List.copyOf(events);
    }
}
