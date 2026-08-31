package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.ModulationFactor;
import io.casehub.neocortex.cognitive.RetrievalModulator;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.neocortex.memory.personality.PersonalityWeights;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.NodeRef;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModulationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void fullMemoryPipelineRanksCorrectly() {
        MoodState mood = new MoodState("agent", "t1", NOW, 0.7, 0.3, 0.5, "test", null, Map.of());
        PersonalityWeights weights = new PersonalityWeights(
            Map.of(new MemoryDomain("experience"), 2.0));

        Memory recentAligned = new Memory("m1", "e1", new MemoryDomain("experience"),
            "t1", null, "recent+aligned", Map.of(),
            NOW.minus(1, ChronoUnit.HOURS), Confidence.unknown(0.9),
            0.7, 0.3, 0.5);

        Memory oldMisaligned = new Memory("m2", "e1", new MemoryDomain("reflection"),
            "t1", null, "old+misaligned", Map.of(),
            NOW.minus(72, ChronoUnit.HOURS), Confidence.unknown(0.3),
            -0.7, -0.3, -0.5);

        List<ModulationFactor<Memory>> factors = List.of(
            ModulationFactors.recencyDecay(Duration.ofDays(7), NOW),
            ModulationFactors.confidenceWeight(),
            ModulationFactors.moodCongruence(mood, 0.6),
            ModulationFactors.domainWeight(weights)
        );

        var result = RetrievalModulator.modulate(
            List.of(oldMisaligned, recentAligned),
            ModulationProfiles.MEMORY, factors);

        assertThat(result).extracting(Memory::text)
            .containsExactly("recent+aligned", "old+misaligned");
    }

    @Test
    void nodeModulationWithoutDomainWeight() {
        MoodState mood = new MoodState("agent", "t1", NOW, 0.5, 0.5, 0.5, "test", null, Map.of());

        MindMapNode aligned = new StubNode(
            "n1", "Aligned", "sg-1",
            new Confidence(ConfidenceOrigin.STATED, 0.9, NOW),
            null, NOW.minus(1, ChronoUnit.HOURS), NOW, null, null,
            Set.of(), Set.of(), 0.5, 0.5, 0.5, Map.of());

        MindMapNode misaligned = new StubNode(
            "n2", "Misaligned", "sg-1",
            new Confidence(ConfidenceOrigin.STATED, 0.3, NOW),
            null, NOW.minus(48, ChronoUnit.HOURS), NOW, null, null,
            Set.of(), Set.of(), -0.5, -0.5, -0.5, Map.of());

        List<ModulationFactor<MindMapNode>> factors = List.of(
            ModulationFactors.recencyDecay(Duration.ofDays(7), NOW),
            ModulationFactors.confidenceWeight(),
            ModulationFactors.moodCongruence(mood, 0.6)
        );

        var result = RetrievalModulator.modulate(
            List.of(misaligned, aligned),
            ModulationProfiles.NODE, factors);

        assertThat(result).extracting(MindMapNode::name)
            .containsExactly("Aligned", "Misaligned");
    }
}
