package io.casehub.neocortex.memory.reflection;

import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import java.util.List;

public final class ReflectionQuery {

    private ReflectionQuery() {}

    public static MemoryQuery forAgent(String agentId, String tenantId) {
        return new MemoryQuery(
            List.of(agentId), ReflectionEvents.DOMAIN, tenantId,
            null, null, 30, null, MemoryOrder.CHRONOLOGICAL);
    }

    public static MemoryQuery search(String agentId, String tenantId, String question) {
        return new MemoryQuery(
            List.of(agentId), ReflectionEvents.DOMAIN, tenantId,
            null, question, 15, null, MemoryOrder.RELEVANCE);
    }

    public static MemoryQuery salient(String agentId, String tenantId) {
        return new MemoryQuery(
            List.of(agentId), ReflectionEvents.DOMAIN, tenantId,
            null, null, 15, null, MemoryOrder.SALIENCE);
    }
}
