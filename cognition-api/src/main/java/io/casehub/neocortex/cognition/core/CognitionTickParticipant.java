package io.casehub.neocortex.cognition.core;

@FunctionalInterface
public interface CognitionTickParticipant {
    void tick(CognitionTickContext context);
}
