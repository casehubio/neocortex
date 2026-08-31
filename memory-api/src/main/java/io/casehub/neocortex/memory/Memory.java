package io.casehub.neocortex.memory;

import io.casehub.neocortex.cognitive.Confidence;

import java.time.Instant;
import java.util.Map;

public record Memory(
        String memoryId,
        String entityId,
        MemoryDomain domain,
        String tenantId,
        String caseId,
        String text,
        Map<String, String> attributes,
        Instant createdAt,
        Confidence confidence, Double pleasure, Double arousal, Double dominance) {
    public Memory {
        attributes = Map.copyOf(attributes);
    }
}
