package io.casehub.neocortex.cognition.drive;

@FunctionalInterface
public interface DriveSource {
    DriveIntensity evaluate(String agentId, String tenantId);
}
