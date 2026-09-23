package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.mindmap.GoalLifecycleProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@DefaultBean
@ApplicationScoped
public class NoOpGoalLifecycleProvider implements GoalLifecycleProvider {

    @Override
    public Map<String, String> getLifecycleStates(String agentId, String tenantId) {
        return Map.of();
    }
}
