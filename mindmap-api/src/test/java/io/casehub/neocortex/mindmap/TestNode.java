package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

record TestNode(Map<String, String> props) implements MindMapNode {
    TestNode { props = Map.copyOf(props); }
    public String id() { return "test-node"; }
    public String name() { return "test"; }
    public String subgraphId() { return "sg-1"; }
    public Confidence confidence() { return null; }
    public String provenance() { return null; }
    public Instant createdAt() { return Instant.now(); }
    public Instant updatedAt() { return Instant.now(); }
    public Instant validFrom() { return null; }
    public Instant validUntil() { return null; }
    public Set<String> traits() { return Set.of(); }
    public Set<NodeRef> refs() { return Set.of(); }
    public Double pleasure() { return null; }
    public Double arousal() { return null; }
    public Double dominance() { return null; }
    public Optional<String> property(String key) { return Optional.ofNullable(props.get(key)); }
    public Map<String, String> properties() { return props; }
}
