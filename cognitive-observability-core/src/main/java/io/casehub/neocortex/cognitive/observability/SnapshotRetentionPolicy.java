package io.casehub.neocortex.cognitive.observability;

import java.time.Duration;

public record SnapshotRetentionPolicy(Duration maxAge, String tenantId) {}
