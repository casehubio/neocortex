package io.casehub.neocortex.memory;

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
    Double importance) {
    public Memory {
        attributes = Map.copyOf(attributes);
    }
}
