package io.casehub.neocortex.cognition.personality;

import io.casehub.neocortex.memory.relationship.QualitySignal;

import java.time.Instant;

public record InteractionAppraised(
        String tenantId,
        String agentId,
        String subjectId,
        QualitySignal quality,
        Instant at
) {
    public InteractionAppraised {
        if (tenantId == null) throw new IllegalArgumentException("tenantId required");
        if (agentId == null) throw new IllegalArgumentException("agentId required");
        if (quality == null) throw new IllegalArgumentException("quality required");
        if (at == null) throw new IllegalArgumentException("at required");
    }
}
