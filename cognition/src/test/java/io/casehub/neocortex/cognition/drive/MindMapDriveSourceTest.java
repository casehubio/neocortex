package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.cognitive.index.CognitiveDerivationEngine;
import io.casehub.neocortex.cognitive.index.DescriptorView;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class MindMapDriveSourceTest {

    @Test
    void returnsIntensityFromMindMapNode() {
        var store = new InMemoryMindMapStore();
        var sgId = store.createSubgraph(new SubgraphInput("cognitive", SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(NodeInput.of("competence-drive", sgId)
                .withProperties(Map.of("cognitiveKind", "drive-intensity", "drive-type", "COMPETENCE",
                        "agent-id", "a1", "intensity", "0.55",
                        "description", "strong mastery orientation")), "t1");

        var registry = CognitiveDefaultsRegistry.forTesting(CognitiveDefaults.empty("a1"));
        var source = new MindMapDriveSource(DriveAxis.COMPETENCE, store, registry);
        var result = source.evaluate("a1", "t1");

        assertThat(result.axis()).isEqualTo(DriveAxis.COMPETENCE);
        assertThat(result.intensity()).isCloseTo(0.55, offset(0.01));
        assertThat(result.trigger()).contains("mastery");
    }

    @Test
    void fallsBackToPersonalityBaselineWhenNoNode() {
        var store = new InMemoryMindMapStore();
        store.createSubgraph(new SubgraphInput("cognitive", SubgraphTypes.COGNITIVE, null), "t1");

        var axes = new DispositionAxes("cooperative", "strict", "aggressive", "moderate", "analytical");
        var view = DescriptorView.of("a1", axes, List.of(), List.of());
        var defaults = CognitiveDerivationEngine.derive(view);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var source = new MindMapDriveSource(DriveAxis.COMPETENCE, store, registry);
        var result = source.evaluate("a1", "t1");

        assertThat(result.axis()).isEqualTo(DriveAxis.COMPETENCE);
        assertThat(result.intensity()).isGreaterThan(0.3);
        assertThat(result.trigger()).contains("competence");
    }

    @Test
    void affiliationBaselineFromCooperativeDisposition() {
        var store = new InMemoryMindMapStore();
        var axes = new DispositionAxes("cooperative", "moderate", "moderate", "moderate", "accommodating");
        var view = DescriptorView.of("a1", axes, List.of(), List.of());
        var defaults = CognitiveDerivationEngine.derive(view);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var source = new MindMapDriveSource(DriveAxis.AFFILIATION, store, registry);
        var result = source.evaluate("a1", "t1");

        assertThat(result.intensity()).isGreaterThanOrEqualTo(0.4);
    }

    @Test
    void autonomyBaselineFromHighAutonomy() {
        var store = new InMemoryMindMapStore();
        var axes = new DispositionAxes("independent", "flexible", "conservative", "high", "confronting");
        var view = DescriptorView.of("a1", axes, List.of(), List.of());
        var defaults = CognitiveDerivationEngine.derive(view);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var source = new MindMapDriveSource(DriveAxis.AUTONOMY, store, registry);
        var result = source.evaluate("a1", "t1");

        assertThat(result.intensity()).isCloseTo(0.6, offset(0.05));
    }

    @Test
    void returnsDefaultWhenNoCdeEntry() {
        var store = new InMemoryMindMapStore();
        var registry = CognitiveDefaultsRegistry.forTesting();
        var source = new MindMapDriveSource(DriveAxis.AFFILIATION, store, registry);
        var result = source.evaluate("unknown-agent", "t1");

        assertThat(result.axis()).isEqualTo(DriveAxis.AFFILIATION);
        assertThat(result.intensity()).isCloseTo(0.4, offset(0.01));
    }

    @Test
    void mindMapNodeTakesPriorityOverPersonalityBaseline() {
        var store = new InMemoryMindMapStore();
        var sgId = store.createSubgraph(new SubgraphInput("cognitive", SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(NodeInput.of("affiliation-drive", sgId)
                .withProperties(Map.of("cognitiveKind", "drive-intensity", "drive-type", "AFFILIATION",
                        "agent-id", "a1", "intensity", "0.8",
                        "description", "deep social bonds from shared adventures")), "t1");

        var axes = new DispositionAxes("independent", "flexible", "conservative", "low", "avoidant");
        var view = DescriptorView.of("a1", axes, List.of(), List.of());
        var defaults = CognitiveDerivationEngine.derive(view);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var source = new MindMapDriveSource(DriveAxis.AFFILIATION, store, registry);
        var result = source.evaluate("a1", "t1");

        assertThat(result.intensity()).isCloseTo(0.8, offset(0.01));
        assertThat(result.trigger()).contains("social bonds");
    }
}
