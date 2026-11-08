package io.casehub.neocortex.corpus.management.impl;

import java.time.Instant;

public record EntrySummary(String id, String title, Instant lastModified, int versionCount) {}
