package io.casehub.neocortex.cognition.memory;

import io.casehub.neocortex.memory.KnowledgeGapSummary;

public interface MemoryHygieneOrchestrator {
    void tick(String agentId, String tenantId);

    KnowledgeGapSummary knowledgeGaps(String agentId, String tenantId);
}
