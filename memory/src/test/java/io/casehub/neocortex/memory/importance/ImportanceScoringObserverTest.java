package io.casehub.neocortex.memory.importance;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.EraseRequest;
import io.casehub.neocortex.memory.ImportanceScorer;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.MemoryQuery;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.Observation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class ImportanceScoringObserverTest {

    private final List<List<ImportanceScorer.ScoringEntry>> scoredBatches = new CopyOnWriteArrayList<>();
    private final List<String> enrichedMemoryIds = new CopyOnWriteArrayList<>();

    private final ImportanceScorer scorer = batch -> {
        scoredBatches.add(new ArrayList<>(batch));
        Map<String, Integer> result = new HashMap<>();
        for (var entry : batch) result.put(entry.memoryId(), 7);
        return result;
    };

    private final CaseMemoryStore store = new MinimalStore() {
        @Override
        public void enrichAttributes(String memoryId, Map<String, String> attrs, String tenantId) {
            enrichedMemoryIds.add(memoryId);
        }
    };

    @Test
    void flushesOnCountThreshold() {
        var observer = new ImportanceScoringObserver(scorer, store, config(3, 60_000));

        observer.onExperienceRecorded(event("m1"));
        observer.onExperienceRecorded(event("m2"));
        assertThat(scoredBatches).isEmpty();

        observer.onExperienceRecorded(event("m3"));
        assertThat(scoredBatches).hasSize(1);
        assertThat(scoredBatches.get(0)).hasSize(3);
        assertThat(enrichedMemoryIds).containsExactly("m1", "m2", "m3");
    }

    @Test
    void flushOnDeadline() throws InterruptedException {
        var observer = new ImportanceScoringObserver(scorer, store, config(100, 100));

        observer.onExperienceRecorded(event("m1"));
        assertThat(scoredBatches).isEmpty();

        Thread.sleep(300);
        assertThat(scoredBatches).hasSize(1);
        assertThat(enrichedMemoryIds).containsExactly("m1");
    }

    @Test
    void noOpScorerSkipsEnrichment() {
        ImportanceScorer noOp = batch -> Map.of();
        var observer = new ImportanceScoringObserver(noOp, store, config(1, 60_000));
        observer.onExperienceRecorded(event("m1"));
        assertThat(enrichedMemoryIds).isEmpty();
    }

    @Test
    void shutdownFlushesRemaining() {
        var observer = new ImportanceScoringObserver(scorer, store, config(100, 60_000));
        observer.onExperienceRecorded(event("m1"));
        observer.shutdown();
        assertThat(scoredBatches).hasSize(1);
        assertThat(enrichedMemoryIds).containsExactly("m1");
    }

    private static ExperienceRecorded event(String memoryId) {
        return new ExperienceRecorded(
            new Observation("agent1", "tenant1", null, "turn1",
                Instant.now(), "Something happened", null, Map.of(), "subject1"),
            memoryId);
    }

    private static ImportanceScoringConfig config(int batchSize, long deadlineMs) {
        return new ImportanceScoringConfig() {
            @Override public boolean enabled() { return true; }
            @Override public int batchSize() { return batchSize; }
            @Override public long batchDeadlineMs() { return deadlineMs; }
        };
    }

    private static abstract class MinimalStore implements CaseMemoryStore {
        @Override public String store(MemoryInput input) { throw new UnsupportedOperationException(); }
        @Override public List<Memory> query(MemoryQuery query) { throw new UnsupportedOperationException(); }
        @Override public int erase(EraseRequest request) { throw new UnsupportedOperationException(); }
    }
}
