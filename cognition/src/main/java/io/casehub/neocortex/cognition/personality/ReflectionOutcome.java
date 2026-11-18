package io.casehub.neocortex.cognition.personality;

import java.time.Instant;
import java.util.List;

public sealed interface ReflectionOutcome {

    record Stable() implements ReflectionOutcome {}

    record Drifting(double magnitude) implements ReflectionOutcome {}

    record Evolved(String previousLabel, String newLabel,
                   List<io.casehub.eidos.api.DispositionValue> newProfile,
                   String triggerType) implements ReflectionOutcome {
        public Evolved {
            newProfile = newProfile != null ? List.copyOf(newProfile) : List.of();
        }
    }

    record Dampened(double decayFactor) implements ReflectionOutcome {}

    record Deferred(String previousLabel, String newLabel,
                    String triggerType, Instant eligibleAt) implements ReflectionOutcome {}

    record Disabled(double relationalComplexity) implements ReflectionOutcome {}

    record Failed(String step, String message) implements ReflectionOutcome {}
}
