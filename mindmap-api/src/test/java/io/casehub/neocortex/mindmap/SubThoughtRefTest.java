package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class SubThoughtRefTest {

    @Test
    void ofCreatesNodeRefWithCorrectScheme() {
        NodeRef ref = SubThoughtRef.of("mem-123", 2);
        assertThat(ref.scheme()).isEqualTo("sub-thought");
        assertThat(ref.id()).isEqualTo("mem-123");
        assertThat(ref.qualifier()).isEqualTo("2");
    }

    @Test
    void memoryIdExtractsFromNode() {
        var node = stubNode(Set.of(SubThoughtRef.of("mem-456", 0)));
        assertThat(SubThoughtRef.memoryId(node)).isEqualTo(Optional.of("mem-456"));
    }

    @Test
    void subThoughtIndexExtractsFromNode() {
        var node = stubNode(Set.of(SubThoughtRef.of("mem-456", 3)));
        assertThat(SubThoughtRef.subThoughtIndex(node)).isEqualTo(Optional.of(3));
    }

    @Test
    void emptyWhenNoSubThoughtRef() {
        var node = stubNode(Set.of(new NodeRef("other", "id", null)));
        assertThat(SubThoughtRef.memoryId(node)).isEmpty();
        assertThat(SubThoughtRef.subThoughtIndex(node)).isEmpty();
    }

    private MindMapNode stubNode(Set<NodeRef> refs) {
        return new MindMapNode() {
            @Override public String subgraphType() { return "cognitive"; }
            @Override public String id() { return "n1"; }
            @Override public String name() { return "test"; }
            @Override public String subgraphId() { return "sg1"; }
            @Override public Confidence confidence() { return null; }
            @Override public String provenance() { return null; }
            @Override public Instant createdAt() { return null; }
            @Override public Instant updatedAt() { return null; }
            @Override public Instant validFrom() { return null; }
            @Override public Instant validUntil() { return null; }
            @Override public Set<String> traits() { return Set.of(); }
            @Override public Set<NodeRef> refs() { return refs; }
            @Override public Double pleasure() { return null; }
            @Override public Double arousal() { return null; }
            @Override public Double dominance() { return null; }
            @Override public Optional<String> property(String key) { return Optional.empty(); }
            @Override public Map<String, String> properties() { return Map.of(); }
            @Override public PrincipalId principalId() { return null; }
            @Override public Set<String> sharedWith() { return Set.of(); }
        };
    }
}
