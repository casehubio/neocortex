package io.casehub.neocortex.cognition.temporal;

import java.util.Set;

public interface TemporalFocusOrchestrator {
    void tick(String agentId, String tenantId, Set<String> relevantSubjects);
}
