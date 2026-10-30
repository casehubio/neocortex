package io.casehub.neocortex.memory.seeding.biography;

public interface BiographyProfilePreprocessor {
    BiographyProfile preprocess(BiographyProfile profile, String agentId, String tenantId);

    default void afterImport(String agentId, String tenantId) {}
}
