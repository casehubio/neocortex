package io.casehub.neocortex.memory.reflection;

import io.casehub.neocortex.memory.Memory;
import java.util.List;

@FunctionalInterface
public interface ReflectionSynthesizer {
    List<ReflectionEvent> synthesize(String agentId, String tenantId,
        List<Memory> sources, int targetLevel);
}
