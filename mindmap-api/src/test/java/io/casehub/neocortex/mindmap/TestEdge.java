package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

record TestEdge(String sourceNodeId, String targetNodeId, String edgeType) implements MindMapEdge {
    TestEdge(String edgeType) { this("src", "tgt", edgeType); }
    public String id() { return "test-edge"; }
    public ValidationTier tier() { return ValidationTier.REGISTERED; }
    public Confidence confidence() { return null; }
    public String provenance() { return null; }
    public Instant createdAt() { return Instant.now(); }
    public Instant updatedAt() { return Instant.now(); }
    public Instant validFrom() { return null; }
    public Instant validUntil() { return null; }
    public Double pleasure() { return null; }
    public Double arousal() { return null; }
    public Double dominance() { return null; }
    public Optional<String> property(String key) { return Optional.empty(); }
    public Map<String, String> properties() { return Map.of(); }
}
