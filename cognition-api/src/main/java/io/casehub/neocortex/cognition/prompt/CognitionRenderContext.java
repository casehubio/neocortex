package io.casehub.neocortex.cognition.prompt;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record CognitionRenderContext(
        String agentId,
        String tenantId,
        @Nullable String subjectId
) {
    public CognitionRenderContext {
        Objects.requireNonNull(agentId);
        Objects.requireNonNull(tenantId);
    }
}
