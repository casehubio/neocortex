package io.casehub.neocortex.corpus.management.impl;

import java.time.Instant;

public record ContributionRecord(String entryId, String title, String action,
                                  Instant timestamp, String sessionId) {}
