package io.casehub.neocortex.entity.token;

import java.util.Objects;

public record TrieMatch(SurfaceForm form, int startOffset, int endOffset) {

    public TrieMatch {
        Objects.requireNonNull(form, "form");
        if (startOffset < 0) throw new IllegalArgumentException("startOffset must be >= 0");
        if (endOffset <= startOffset) throw new IllegalArgumentException("endOffset must be > startOffset");
    }

    int length() {
        return endOffset - startOffset;
    }
}
