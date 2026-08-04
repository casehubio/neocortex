package io.casehub.neocortex.memory.reflection;

import java.time.Instant;
import java.util.List;

public interface ReflectionOrchestrator {
    List<String> reflect(String agentId, String tenantId, Instant since, int maxSourceMemories);
}
