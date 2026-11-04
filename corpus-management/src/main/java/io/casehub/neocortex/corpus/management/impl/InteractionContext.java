package io.casehub.neocortex.corpus.management.impl;

import java.util.List;

public record InteractionContext(
    String agentId,
    String tenantId,
    InteractionType interactionType,
    ActivityType activityType,
    String domain,
    List<String> participants,
    int turnCount,
    String brief,
    String artifactRef,
    List<String> relatedNodeIds
) {
    public InteractionContext {
        if (agentId == null || agentId.isBlank())
            throw new IllegalArgumentException("agentId must not be null or blank");
        if (tenantId == null || tenantId.isBlank())
            throw new IllegalArgumentException("tenantId must not be null or blank");
        if (interactionType == null)
            throw new IllegalArgumentException("interactionType must not be null");
        if (activityType == null)
            throw new IllegalArgumentException("activityType must not be null");
        if (artifactRef == null || artifactRef.isBlank())
            throw new IllegalArgumentException("artifactRef must not be null or blank");
        if (brief == null || brief.isBlank())
            throw new IllegalArgumentException("brief must not be null or blank");
        participants = participants != null ? List.copyOf(participants) : List.of();
        relatedNodeIds = relatedNodeIds != null ? List.copyOf(relatedNodeIds) : List.of();
    }

    public InteractionContext(String agentId, String tenantId, InteractionType interactionType,
                              ActivityType activityType, String domain, List<String> participants,
                              int turnCount, String brief, String artifactRef) {
        this(agentId, tenantId, interactionType, activityType, domain, participants,
             turnCount, brief, artifactRef, List.of());
    }
}
