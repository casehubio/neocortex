package io.casehub.neocortex.mindmap;

import java.util.Map;

@FunctionalInterface
public interface GoalLifecycleProvider {
    Map<String, String> getLifecycleStates(String agentId, String tenantId);
}
