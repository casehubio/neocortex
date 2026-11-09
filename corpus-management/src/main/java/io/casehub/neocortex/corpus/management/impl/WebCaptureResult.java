package io.casehub.neocortex.corpus.management.impl;

public record WebCaptureResult(
    String entryId,
    String url,
    String title,
    String body,
    String fetchedAt,
    boolean cached
) {}
