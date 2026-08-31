package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record NodeInput(
        String name,
        String subgraphId,
        Confidence confidence,
        String provenance,
        Set<String> traits,
        Set<NodeRef> refs,
        Instant validFrom,
        Instant validUntil,
        Double pleasure,
        Double arousal,
        Double dominance,
        Map<String, String> properties
                       ) {

    public NodeInput {
        if (name == null || name.isBlank()) {throw new IllegalArgumentException("name must not be blank");}
        if (subgraphId == null || subgraphId.isBlank()) {
            throw new IllegalArgumentException("subgraphId must not be blank");
        }
        traits     = traits == null ? Set.of() : Set.copyOf(traits);
        refs       = refs == null ? Set.of() : Set.copyOf(refs);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    public static NodeInput of(String name, String subgraphId) {
        return new NodeInput(name, subgraphId, null, null, null, null,
                             null, null, null, null, null, null);
    }

    public NodeInput withConfidence(Confidence confidence) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withProvenance(String provenance) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withTraits(Set<String> traits) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withRefs(Set<NodeRef> refs) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withValidFrom(Instant validFrom) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withValidUntil(Instant validUntil) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withPleasure(Double pleasure) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withArousal(Double arousal) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withDominance(Double dominance) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withPad(Double pleasure, Double arousal, Double dominance) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withProperties(Map<String, String> properties) {
        return new NodeInput(name, subgraphId, confidence, provenance, traits, refs,
                             validFrom, validUntil, pleasure, arousal, dominance, properties);
    }

    public NodeInput withProperty(String key, String value) {
        var merged = new java.util.HashMap<>(properties);
        merged.put(key, value);
        return withProperties(merged);
    }
}
