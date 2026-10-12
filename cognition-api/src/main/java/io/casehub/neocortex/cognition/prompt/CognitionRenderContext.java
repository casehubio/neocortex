package io.casehub.neocortex.cognition.prompt;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record CognitionRenderContext(
        String agentId,
        String tenantId,
        @Nullable String subjectId,
        java.util.List<String> activatedDriveCategories
) {
    public CognitionRenderContext {
        Objects.requireNonNull(agentId);
        Objects.requireNonNull(tenantId);
        activatedDriveCategories = activatedDriveCategories != null
                                   ? java.util.List.copyOf(activatedDriveCategories) : java.util.List.of();
    }

    public CognitionRenderContext(String agentId, String tenantId,
                                  @Nullable String subjectId) {
        this(agentId, tenantId, subjectId, java.util.List.of());
    }

    public boolean isDriveActivated(String category) {
        return !activatedDriveCategories.isEmpty() && activatedDriveCategories.contains(category);
    }
}
