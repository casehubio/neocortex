package io.casehub.neocortex.memory;

import io.casehub.neocortex.cognitive.Confidence;

import java.util.Map;
import java.util.Set;
import java.util.Objects;

public record MemoryInput(
        Subject subject,
        MemoryDomain domain,
        String tenantId,
        String caseId,
        String text,
        Map<String, String> attributes,
        Confidence confidence, Double pleasure, Double arousal, Double dominance,
        String principalId,
        Set<String> sharedWith) {

    public MemoryInput {
        Objects.requireNonNull(subject, "subject required");
        Objects.requireNonNull(domain, "domain required");
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(text, "text required");
        if (text.isBlank()) {throw new IllegalArgumentException("text must not be blank");}
        Objects.requireNonNull(attributes, "attributes required");
        attributes = Map.copyOf(attributes);
        sharedWith = sharedWith != null ? Set.copyOf(sharedWith) : Set.of();
    }

    @Deprecated(forRemoval = true)
    public MemoryInput(String entityId, MemoryDomain domain, String tenantId,
                       String caseId, String text, Map<String, String> attributes,
                       Confidence confidence, Double pleasure, Double arousal, Double dominance) {
        this(Subject.of("unknown", entityId), domain, tenantId, caseId, text,
             attributes, confidence, pleasure, arousal, dominance, null, null);
    }

    public static MemoryInput of(Subject subject, MemoryDomain domain,
                                 String tenantId, String text) {
        return new MemoryInput(subject, domain, tenantId, null, text,
                               Map.of(), null, null, null, null, null, null);
    }

    @Deprecated(forRemoval = true)
    public static MemoryInput of(String entityId, MemoryDomain domain,
                                 String tenantId, String text) {
        return of(Subject.of("unknown", entityId), domain, tenantId, text);
    }

    public static MemoryInput ownedBy(Subject subject, MemoryDomain domain,
                                      String tenantId, String text, String principalId) {
        return new MemoryInput(subject, domain, tenantId, null, text,
                               Map.of(), null, null, null, null, principalId, null);
    }

    public MemoryInput withAttribute(String key, String value) {
        var merged = new java.util.HashMap<>(attributes);
        merged.put(key, value);
        return new MemoryInput(subject, domain, tenantId, caseId, text, merged, confidence, pleasure, arousal, dominance, principalId, sharedWith);
    }

    public MemoryInput withAttributes(Map<String, String> additional) {
        var merged = new java.util.HashMap<>(attributes);
        merged.putAll(additional);
        return new MemoryInput(subject, domain, tenantId, caseId, text, merged, confidence, pleasure, arousal, dominance, principalId, sharedWith);
    }

    public MemoryInput withText(String newText) {
        return new MemoryInput(subject, domain, tenantId, caseId, newText, attributes, confidence, pleasure, arousal, dominance, principalId, sharedWith);
    }

    public MemoryInput withPad(Double pleasure, Double arousal, Double dominance) {
        return new MemoryInput(subject, domain, tenantId, caseId, text, attributes, confidence, pleasure, arousal, dominance, principalId, sharedWith);
    }

    public MemoryInput withCaseId(String caseId) {
        return new MemoryInput(subject, domain, tenantId, caseId, text, attributes, confidence, pleasure, arousal, dominance, principalId, sharedWith);
    }

    public MemoryInput withConfidence(Confidence confidence) {
        return new MemoryInput(subject, domain, tenantId, caseId, text, attributes, confidence, pleasure, arousal, dominance, principalId, sharedWith);
    }

    public MemoryInput withPrincipalId(String principalId) {
        return new MemoryInput(subject, domain, tenantId, caseId, text, attributes, confidence, pleasure, arousal, dominance, principalId, sharedWith);
    }

    public MemoryInput withSharedWith(Set<String> sharedWith) {
        return new MemoryInput(subject, domain, tenantId, caseId, text, attributes, confidence, pleasure, arousal, dominance, principalId, sharedWith);
    }

    @Deprecated(forRemoval = true)
    public String entityId() {
        return subject.id();
    }
}
