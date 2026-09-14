package io.casehub.neocortex.cognitive.observability.testing;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.observability.ConsolidationAuditEntry;
import io.casehub.neocortex.cognitive.observability.EdgeSnapshot;
import io.casehub.neocortex.cognitive.observability.FieldChange;
import io.casehub.neocortex.cognitive.observability.GraphMutation;
import io.casehub.neocortex.cognitive.observability.GraphSnapshot;
import io.casehub.neocortex.cognitive.observability.NodeSnapshot;
import io.casehub.neocortex.cognitive.observability.SnapshotRetentionPolicy;
import io.casehub.neocortex.cognitive.observability.SnapshotStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public abstract class SnapshotStoreContractTest {

    protected abstract SnapshotStore createStore();

    private SnapshotStore store;
    private static final Confidence STATED = new Confidence(ConfidenceOrigin.STATED, 0.9, null);

    @BeforeEach
    void setUp() {
        store = createStore();
    }

    @Test
    void shouldStoreMutationAndFindByTimeRange() {
        Instant t0 = Instant.now().minusSeconds(60);
        Instant t1 = Instant.now().minusSeconds(30);

        store.storeMutation("t1", new GraphMutation.NodeAdded("n1", "Alice", "sg1", STATED, t0, "manual"));
        store.storeMutation("t1", new GraphMutation.NodeAdded("n2", "Bob", "sg1", STATED, t1, "manual"));

        var results = store.findMutations("t1", "sg1", t0.minusSeconds(1), Instant.now());
        assertEquals(2, results.size());
    }

    @Test
    void shouldFilterByTenant() {
        Instant now = Instant.now();
        store.storeMutation("t1", new GraphMutation.NodeAdded("n1", "Alice", "sg1", STATED, now, "manual"));
        store.storeMutation("t2", new GraphMutation.NodeAdded("n2", "Bob", "sg1", STATED, now, "manual"));

        var results = store.findMutations("t1", null, now.minusSeconds(1), now.plusSeconds(1));
        assertEquals(1, results.size());
        assertEquals("Alice", ((GraphMutation.NodeAdded) results.getFirst()).name());
    }

    @Test
    void shouldFindMutationsForEntity() {
        Instant now = Instant.now();
        store.storeMutation("t1", new GraphMutation.NodeAdded("n1", "Alice", "sg1", STATED, now.minusSeconds(10), "manual"));
        store.storeMutation("t1", new GraphMutation.NodeUpdated("n1", "sg1",
            Map.of("name", new FieldChange("name", "Alice", "Alicia")), now.minusSeconds(5), "manual"));
        store.storeMutation("t1", new GraphMutation.NodeAdded("n2", "Bob", "sg1", STATED, now, "manual"));

        var results = store.findMutationsForEntity("t1", "n1", now.minusSeconds(60), now.plusSeconds(1));
        assertEquals(2, results.size());
    }

    @Test
    void shouldFindMergesForBothNodes() {
        Instant now = Instant.now();
        store.storeMutation("t1", new GraphMutation.NodesMerged("keep", "remove", List.of(), now, "manual"));

        var keepResults = store.findMutationsForEntity("t1", "keep", now.minusSeconds(1), now.plusSeconds(1));
        var removeResults = store.findMutationsForEntity("t1", "remove", now.minusSeconds(1), now.plusSeconds(1));

        assertEquals(1, keepResults.size());
        assertEquals(1, removeResults.size());
        assertSame(keepResults.getFirst().getClass(), removeResults.getFirst().getClass());
    }

    @Test
    void shouldFindEdgesForBothEndpoints() {
        Instant now = Instant.now();
        store.storeMutation("t1", new GraphMutation.EdgeAdded("e1", "n1", "n2", "knows", STATED, now, "manual"));

        var n1Results = store.findMutationsForEntity("t1", "n1", now.minusSeconds(1), now.plusSeconds(1));
        var n2Results = store.findMutationsForEntity("t1", "n2", now.minusSeconds(1), now.plusSeconds(1));

        assertEquals(1, n1Results.size());
        assertEquals(1, n2Results.size());
    }

    @Test
    void shouldStoreAndRetrieveKeyframe() {
        var keyframe = new GraphSnapshot("kf1", "t1", "sg1", Instant.now(),
            GraphSnapshot.SnapshotType.KEYFRAME,
            List.of(new NodeSnapshot("n1", "Alice", "person", STATED, null, null, null,
                Set.of("Personable"), Set.of(), Map.of(), Instant.now(), Instant.now())),
            List.of());

        store.storeKeyframe(keyframe);

        var latest = store.latestKeyframe("t1", "sg1");
        assertTrue(latest.isPresent());
        assertEquals("kf1", latest.get().snapshotId());
        assertEquals(1, latest.get().nodes().size());
    }

    @Test
    void shouldCountMutationsSinceKeyframe() {
        Instant kfTime = Instant.now().minusSeconds(30);
        store.storeKeyframe(new GraphSnapshot("kf1", "t1", "sg1", kfTime,
            GraphSnapshot.SnapshotType.KEYFRAME, List.of(), List.of()));

        store.storeMutation("t1", new GraphMutation.NodeAdded("n1", "A", "sg1", STATED,
            kfTime.plusSeconds(5), "manual"));
        store.storeMutation("t1", new GraphMutation.NodeAdded("n2", "B", "sg1", STATED,
            kfTime.plusSeconds(10), "manual"));

        assertEquals(2, store.mutationCountSinceKeyframe("t1", "sg1"));
    }

    @Test
    void shouldStoreAndFindAuditEntries() {
        Instant now = Instant.now();
        var entry = new ConsolidationAuditEntry("t1", now.minusSeconds(5), now, List.of(
            new ConsolidationAuditEntry.PhaseAuditEntry("Phase1", Duration.ofSeconds(3), 5, true, null)));

        store.storeAuditEntry(entry);

        var entries = store.findAuditEntries("t1", now.minusSeconds(60), now.plusSeconds(1));
        assertEquals(1, entries.size());
        assertEquals(1, entries.getFirst().phases().size());
        assertEquals("Phase1", entries.getFirst().phases().getFirst().phaseName());
    }

    @Test
    void shouldReturnLastConsolidationTime() {
        Instant t1 = Instant.now().minusSeconds(60);
        Instant t2 = Instant.now().minusSeconds(30);

        store.storeAuditEntry(new ConsolidationAuditEntry("tenant1", t1.minusSeconds(5), t1, List.of()));
        store.storeAuditEntry(new ConsolidationAuditEntry("tenant1", t2.minusSeconds(5), t2, List.of()));

        var last = store.lastConsolidationTime("tenant1");
        assertTrue(last.isPresent());
        assertEquals(t2, last.get());
    }

    @Test
    void shouldPurgeOldEntries() {
        Instant old = Instant.now().minusSeconds(7200);
        Instant recent = Instant.now().minusSeconds(10);

        store.storeMutation("t1", new GraphMutation.NodeAdded("n1", "Old", "sg1", STATED, old, "manual"));
        store.storeMutation("t1", new GraphMutation.NodeAdded("n2", "Recent", "sg1", STATED, recent, "manual"));

        store.purge(new SnapshotRetentionPolicy(Duration.ofHours(1), "t1"));

        var results = store.findMutations("t1", null, Instant.EPOCH, Instant.now().plusSeconds(60));
        assertEquals(1, results.size());
        assertEquals("Recent", ((GraphMutation.NodeAdded) results.getFirst()).name());
    }

    @Test
    void shouldCountByTenant() {
        Instant now = Instant.now();
        store.storeMutation("t1", new GraphMutation.NodeAdded("n1", "A", "sg1", STATED, now, "manual"));
        store.storeMutation("t1", new GraphMutation.NodeAdded("n2", "B", "sg1", STATED, now, "manual"));
        store.storeMutation("t2", new GraphMutation.NodeAdded("n3", "C", "sg1", STATED, now, "manual"));

        assertEquals(2, store.count("t1"));
        assertEquals(1, store.count("t2"));
    }

    @Test
    void shouldReturnEmptyForMissingTenant() {
        var results = store.findMutations("nonexistent", null, Instant.EPOCH, Instant.now());
        assertTrue(results.isEmpty());

        var latest = store.latestKeyframe("nonexistent", "sg1");
        assertFalse(latest.isPresent());

        var last = store.lastConsolidationTime("nonexistent");
        assertFalse(last.isPresent());
    }
}
