package io.casehub.neocortex.memory;

import java.util.Map;
import java.util.Objects;

public record MemoryInput(
    String entityId,
    MemoryDomain domain,
    String tenantId,
    String caseId,
    String text,
    Map<String, String> attributes
) {
    public MemoryInput {
        Objects.requireNonNull(entityId,  "entityId required");
        Objects.requireNonNull(domain,    "domain required");
        Objects.requireNonNull(tenantId,  "tenantId required");
        Objects.requireNonNull(text,      "text required");
        if (text.isBlank()) throw new IllegalArgumentException("text must not be blank");
        Objects.requireNonNull(attributes, "attributes required");
        attributes = Map.copyOf(attributes);
    }

    public MemoryInput withAttribute(String key, String value) {
        var merged = new java.util.HashMap<>(attributes);
        merged.put(key, value);
        return new MemoryInput(entityId, domain, tenantId, caseId, text, merged);
    }

    public MemoryInput withAttributes(Map<String, String> additional) {
        var merged = new java.util.HashMap<>(attributes);
        merged.putAll(additional);
        return new MemoryInput(entityId, domain, tenantId, caseId, text, merged);
    }

    public MemoryInput withText(String newText) {
        return new MemoryInput(entityId, domain, tenantId, caseId, newText, attributes);
    }
}
