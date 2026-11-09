package io.casehub.neocortex.corpus.management.impl;

public record InterventionResult(
    String id,
    String url,
    String reason,
    String screenshotRef,
    String submittedAt
) {}
