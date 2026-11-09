package io.casehub.neocortex.corpus.management.impl;

import java.time.Instant;

public record InterventionRequest(
    String id,
    String url,
    String reason,
    String screenshotRef,
    Instant submittedAt
) {
    public InterventionRequest {
        if (id == null) throw new IllegalArgumentException("id must not be null");
        if (url == null) throw new IllegalArgumentException("url must not be null");
        if (reason == null) reason = "";
        if (submittedAt == null) submittedAt = Instant.now();
    }
}
