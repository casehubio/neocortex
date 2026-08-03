package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.memory.MemoryOrder;
import io.casehub.neocortex.memory.MemoryQuery;
import java.util.List;

public final class ExperienceQuery {

    private ExperienceQuery() {}

    public static MemoryQuery forAgent(String agentId, String tenantId) {
        return new MemoryQuery(
            List.of(agentId), ExperienceEvents.DOMAIN, tenantId,
            null, null, 50, null, MemoryOrder.CHRONOLOGICAL);
    }

    public static MemoryQuery forAgentInCase(String agentId, String tenantId, String caseId) {
        return forAgent(agentId, tenantId).withCaseId(caseId);
    }

    public static MemoryQuery forAgents(List<String> agentIds, String tenantId) {
        return new MemoryQuery(
            agentIds, ExperienceEvents.DOMAIN, tenantId,
            null, null, 50, null, MemoryOrder.CHRONOLOGICAL);
    }

    public static MemoryQuery search(String agentId, String tenantId, String question) {
        return new MemoryQuery(
            List.of(agentId), ExperienceEvents.DOMAIN, tenantId,
            null, question, 20, null, MemoryOrder.RELEVANCE);
    }

    public static MemoryQuery salient(String agentId, String tenantId) {
        return new MemoryQuery(
            List.of(agentId), ExperienceEvents.DOMAIN, tenantId,
            null, null, 20, null, MemoryOrder.SALIENCE);
    }
}
