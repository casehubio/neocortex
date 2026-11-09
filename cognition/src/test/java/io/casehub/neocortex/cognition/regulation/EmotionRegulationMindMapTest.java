package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.regulation.RegulationContext;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmotionRegulationMindMapTest {

    private InMemoryMindMapStore mindMapStore;
    private String cognitiveSubgraphId;

    @BeforeEach
    void setUp() {
        mindMapStore = new InMemoryMindMapStore();
        cognitiveSubgraphId = mindMapStore.createSubgraph(
                new SubgraphInput("cognitive", SubgraphTypes.COGNITIVE, null), "t1");
    }

    @Test
    void writeAndLoadRegulationResult() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.RESPONSE_MODULATION,
                List.of("anger"), List.of("gloating"),
                0.85, 0.4, 0.6,
                "You hold back the triumph", null, null);

        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1", cognitiveSubgraphId, result);

        var history = EmotionRegulationMindMapAdapter.loadHistory(mindMapStore, "a1", "t1", 10);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().strategy()).isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION);
        assertThat(history.getFirst().targetEmotions()).containsExactly("anger");
        assertThat(history.getFirst().targetDrives()).containsExactly("gloating");
        assertThat(history.getFirst().peakOriginalIntensity()).isEqualTo(0.85);
        assertThat(history.getFirst().regulatedIntensity()).isEqualTo(0.4);
        assertThat(history.getFirst().effectiveness()).isEqualTo(0.6);
        assertThat(history.getFirst().narrative()).isEqualTo("You hold back the triumph");
    }

    @Test
    void writeEnvironmentalStrategyPreservesAxisAndDirective() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SITUATION_SELECTION,
                List.of("anxiety"), List.of(),
                0.9, 0.5, 0.7,
                "You decide to leave", DriveAxis.AFFILIATION,
                "Avoid being alone with Muttley");

        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1", cognitiveSubgraphId, result);

        var history = EmotionRegulationMindMapAdapter.loadHistory(mindMapStore, "a1", "t1", 10);

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().strategy()).isEqualTo(EmotionRegulationStrategy.SITUATION_SELECTION);
        assertThat(history.getFirst().regulationAxis()).isEqualTo(DriveAxis.AFFILIATION);
        assertThat(history.getFirst().environmentalDirective()).isEqualTo("Avoid being alone with Muttley");
    }

    @Test
    void loadHistoryReturnsEmptyWhenNone() {
        var history = EmotionRegulationMindMapAdapter.loadHistory(mindMapStore, "a1", "t1", 10);
        assertThat(history).isEmpty();
    }

    @Test
    void loadHistoryFiltersByAgent() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.COGNITIVE_CHANGE,
                List.of("sadness"), List.of(),
                0.7, 0.4, 0.5,
                "You reframe the meaning", null, null);

        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1", cognitiveSubgraphId, result);
        EmotionRegulationMindMapAdapter.write(mindMapStore, "a2", "t1", cognitiveSubgraphId, result);

        var historyA1 = EmotionRegulationMindMapAdapter.loadHistory(mindMapStore, "a1", "t1", 10);
        var historyA2 = EmotionRegulationMindMapAdapter.loadHistory(mindMapStore, "a2", "t1", 10);

        assertThat(historyA1).hasSize(1);
        assertThat(historyA2).hasSize(1);
    }

    @Test
    void loadHistoryRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            var result = new EmotionRegulationResult(
                    EmotionRegulationStrategy.RESPONSE_MODULATION,
                    List.of("anger"), List.of(),
                    0.8, 0.4, 0.5,
                    "Regulation " + i, null, null);
            EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1", cognitiveSubgraphId, result);
        }

        var history = EmotionRegulationMindMapAdapter.loadHistory(mindMapStore, "a1", "t1", 3);
        assertThat(history).hasSize(3);
    }

    @Test
    void writeStoresDominantEmotionType() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SITUATION_SELECTION,
                List.of("SHAME"), List.of(),
                0.8, 0.5, 0.3,
                "You withdraw from the room", null, null);

        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1",
                                              cognitiveSubgraphId, result, "SHAME");

        var nodes = mindMapStore.search(MindMapQuery.of("t1", 200).withType(SubgraphTypes.COGNITIVE));
        var regulationNode = nodes.stream()
                                  .filter(n -> "emotion-regulation-result".equals(n.properties().get("cognitiveKind")))
                                  .findFirst().orElseThrow();

        assertThat(regulationNode.properties().get("dominantEmotionType")).isEqualTo("SHAME");
    }

    @Test
    void loadHistoryFiltersByEmotionType() {
        var shameResult = new EmotionRegulationResult(
                EmotionRegulationStrategy.SITUATION_SELECTION,
                List.of("SHAME"), List.of(),
                0.8, 0.5, 0.3,
                "You withdraw", null, null);
        var angerResult = new EmotionRegulationResult(
                EmotionRegulationStrategy.COGNITIVE_CHANGE,
                List.of("ANGER"), List.of(),
                0.7, 0.3, 0.6,
                "You reframe", null, null);

        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1",
                                              cognitiveSubgraphId, shameResult, "SHAME");
        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1",
                                              cognitiveSubgraphId, angerResult, "ANGER");
        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1",
                                              cognitiveSubgraphId, shameResult, "SHAME");

        var shameHistory = EmotionRegulationMindMapAdapter.loadHistory(
                mindMapStore, "a1", "t1", EmotionType.SHAME, 10);
        var angerHistory = EmotionRegulationMindMapAdapter.loadHistory(
                mindMapStore, "a1", "t1", EmotionType.ANGER, 10);

        assertThat(shameHistory).hasSize(2);
        assertThat(angerHistory).hasSize(1);
    }

    @Test
    void loadHistoryByEmotionTypeIgnoresNodesWithoutField() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.RESPONSE_MODULATION,
                List.of("SHAME"), List.of(),
                0.8, 0.4, 0.5,
                "You suppress", null, null);

        // Write without dominantEmotionType (legacy node)
        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1",
                                              cognitiveSubgraphId, result);
        // Write with dominantEmotionType
        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1",
                                              cognitiveSubgraphId, result, "SHAME");

        var filtered = EmotionRegulationMindMapAdapter.loadHistory(
                mindMapStore, "a1", "t1", EmotionType.SHAME, 10);
        var unfiltered = EmotionRegulationMindMapAdapter.loadHistory(
                mindMapStore, "a1", "t1", 10);

        assertThat(filtered).hasSize(1);
        assertThat(unfiltered).hasSize(2);
    }

    @Test
    void writeStoresRegulationContext() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.COGNITIVE_CHANGE,
                List.of("ANGER"), List.of("COMPETENCE"),
                0.7, 0.3, 0.8, "reframed", DriveAxis.COMPETENCE, null);
        var regCtx = new RegulationContext("library", List.of("dick-dastardly", "hooded-claw"), "treasure");
        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1",
                                              cognitiveSubgraphId, result, "ANGER", regCtx);

        var nodes = mindMapStore.search(MindMapQuery.of("t1", 10).withType(SubgraphTypes.COGNITIVE));
        var regNode = nodes.stream()
                           .filter(n -> "emotion-regulation-result".equals(n.properties().get("cognitiveKind")))
                           .findFirst().orElseThrow();
        assertThat(regNode.properties().get("regulationLocation")).isEqualTo("library");
        assertThat(regNode.properties().get("regulationAgents")).isEqualTo("dick-dastardly,hooded-claw");
        assertThat(regNode.properties().get("regulationTopic")).isEqualTo("treasure");
    }

    @Test
    void writeHandlesNullRegulationContext() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.COGNITIVE_CHANGE,
                List.of("ANGER"), List.of("COMPETENCE"),
                0.7, 0.3, 0.8, "reframed", DriveAxis.COMPETENCE, null);
        EmotionRegulationMindMapAdapter.write(mindMapStore, "a1", "t1",
                                              cognitiveSubgraphId, result, "ANGER", null);

        var nodes = mindMapStore.search(MindMapQuery.of("t1", 10).withType(SubgraphTypes.COGNITIVE));
        var regNode = nodes.stream()
                           .filter(n -> "emotion-regulation-result".equals(n.properties().get("cognitiveKind")))
                           .findFirst().orElseThrow();
        assertThat(regNode.properties().get("regulationLocation")).isNull();
        assertThat(regNode.properties().get("regulationAgents")).isNull();
        assertThat(regNode.properties().get("regulationTopic")).isNull();
    }


}
