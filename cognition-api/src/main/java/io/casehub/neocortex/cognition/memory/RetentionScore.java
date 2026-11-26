package io.casehub.neocortex.cognition.memory;

import java.util.Objects;

public record RetentionScore(String caseId, String entityId,
                             double confidence, double recencyFactor,
                             double frequencyFactor, double trustFactor,
                             double composite) {
    public RetentionScore {
        Objects.requireNonNull(caseId, "caseId required");
        Objects.requireNonNull(entityId, "entityId required");
    }

    public static RetentionScore compute(String caseId, String entityId,
                                         double confidence, double recencyFactor,
                                         double frequencyFactor, double trustFactor,
                                         RetentionConfig config) {
        double base = (config.recencyWeight() * recencyFactor + config.frequencyWeight() * frequencyFactor)
                      / (config.recencyWeight() + config.frequencyWeight());
        double composite = Math.clamp(base * confidence * trustFactor, 0.0, 1.0);
        return new RetentionScore(caseId, entityId, confidence,
                                  recencyFactor, frequencyFactor, trustFactor, composite);
    }
}
