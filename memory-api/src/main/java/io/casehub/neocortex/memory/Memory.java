package io.casehub.neocortex.memory;

import io.casehub.neocortex.cognitive.Confidence;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record Memory(
        String memoryId,
        Subject subject,
        MemoryDomain domain,
        String tenantId,
        String caseId,
        String text,
        Map<String, String> attributes,
        Instant createdAt,
        Confidence confidence, Double pleasure, Double arousal, Double dominance,
        String principalId,
        Set<String> sharedWith) {
    public Memory {
        attributes = Map.copyOf(attributes);
        sharedWith = sharedWith != null ? Set.copyOf(sharedWith) : Set.of();
    }

    @Deprecated(forRemoval = true)
    public Memory(String memoryId, String entityId, MemoryDomain domain, String tenantId,
                  String caseId, String text, Map<String, String> attributes, Instant createdAt,
                  Confidence confidence, Double pleasure, Double arousal, Double dominance) {
        this(memoryId, Subject.of("unknown", entityId), domain, tenantId, caseId, text,
             attributes, createdAt, confidence, pleasure, arousal, dominance, null, null);
    }

    @Deprecated(forRemoval = true)
    public String entityId() {
        return subject.id();
    }
}
