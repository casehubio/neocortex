package io.casehub.neocortex.cognitive.index;

import io.casehub.platform.api.identity.PrincipalId;

import java.time.Instant;
import java.util.Map;

public record DomainActivationResult(
        Map<String, DomainSignal> domains,
        Map<DomainPair, DomainCorrelation> correlations,
        PrincipalId principal,
        String tenantId,
        Instant from, Instant to
) {
    public DomainActivationResult {
        domains      = Map.copyOf(domains);
        correlations = Map.copyOf(correlations);
    }
}