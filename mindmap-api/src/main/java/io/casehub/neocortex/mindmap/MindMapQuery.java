package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.platform.api.identity.PrincipalId;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record MindMapQuery(
        String tenantId,
        String subgraphId,
        String type,
        String text,
        String edgeType,
        Set<String> traits,
        Double minConfidence,
        ConfidenceOrigin confidenceOrigin,
        boolean includeSuperseded,
        Instant validAfter, Instant validBefore, Instant updatedAfter, int limit,
        PrincipalId callerPrincipal
) {

    public MindMapQuery {
        Objects.requireNonNull(tenantId, "tenantId");
        if (limit <= 0) {throw new IllegalArgumentException("limit must be positive");}
        traits = traits == null ? null : Set.copyOf(traits);
    }

    public static MindMapQuery of(String tenantId, int limit) {
        return new MindMapQuery(tenantId, null, null, null, null, null,
                                null, null, false, null, null, null, limit, null);
    }

    public MindMapQuery withSubgraphId(String subgraphId) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withType(String type) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withText(String text) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withEdgeType(String edgeType) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withTraits(Set<String> traits) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withMinConfidence(Double minConfidence) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withConfidenceOrigin(ConfidenceOrigin confidenceOrigin) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withIncludeSuperseded(boolean includeSuperseded) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withValidAfter(Instant validAfter) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withValidBefore(Instant validBefore) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withUpdatedAfter(Instant updatedAfter) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }

    public MindMapQuery withCallerPrincipal(PrincipalId callerPrincipal) {
        return new MindMapQuery(tenantId, subgraphId, type, text, edgeType, traits,
                                minConfidence, confidenceOrigin, includeSuperseded,
                                validAfter, validBefore, updatedAfter, limit, callerPrincipal);
    }
}
