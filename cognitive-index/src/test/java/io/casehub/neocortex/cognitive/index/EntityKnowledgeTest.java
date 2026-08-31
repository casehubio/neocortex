package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.mindmap.NodeRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntityKnowledgeTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final MemoryDomain EXP = new MemoryDomain("experience");

    @Test
    void rejectsNullNode() {
        assertThatThrownBy(() -> new EntityKnowledge(
            null, List.of(), Map.of(), null, Set.of(), "tenant"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTenantId() {
        assertThatThrownBy(() -> new EntityKnowledge(
            StubNode.named("Alice"), List.of(), Map.of(), null, Set.of(), null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defensiveCopiesOnCollections() {
        var memories = new java.util.HashMap<MemoryDomain, List<Memory>>();
        memories.put(EXP, List.of());
        var refs = new java.util.HashSet<NodeRef>();
        refs.add(new NodeRef("cbr", "case-1", null));

        var ek = new EntityKnowledge(
            StubNode.named("Alice"), List.of(), memories, null, refs, "tenant");

        memories.put(new MemoryDomain("mood"), List.of());
        refs.add(new NodeRef("cbr", "case-2", null));

        assertThat(ek.memories()).hasSize(1);
        assertThat(ek.unresolvedRefs()).hasSize(1);
    }

    @Test
    void trajectoryNullable() {
        var ek = new EntityKnowledge(
            StubNode.named("Alice"), List.of(), Map.of(), null, Set.of(), "tenant");
        assertThat(ek.trajectory()).isNull();
    }

    @Test
    void trajectoryPresent() {
        var trajectory = new AffectTrajectory(0.1, 0.2, 0.05, TrendDirection.IMPROVING, 0.1, 5);
        var ek = new EntityKnowledge(
            StubNode.named("Alice"), List.of(), Map.of(), trajectory, Set.of(), "tenant");
        assertThat(ek.trajectory()).isEqualTo(trajectory);
    }
}
