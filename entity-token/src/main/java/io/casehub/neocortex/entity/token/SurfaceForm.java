package io.casehub.neocortex.entity.token;

import java.util.Objects;

public record SurfaceForm(String text, String variant, String entityId) {

    public SurfaceForm {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(entityId, "entityId");
    }
}
