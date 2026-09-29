package io.casehub.neocortex.cognition.goal;

import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.narrative.NarrativeState;
import io.casehub.eidos.api.AgentDescriptor;

import java.util.Objects;

public record GoalEscalationContext(
        NarrativeState narrative,
        DriveProfile drives,
        AgentDescriptor descriptor) {
    public GoalEscalationContext {
        Objects.requireNonNull(narrative);
        Objects.requireNonNull(drives);
        Objects.requireNonNull(descriptor);
    }
}
