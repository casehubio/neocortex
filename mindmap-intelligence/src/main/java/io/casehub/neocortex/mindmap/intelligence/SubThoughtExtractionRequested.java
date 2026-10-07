package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.platform.api.identity.PrincipalId;

import java.util.Objects;

public record SubThoughtExtractionRequested(
        String memoryId,
        String tenantId,
        String experienceText,
        PrincipalId principalId
) {
    public SubThoughtExtractionRequested {
        Objects.requireNonNull(memoryId, "memoryId required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(experienceText, "experienceText required");
    }
}
