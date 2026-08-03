package io.casehub.neocortex.memory.relationship;

import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import java.util.List;

public final class RelationshipQuery {

    private RelationshipQuery() {}

    public static MemoryQuery forPair(String agentId, String otherAgentId, String tenantId) {
        return new MemoryQuery(
            List.of(agentId, otherAgentId), RelationshipEvents.DOMAIN, tenantId,
            null, null, 50, null, MemoryOrder.CHRONOLOGICAL);
    }

    public static MemoryQuery forAgent(String agentId, String tenantId) {
        return new MemoryQuery(
            List.of(agentId), RelationshipEvents.DOMAIN, tenantId,
            null, null, 50, null, MemoryOrder.CHRONOLOGICAL);
    }

    public static MemoryQuery search(String agentId, String tenantId, String question) {
        return new MemoryQuery(
            List.of(agentId), RelationshipEvents.DOMAIN, tenantId,
            null, question, 20, null, MemoryOrder.RELEVANCE);
    }
}
