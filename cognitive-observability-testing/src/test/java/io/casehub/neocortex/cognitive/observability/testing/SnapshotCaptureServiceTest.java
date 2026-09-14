package io.casehub.neocortex.cognitive.observability.testing;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.observability.GraphMutation;
import io.casehub.neocortex.cognitive.observability.SnapshotCaptureService;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.neocortex.mindmap.intelligence.consolidation.ConsolidationCompleted;
import io.casehub.neocortex.mindmap.intelligence.consolidation.PhaseResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotCaptureServiceTest {

    @Test
    void shouldConstructAuditEntry() {
        var snapshotStore = new InMemorySnapshotStore();
        var mindMapStore = new InMemoryMindMapStore();
        var service = new SnapshotCaptureService(snapshotStore, mindMapStore, 10, 90);

        var phaseResults = List.of(
            new PhaseResult("AccessFrequencyPhase",
                Instant.now().minusSeconds(5), Instant.now().minusSeconds(3), true, null),
            new PhaseResult("MergeDetectionPhase",
                Instant.now().minusSeconds(3), Instant.now(), true, null));

        service.onConsolidationCompleted(new ConsolidationCompleted("t1", phaseResults));

        var entries = snapshotStore.findAuditEntries("t1",
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        assertEquals(1, entries.size());
        assertEquals(2, entries.getFirst().phases().size());
        assertEquals("AccessFrequencyPhase", entries.getFirst().phases().get(0).phaseName());
        assertTrue(entries.getFirst().phases().get(0).success());
    }

    @Test
    void shouldCaptureKeyframeAtThreshold() {
        var snapshotStore = new InMemorySnapshotStore();
        var mindMapStore = new InMemoryMindMapStore();
        String sgId = mindMapStore.createSubgraph(new SubgraphInput("test", "concept", null), "t1");
        mindMapStore.addNode(NodeInput.of("Alice", sgId)
            .withConfidence(new Confidence(ConfidenceOrigin.STATED, 0.9, null)), "t1");

        var service = new SnapshotCaptureService(snapshotStore, mindMapStore, 3, 90);

        for (int i = 0; i < 3; i++) {
            snapshotStore.storeMutation("t1", new GraphMutation.NodeAdded(
                "n" + i, "Node" + i, sgId,
                new Confidence(ConfidenceOrigin.STATED, 0.9, null),
                Instant.now(), "test"));
        }

        service.onConsolidationCompleted(new ConsolidationCompleted("t1", List.of()));

        var keyframe = snapshotStore.latestKeyframe("t1", sgId);
        assertTrue(keyframe.isPresent());
        assertEquals(1, keyframe.get().nodes().size());
        assertEquals("Alice", keyframe.get().nodes().getFirst().name());
    }

    @Test
    void shouldNotCaptureKeyframeBelowThreshold() {
        var snapshotStore = new InMemorySnapshotStore();
        var mindMapStore = new InMemoryMindMapStore();
        String sgId = mindMapStore.createSubgraph(new SubgraphInput("test", "concept", null), "t1");

        var service = new SnapshotCaptureService(snapshotStore, mindMapStore, 10, 90);

        snapshotStore.storeMutation("t1", new GraphMutation.NodeAdded(
            "n1", "Alice", sgId,
            new Confidence(ConfidenceOrigin.STATED, 0.9, null),
            Instant.now(), "test"));

        service.onConsolidationCompleted(new ConsolidationCompleted("t1", List.of()));

        var keyframe = snapshotStore.latestKeyframe("t1", sgId);
        assertFalse(keyframe.isPresent());
    }

    @Test
    void shouldRecordFailedPhase() {
        var snapshotStore = new InMemorySnapshotStore();
        var service = new SnapshotCaptureService(snapshotStore, null, 10, 90);

        var phaseResults = List.of(
            new PhaseResult("BrokenPhase",
                Instant.now().minusSeconds(2), Instant.now(), false, "NPE in phase"));

        service.onConsolidationCompleted(new ConsolidationCompleted("t1", phaseResults));

        var entries = snapshotStore.findAuditEntries("t1",
            Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        assertEquals(1, entries.size());
        assertFalse(entries.getFirst().phases().getFirst().success());
        assertEquals("NPE in phase", entries.getFirst().phases().getFirst().errorMessage());
    }
}
