package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.List;

public record ExtractionRequested(
        String cleanedText,
        String tenantId,
        List<String> recentEntityNames,
        List<String> segmentNodeIds,
        PrincipalId principalId
) {
    public ExtractionRequested {
        recentEntityNames = recentEntityNames != null ? List.copyOf(recentEntityNames) : List.of();
        segmentNodeIds    = List.copyOf(segmentNodeIds);
    }
}
