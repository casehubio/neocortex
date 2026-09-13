package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.platform.api.identity.PrincipalId;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record DomainActivationQuery(
        PrincipalId principal,
        Set<String> subgraphIds,
        String tenantId,
        Instant from,
        Instant to,
        Duration bucketDuration,
        Set<MemoryDomain> contextDomains,
        Duration eventWindow
) {
    public DomainActivationQuery {
        Objects.requireNonNull(principal, "principal required");
        Objects.requireNonNull(tenantId, "tenantId required");
        if (contextDomains == null) contextDomains = Set.of();
        contextDomains = Set.copyOf(contextDomains);
        if (subgraphIds == null || (subgraphIds.size() < 2 && contextDomains.isEmpty())) {
            throw new IllegalArgumentException(
                "at least 2 subgraphIds required (or 1 with contextDomains)");
        }
        subgraphIds = Set.copyOf(subgraphIds);
        if (bucketDuration == null) {bucketDuration = Duration.ofHours(24);}
    }

    public static DomainActivationQuery between(
            PrincipalId principal, String tenantId,
            String subgraphA, String subgraphB) {
        return new DomainActivationQuery(principal, Set.of(subgraphA, subgraphB),
                                         tenantId, null, null, null, Set.of(), null);
    }

    public DomainActivationQuery withFrom(Instant from) {
        return new DomainActivationQuery(principal, subgraphIds, tenantId, from, to, bucketDuration, contextDomains, eventWindow);
    }

    public DomainActivationQuery withTo(Instant to) {
        return new DomainActivationQuery(principal, subgraphIds, tenantId, from, to, bucketDuration, contextDomains, eventWindow);
    }

    public DomainActivationQuery withBucketDuration(Duration bucketDuration) {
        return new DomainActivationQuery(principal, subgraphIds, tenantId, from, to, bucketDuration, contextDomains, eventWindow);
    }

    public DomainActivationQuery withContextDomains(Set<MemoryDomain> contextDomains) {
        return new DomainActivationQuery(principal, subgraphIds, tenantId, from, to, bucketDuration, contextDomains, eventWindow);
    }

    public DomainActivationQuery withEventWindow(Duration eventWindow) {
        return new DomainActivationQuery(principal, subgraphIds, tenantId, from, to, bucketDuration, contextDomains, eventWindow);
    }
}