package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record EdgeInput(
        String sourceNodeId,
        String targetNodeId,
        String edgeType,
        Confidence confidence,
        String provenance,
        Instant validFrom,
        Instant validUntil,
        Double pleasure,
        Double arousal,
        Double dominance,
        Map<String, String> properties
                       ) {

    public EdgeInput {
        Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        Objects.requireNonNull(targetNodeId, "targetNodeId");
        Objects.requireNonNull(edgeType, "edgeType");
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static EdgeInput of(String sourceNodeId, String targetNodeId, String edgeType) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, null, null,
                             null, null, null, null, null, null);
    }

    public EdgeInput withConfidence(Confidence confidence) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, confidence, provenance,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public EdgeInput withProvenance(String provenance) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, confidence, provenance,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public EdgeInput withValidFrom(Instant validFrom) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, confidence, provenance,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public EdgeInput withValidUntil(Instant validUntil) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, confidence, provenance,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public EdgeInput withPleasure(Double pleasure) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, confidence, provenance,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public EdgeInput withArousal(Double arousal) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, confidence, provenance,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public EdgeInput withDominance(Double dominance) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, confidence, provenance,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public EdgeInput withPad(Double pleasure, Double arousal, Double dominance) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, confidence, provenance,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public EdgeInput withProperties(Map<String, String> properties) {
        return new EdgeInput(sourceNodeId, targetNodeId, edgeType, confidence, provenance,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public EdgeInput withProperty(String key, String value) {
        var merged = new java.util.HashMap<>(properties);
        merged.put(key, value);
        return withProperties(merged);
    }
}
