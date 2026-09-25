package io.casehub.neocortex.mindmap;

import java.time.Instant;
import java.util.List;

public record AttentionBriefing(
    String principalId,
    String tenantId,
    List<AttentionSignal> signals,
    double urgencyP75,
    Instant generatedAt
) {
    public AttentionBriefing {
        signals = List.copyOf(signals);
    }

    public List<AttentionSignal> topN(int n) {
        return signals.subList(0, Math.min(n, signals.size()));
    }
}
