package io.casehub.neocortex.cognition.relationship;

@FunctionalInterface
public interface RelationshipStageConfigProvider {
    RelationshipStageConfig forAgent(String agentId);
}
