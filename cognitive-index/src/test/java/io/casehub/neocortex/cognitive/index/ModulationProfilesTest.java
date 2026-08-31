package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeRef;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModulationProfilesTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void memoryProfileExtractsCorrectly() {
        var profile = ModulationProfiles.MEMORY;
        Memory m = new Memory("m1", "e1", new MemoryDomain("experience"), "t1", null,
            "text", Map.of(), NOW, Confidence.unknown(0.7), 0.5, 0.3, -0.1);
        assertThat(profile.confidence().apply(m).value()).isEqualTo(0.7);
        assertThat(profile.pleasure().apply(m)).isEqualTo(0.5);
        assertThat(profile.arousal().apply(m)).isEqualTo(0.3);
        assertThat(profile.dominance().apply(m)).isEqualTo(-0.1);
        assertThat(profile.timestamp().apply(m)).isEqualTo(NOW);
    }

    @Test
    void nodeProfileExtractsCorrectly() {
        var profile = ModulationProfiles.NODE;
        MindMapNode node = new StubNode(
            "n1", "Test", "sg-1",
            new Confidence(ConfidenceOrigin.STATED, 0.8, NOW),
            null, NOW, NOW, null, null,
            Set.of(), Set.of(), 0.4, -0.2, 0.6, Map.of());
        assertThat(profile.confidence().apply(node).value()).isEqualTo(0.8);
        assertThat(profile.pleasure().apply(node)).isEqualTo(0.4);
        assertThat(profile.arousal().apply(node)).isEqualTo(-0.2);
        assertThat(profile.dominance().apply(node)).isEqualTo(0.6);
        assertThat(profile.timestamp().apply(node)).isEqualTo(NOW);
    }
}
