package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.platform.api.identity.PrincipalId;

import java.util.Objects;
import java.util.Set;

public record CognitiveProfileQuery(
        String nodeId,
        String entityName,
        String subgraphId,
        String tenantId,
        Set<MemoryDomain> domains,
        boolean includeEdges,
        int memoryLimit,
        PrincipalId asSeenBy
) {

    public CognitiveProfileQuery {
        Objects.requireNonNull(tenantId, "tenantId required");
        if (nodeId == null && entityName == null) {
            throw new IllegalArgumentException("nodeId or entityName required");
        }
        if (nodeId != null && entityName != null) {
            throw new IllegalArgumentException("nodeId and entityName are mutually exclusive");
        }
        if (memoryLimit < 1) {
            throw new IllegalArgumentException("memoryLimit must be >= 1, got: " + memoryLimit);
        }
        domains = domains == null ? Set.of() : Set.copyOf(domains);
    }

    public static CognitiveProfileQuery byId(String nodeId, String tenantId) {
        Objects.requireNonNull(nodeId, "nodeId required");
        return new CognitiveProfileQuery(nodeId, null, null, tenantId, Set.of(), true, 50, null);
    }

    public static CognitiveProfileQuery byName(String entityName, String tenantId) {
        Objects.requireNonNull(entityName, "entityName required");
        return new CognitiveProfileQuery(null, entityName, null, tenantId, Set.of(), true, 50, null);
    }

    public static CognitiveProfileQuery byName(String entityName, String subgraphId, String tenantId) {
        Objects.requireNonNull(entityName, "entityName required");
        return new CognitiveProfileQuery(null, entityName, subgraphId, tenantId, Set.of(), true, 50, null);
    }

    public CognitiveProfileQuery withDomains(Set<MemoryDomain> domains) {
        return new CognitiveProfileQuery(nodeId, entityName, subgraphId, tenantId, domains, includeEdges, memoryLimit, asSeenBy);
    }

    public CognitiveProfileQuery withIncludeEdges(boolean includeEdges) {
        return new CognitiveProfileQuery(nodeId, entityName, subgraphId, tenantId, domains, includeEdges, memoryLimit, asSeenBy);
    }

    public CognitiveProfileQuery withMemoryLimit(int memoryLimit) {
        return new CognitiveProfileQuery(nodeId, entityName, subgraphId, tenantId, domains, includeEdges, memoryLimit, asSeenBy);
    }

    public CognitiveProfileQuery withAsSeenBy(PrincipalId principal) {
        return new CognitiveProfileQuery(nodeId, entityName, subgraphId, tenantId, domains, includeEdges, memoryLimit, principal);
    }
}
