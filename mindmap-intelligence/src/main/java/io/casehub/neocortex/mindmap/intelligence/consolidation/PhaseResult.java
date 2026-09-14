package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.time.Instant;

public record PhaseResult(String phaseName, Instant startedAt, Instant completedAt,
                           boolean success, String errorMessage) {}
