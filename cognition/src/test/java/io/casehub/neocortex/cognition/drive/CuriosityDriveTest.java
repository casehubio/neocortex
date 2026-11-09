package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.mindmap.EdgeInput;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.CuriosityChannelComputer;
import io.casehub.neocortex.mindmap.intelligence.InformationGapDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CuriosityDriveTest {

    private              InMemoryMindMapStore store;
    private              CuriosityDrive       drive;
    private static final String               TENANT = "test-tenant";
    private static final Instant              NOW    = Instant.now();

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        drive = new CuriosityDrive(new InformationGapDetector(store), new CuriosityChannelComputer(), 0.5);
    }

    @Test
    void evaluate_noGaps_zeroIntensity() {
        var result = drive.evaluate("agent-1", TENANT);

        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.intensity()).isEqualTo(0.0);
        assertThat(result.trigger()).contains("no knowledge gaps");
    }

    @Test
    void evaluate_partialKnowledge_positiveIntensity() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String nodeA = store.addNode(NodeInput.of("quantum-entanglement", sgId)
                                              .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        String nodeB = store.addNode(NodeInput.of("wave-function", sgId)
                                              .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

        var result = drive.evaluate("agent-1", TENANT);

        assertThat(result.axis()).isEqualTo(DriveAxis.CURIOSITY);
        assertThat(result.intensity()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        assertThat(result.trigger()).contains("quantum-entanglement");
    }

    @Test
    void evaluate_manyGaps_higherIntensity() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String ctx = store.addNode(NodeInput.of("context", sgId)
                                            .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        for (int i = 0; i < 5; i++) {
            String gap = store.addNode(NodeInput.of("gap-" + i, sgId)
                                                .withConfidence(Confidence.inferred(0.35, NOW)), TENANT);
            store.addEdge(EdgeInput.of(gap, ctx, "relates-to"), TENANT);
        }

        var result = drive.evaluate("agent-1", TENANT);

        assertThat(result.intensity()).isGreaterThan(0.0);
    }

    @Test
    void evaluate_intensityNeverExceedsOne() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String ctx = store.addNode(NodeInput.of("hub", sgId)
                                            .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        for (int i = 0; i < 20; i++) {
            String gap = store.addNode(NodeInput.of("gap-" + i, sgId)
                                                .withConfidence(Confidence.inferred(0.5, NOW)), TENANT);
            store.addEdge(EdgeInput.of(gap, ctx, "relates-to"), TENANT);
            store.addEdge(EdgeInput.of(ctx, gap, "relates-to"), TENANT);
        }

        var result = drive.evaluate("agent-1", TENANT);

        assertThat(result.intensity()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void lastGaps_emptyBeforeEvaluation() {
        assertThat(drive.lastGaps()).isEmpty();
    }

    @Test
    void lastGaps_populatedAfterEvaluation() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String nodeA = store.addNode(NodeInput.of("partial", sgId)
                                              .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        String nodeB = store.addNode(NodeInput.of("context", sgId)
                                              .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

        drive.evaluate("agent-1", TENANT);

        assertThat(drive.lastGaps()).isNotEmpty();
    }

    @Test
    void lastGaps_updatedOnSubsequentEvaluation() {
        drive.evaluate("agent-1", TENANT);
        var firstGaps = drive.lastGaps();
        assertThat(firstGaps).isEmpty();

        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String nodeA = store.addNode(NodeInput.of("new-topic", sgId)
                                              .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        String nodeB = store.addNode(NodeInput.of("known", sgId)
                                              .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

        drive.evaluate("agent-1", TENANT);
        assertThat(drive.lastGaps()).isNotEmpty();
    }

    @Test
    void lastChannel_nullBeforeEvaluation() {
        assertThat(drive.lastChannel()).isNull();
    }

    @Test
    void lastChannel_nullWhenNoGaps() {
        drive.evaluate("agent-1", TENANT);
        assertThat(drive.lastChannel()).isNull();
    }

    @Test
    void lastChannel_populatedWhenGapsPresent() {
        String sgId = store.createSubgraph(
                new SubgraphInput("concepts", SubgraphTypes.CONCEPT, null), TENANT);
        String nodeA = store.addNode(NodeInput.of("partial-knowledge", sgId)
                                              .withConfidence(Confidence.inferred(0.4, NOW)), TENANT);
        String nodeB = store.addNode(NodeInput.of("context", sgId)
                                              .withConfidence(Confidence.stated(0.9, NOW)), TENANT);
        store.addEdge(EdgeInput.of(nodeA, nodeB, "relates-to"), TENANT);

        drive.evaluate("agent-1", TENANT);

        assertThat(drive.lastChannel()).isNotNull();
    }

}
