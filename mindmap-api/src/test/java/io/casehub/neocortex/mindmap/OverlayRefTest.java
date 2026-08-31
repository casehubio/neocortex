package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OverlayRefTest {

    @Test
    void ofCreatesCorrectNodeRef() {
        NodeRef ref = OverlayRef.of("shared-123");
        assertThat(ref.scheme()).isEqualTo("overlay");
        assertThat(ref.id()).isEqualTo("shared-123");
        assertThat(ref.qualifier()).isNull();
    }

    @Test
    void sharedNodeIdExtractsFromOverlayNode() {
        NodeRef overlayRef = OverlayRef.of("shared-456");
        NodeRef otherRef = new NodeRef("memory", "mem-1", null);
        MindMapNode node = stubWithRefs(Set.of(overlayRef, otherRef));
        Optional<String> id = OverlayRef.sharedNodeId(node);
        assertThat(id).contains("shared-456");
    }

    @Test
    void sharedNodeIdReturnsEmptyWhenNoOverlayRef() {
        NodeRef otherRef = new NodeRef("memory", "mem-1", null);
        MindMapNode node = stubWithRefs(Set.of(otherRef));
        assertThat(OverlayRef.sharedNodeId(node)).isEmpty();
    }

    @Test
    void agentIdConstantExists() {
        assertThat(OverlayRef.AGENT_ID).isEqualTo("agentId");
    }


    private MindMapNode stubWithRefs(Set<NodeRef> refs) {
        return new MindMapNode() {
            public String id() { return "n1"; }
            public String name() { return "test"; }
            public String subgraphId() { return "sg1"; }
            public io.casehub.neocortex.cognitive.Confidence confidence() { return null; }
            public String provenance() { return null; }
            public java.time.Instant createdAt() { return java.time.Instant.now(); }
            public java.time.Instant updatedAt() { return java.time.Instant.now(); }
            public java.time.Instant validFrom() { return null; }
            public java.time.Instant validUntil() { return null; }
            public Set<String> traits() { return Set.of(); }
            public Set<NodeRef> refs() { return refs; }
            public Double pleasure() { return null; }
            public Double arousal() { return null; }
            public Double dominance() { return null; }
            public java.util.Optional<String> property(String key) { return java.util.Optional.empty(); }
            public java.util.Map<String, String> properties() { return java.util.Map.of(); }
        };
    }
}
