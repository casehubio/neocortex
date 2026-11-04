package io.casehub.neocortex.entity.token;

import java.util.Objects;

public record ParsedToken(
    String entityId,
    String variant,
    String surfaceForm,
    int startOffset,
    int endOffset
) {

    public ParsedToken {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(surfaceForm, "surfaceForm");
        if (startOffset < 0) throw new IllegalArgumentException("startOffset must be >= 0");
        if (endOffset <= startOffset) throw new IllegalArgumentException("endOffset must be > startOffset");
    }
}
