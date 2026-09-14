package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CognitionDiffServiceTest {

    @Test
    void shouldFindMutationsInTimeRange() {
        var store = new TestSnapshotStore();
        Instant t0 = Instant.now().minusSeconds(60);
        Instant t1 = Instant.now().minusSeconds(30);
        Instant t2 = Instant.now();

        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n1", "Alice", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null), t0, "manual"));
        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n2", "Bob", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null), t1, "consolidation:MergeDetectionPhase"));

        var result = CognitionDiffService.diff(store, "t1", "sg1",
            t0.minusSeconds(1), t2, null);

        assertEquals(2, result.mutations().size());
        assertEquals(2, result.summary().nodesAdded());
        assertEquals(0, result.summary().nodesErased());
        assertEquals(0, result.summary().edgesAdded());
    }

    @Test
    void shouldFilterBySource() {
        var store = new TestSnapshotStore();
        Instant now = Instant.now();

        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n1", "Alice", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null), now.minusSeconds(10), "conversation-bridge"));
        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n2", "Bob", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null), now.minusSeconds(5), "consolidation:MergeDetectionPhase"));

        var result = CognitionDiffService.diff(store, "t1", null,
            now.minusSeconds(60), now, "consolidation:*");

        assertEquals(1, result.mutations().size());
        assertEquals("Bob", ((GraphMutation.NodeAdded) result.mutations().getFirst()).name());
    }

    @Test
    void shouldFilterByExactSource() {
        var store = new TestSnapshotStore();
        Instant now = Instant.now();

        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n1", "Alice", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null), now.minusSeconds(10), "conversation-bridge"));
        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n2", "Bob", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null), now.minusSeconds(5), "manual"));

        var result = CognitionDiffService.diff(store, "t1", null,
            now.minusSeconds(60), now, "manual");

        assertEquals(1, result.mutations().size());
        assertEquals("Bob", ((GraphMutation.NodeAdded) result.mutations().getFirst()).name());
    }

    @Test
    void shouldDefaultFromToLastConsolidation() {
        var store = new TestSnapshotStore();
        Instant consolidationTime = Instant.now().minusSeconds(300);
        store.storeAuditEntry(new ConsolidationAuditEntry(
            "t1", consolidationTime.minusSeconds(5), consolidationTime, List.of()));
        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n1", "Alice", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null),
            consolidationTime.plusSeconds(10), "manual"));
        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n2", "OlderBob", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null),
            consolidationTime.minusSeconds(60), "manual"));

        var result = CognitionDiffService.diff(store, "t1", null, null, null, null);

        assertEquals(1, result.mutations().size());
        assertEquals("Alice", ((GraphMutation.NodeAdded) result.mutations().getFirst()).name());
    }

    @Test
    void shouldDefaultFromTo24HoursAgoWhenNoConsolidation() {
        var store = new TestSnapshotStore();
        Instant now = Instant.now();

        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n1", "Recent", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null), now.minusSeconds(3600), "manual"));
        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n2", "Old", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null), now.minusSeconds(90000), "manual"));

        var result = CognitionDiffService.diff(store, "t1", null, null, null, null);

        assertEquals(1, result.mutations().size());
        assertEquals("Recent", ((GraphMutation.NodeAdded) result.mutations().getFirst()).name());
    }

    @Test
    void shouldCountAllMutationTypes() {
        var store = new TestSnapshotStore();
        Instant now = Instant.now();

        store.storeMutation("t1", new GraphMutation.NodeAdded(
            "n1", "Alice", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null), now.minusSeconds(10), "manual"));
        store.storeMutation("t1", new GraphMutation.NodeUpdated(
            "n1", "sg1", Map.of("name", new FieldChange("name", "Alice", "Alicia")),
            now.minusSeconds(9), "manual"));
        store.storeMutation("t1", new GraphMutation.EdgeAdded(
            "e1", "n1", "n2", "knows", new Confidence(ConfidenceOrigin.STATED, 0.9, null), now.minusSeconds(8), "manual"));
        store.storeMutation("t1", new GraphMutation.EdgeRemoved(
            "e1", "n1", "n2", "knows", now.minusSeconds(7), "manual"));
        store.storeMutation("t1", new GraphMutation.NodesMerged(
            "n1", "n3", List.of(), now.minusSeconds(6), "manual"));
        store.storeMutation("t1", new GraphMutation.NodeSuperseded(
            "n4", "n1", "duplicate", now.minusSeconds(5), "manual"));
        store.storeMutation("t1", new GraphMutation.NodeReinstated(
            "n4", now.minusSeconds(4), "manual"));
        store.storeMutation("t1", new GraphMutation.NodeErased(
            "n5", "sg1", 2, now.minusSeconds(3), "manual"));

        var result = CognitionDiffService.diff(store, "t1", null,
            now.minusSeconds(60), now, null);

        var summary = result.summary();
        assertEquals(1, summary.nodesAdded());
        assertEquals(1, summary.nodesUpdated());
        assertEquals(1, summary.nodesErased());
        assertEquals(1, summary.edgesAdded());
        assertEquals(1, summary.edgesRemoved());
        assertEquals(1, summary.merges());
        assertEquals(1, summary.supersessions());
        assertEquals(1, summary.reinstated());
    }

    @Test
    void shouldReturnEmptyResultForNoMutations() {
        var store = new TestSnapshotStore();
        Instant now = Instant.now();

        var result = CognitionDiffService.diff(store, "t1", "sg1",
            now.minusSeconds(60), now, null);

        assertTrue(result.mutations().isEmpty());
        assertEquals(0, result.summary().nodesAdded());
    }

    @Test
    void shouldIncludeTimeRangeInResult() {
        var store = new TestSnapshotStore();
        Instant from = Instant.now().minusSeconds(60);
        Instant to = Instant.now();

        var result = CognitionDiffService.diff(store, "t1", null, from, to, null);

        assertEquals(from, result.timeRange().from());
        assertEquals(to, result.timeRange().to());
    }
}
