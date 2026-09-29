package io.casehub.neocortex.cognition.memory;

public interface MemoryHygieneOrchestrator {
    void tick(String agentId, String tenantId);
}
