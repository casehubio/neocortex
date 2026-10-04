package io.casehub.neocortex.knowledge;

import java.time.Instant;
import java.util.Objects;

public record ResearchSession(
    String id, String name, String criteria, String mindMapSubgraphId,
    ResearchState state, String tenantId, Instant createdAt, Instant lastActive
) {
    public ResearchSession {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(mindMapSubgraphId);
        Objects.requireNonNull(state);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(lastActive);
    }
}
