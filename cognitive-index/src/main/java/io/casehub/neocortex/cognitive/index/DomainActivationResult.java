package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.platform.api.identity.PrincipalId;

import java.time.Instant;
import java.util.Map;

public record DomainActivationResult(
        Map<String, DomainSignal> domains,
        Map<DomainPair, DomainCorrelation> correlations,
        Map<MemoryDomain, Map<String, DomainCorrelation>> contextCorrelations,
        Map<MemoryDomain, Map<String, EventImpact>> eventImpacts,
        PrincipalId principal,
        String tenantId,
        Instant from, Instant to
) {
    public DomainActivationResult {
        domains      = Map.copyOf(domains);
        correlations = Map.copyOf(correlations);
        contextCorrelations = contextCorrelations != null ? Map.copyOf(contextCorrelations) : Map.of();
        eventImpacts = eventImpacts != null ? Map.copyOf(eventImpacts) : Map.of();
    }
}