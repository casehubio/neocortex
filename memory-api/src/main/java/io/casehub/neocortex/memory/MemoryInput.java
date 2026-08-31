package io.casehub.neocortex.memory;

import io.casehub.neocortex.cognitive.Confidence;

import java.util.Map;
import java.util.Objects;

public record MemoryInput(
        String entityId,
        MemoryDomain domain,
        String tenantId,
        String caseId,
        String text,
        Map<String, String> attributes,
        Confidence confidence, Double pleasure, Double arousal, Double dominance) {
    public MemoryInput {
        Objects.requireNonNull(entityId, "entityId required");
        Objects.requireNonNull(domain, "domain required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(text, "text required");
        if (text.isBlank()) {throw new IllegalArgumentException("text must not be blank");}
        Objects.requireNonNull(attributes, "attributes required");
        attributes = Map.copyOf(attributes);
    }

    public MemoryInput withAttribute(String key, String value) {
        var merged = new java.util.HashMap<>(attributes);
        merged.put(key, value);
        return new MemoryInput(entityId, domain, tenantId, caseId, text, merged, confidence, pleasure, arousal, dominance);
    }

    public MemoryInput withAttributes(Map<String, String> additional) {
        var merged = new java.util.HashMap<>(attributes);
        merged.putAll(additional);
        return new MemoryInput(entityId, domain, tenantId, caseId, text, merged, confidence, pleasure, arousal, dominance);
    }

    public MemoryInput withText(String newText) {
        return new MemoryInput(entityId, domain, tenantId, caseId, newText, attributes, confidence, pleasure, arousal, dominance);
    }

    public MemoryInput withPad(Double pleasure, Double arousal, Double dominance) {
        return new MemoryInput(entityId, domain, tenantId, caseId, text, attributes, confidence, pleasure, arousal, dominance);
    }
}
