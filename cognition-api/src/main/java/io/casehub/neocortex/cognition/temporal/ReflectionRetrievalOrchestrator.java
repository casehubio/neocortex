package io.casehub.neocortex.cognition.temporal;

import io.casehub.neocortex.memory.ReflectionEntry;

import java.util.List;

public interface ReflectionRetrievalOrchestrator {
    void tick(String agentId, String tenantId);

    List<ReflectionEntry> lastReflections();
}
