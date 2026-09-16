package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CognitionTraceServiceTest {

    @Test
    void shouldTraceEntityLifecycle() {
        var snapshotStore = new TestSnapshotStore();
        Instant t0 = Instant.now().minusSeconds(60);
        Instant t1 = Instant.now().minusSeconds(30);

        snapshotStore.storeMutation("t1", new GraphMutation.NodeAdded(
            "n1", "Alice", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null),
            t0, "conversation-bridge"));
        snapshotStore.storeMutation("t1", new GraphMutation.NodeUpdated(
            "n1", "sg1", Map.of("confidence", new FieldChange("confidence",
                new Confidence(ConfidenceOrigin.STATED, 0.9, null),
                new Confidence(ConfidenceOrigin.INFERRED, 0.5, null))),
            t1, "consolidation:MergeDetectionPhase"));

        var trace = CognitionTraceService.trace(snapshotStore,
            "t1", "n1", null, null);

        assertEquals("n1", trace.entityId());
        assertEquals(2, trace.events().size());
        assertEquals(TraceEvent.Type.CREATED, trace.events().get(0).type());
        assertEquals(TraceEvent.Type.UPDATED, trace.events().get(1).type());
    }

    @Test
    void shouldShowMergeFromBothPerspectives() {
        var snapshotStore = new TestSnapshotStore();
        Instant t0 = Instant.now();

        snapshotStore.storeMutation("t1", new GraphMutation.NodesMerged(
            "keep", "remove", List.of(), t0, "consolidation:MergeDetectionPhase"));

        var absorbedTrace = CognitionTraceService.trace(snapshotStore,
            "t1", "remove", null, null);
        assertEquals(1, absorbedTrace.events().size());
        assertEquals(TraceEvent.Type.MERGED_INTO, absorbedTrace.events().getFirst().type());
        assertEquals("keep", absorbedTrace.events().getFirst().relatedEntities().getFirst());

        var survivorTrace = CognitionTraceService.trace(snapshotStore,
            "t1", "keep", null, null);
        assertEquals(1, survivorTrace.events().size());
        assertEquals(TraceEvent.Type.MERGED_FROM, survivorTrace.events().getFirst().type());
        assertEquals("remove", survivorTrace.events().getFirst().relatedEntities().getFirst());
    }

    @Test
    void shouldTraceSupersession() {
        var snapshotStore = new TestSnapshotStore();
        Instant t0 = Instant.now();

        snapshotStore.storeMutation("t1", new GraphMutation.NodeSuperseded(
            "old", "new", "duplicate", t0, "manual"));

        var supersededTrace = CognitionTraceService.trace(snapshotStore,
            "t1", "old", null, null);
        assertEquals(TraceEvent.Type.SUPERSEDED, supersededTrace.events().getFirst().type());
        assertEquals("new", supersededTrace.events().getFirst().relatedEntities().getFirst());

        var supersedingTrace = CognitionTraceService.trace(snapshotStore,
            "t1", "new", null, null);
        assertEquals(TraceEvent.Type.SUPERSEDED_BY, supersedingTrace.events().getFirst().type());
        assertEquals("old", supersedingTrace.events().getFirst().relatedEntities().getFirst());
    }

    @Test
    void shouldTraceReinstatement() {
        var snapshotStore = new TestSnapshotStore();
        Instant t0 = Instant.now();

        snapshotStore.storeMutation("t1", new GraphMutation.NodeReinstated(
            "n1", t0, "manual"));

        var trace = CognitionTraceService.trace(snapshotStore,
            "t1", "n1", null, null);
        assertEquals(TraceEvent.Type.REINSTATED, trace.events().getFirst().type());
    }

    @Test
    void shouldTraceAliasOperations() {
        var snapshotStore = new TestSnapshotStore();
        Instant t0 = Instant.now().minusSeconds(10);
        Instant t1 = Instant.now();

        snapshotStore.storeMutation("t1", new GraphMutation.AliasAdded(
            "n1", "Al", t0, "manual"));
        snapshotStore.storeMutation("t1", new GraphMutation.AliasRemoved(
            "n1", "Al", t1, "manual"));

        var trace = CognitionTraceService.trace(snapshotStore,
            "t1", "n1", null, null);
        assertEquals(2, trace.events().size());
        assertEquals(TraceEvent.Type.ALIAS_ADDED, trace.events().get(0).type());
        assertEquals(TraceEvent.Type.ALIAS_REMOVED, trace.events().get(1).type());
    }

    @Test
    void shouldTraceErasure() {
        var snapshotStore = new TestSnapshotStore();
        Instant t0 = Instant.now();

        snapshotStore.storeMutation("t1", new GraphMutation.NodeErased(
            "n1", "sg1", 3, t0, "manual"));

        var trace = CognitionTraceService.trace(snapshotStore,
            "t1", "n1", null, null);
        assertEquals(TraceEvent.Type.ERASED, trace.events().getFirst().type());
    }

    @Test
    void shouldFilterByTimeRange() {
        var snapshotStore = new TestSnapshotStore();
        Instant t0 = Instant.now().minusSeconds(60);
        Instant t1 = Instant.now().minusSeconds(30);
        Instant t2 = Instant.now();

        snapshotStore.storeMutation("t1", new GraphMutation.NodeAdded(
            "n1", "Alice", "sg1", new Confidence(ConfidenceOrigin.STATED, 0.9, null),
            t0, "manual"));
        snapshotStore.storeMutation("t1", new GraphMutation.NodeUpdated(
            "n1", "sg1", Map.of("name", new FieldChange("name", "Alice", "Alicia")),
            t2, "manual"));

        var trace = CognitionTraceService.trace(snapshotStore,
            "t1", "n1", t1, t2);

        assertEquals(1, trace.events().size());
        assertEquals(TraceEvent.Type.UPDATED, trace.events().getFirst().type());
    }

    @Test
    void shouldReturnEmptyTraceForUnknownEntity() {
        var snapshotStore = new TestSnapshotStore();

        var trace = CognitionTraceService.trace(snapshotStore,
            "t1", "nonexistent", null, null);

        assertEquals("nonexistent", trace.entityId());
        assertTrue(trace.events().isEmpty());
    }

    @Test
    void shouldTraceEdgeOperations() {
        var snapshotStore = new TestSnapshotStore();
        Instant t0 = Instant.now().minusSeconds(10);
        Instant t1 = Instant.now();

        snapshotStore.storeMutation("t1", new GraphMutation.EdgeAdded(
            "e1", "n1", "n2", "knows",
            new Confidence(ConfidenceOrigin.STATED, 0.9, null), t0, "manual"));
        snapshotStore.storeMutation("t1", new GraphMutation.EdgeRemoved(
            "e1", "n1", "n2", "knows", t1, "manual"));

        var traceN1 = CognitionTraceService.trace(snapshotStore,
            "t1", "n1", null, null);
        assertEquals(2, traceN1.events().size());

        var traceN2 = CognitionTraceService.trace(snapshotStore,
            "t1", "n2", null, null);
        assertEquals(2, traceN2.events().size());
    }
}
