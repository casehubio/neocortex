package io.casehub.neocortex.cognition.core;

@FunctionalInterface
public interface InteractionMapper {
    CognitiveImpact mapInteraction(String agentId, String targetId,
                                    String interactionType);
}
