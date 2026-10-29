package io.casehub.neocortex.memory.importance;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.ImportanceScorer;

import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class ImportanceScoringObserver {

    private static final Logger LOG = Logger.getLogger(ImportanceScoringObserver.class.getName());

    private final ImportanceScorer scorer;
    private final CaseMemoryStore store;
    private final int batchSize;
    private final long deadlineMs;
    private final ConcurrentLinkedQueue<ImportanceScorer.ScoringEntry> buffer = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> deadlineTask;

    public ImportanceScoringObserver(ImportanceScorer scorer, CaseMemoryStore store,
                                     ImportanceScoringConfig config) {
        this.scorer = scorer;
        this.store = store;
        this.batchSize = config.batchSize();
        this.deadlineMs = config.batchDeadlineMs();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "importance-scorer");
            t.setDaemon(true);
            return t;
        });
    }

    void onExperienceRecorded(@Observes ExperienceRecorded event) {
        String domain = event.event().getClass().getSimpleName().toLowerCase();
        buffer.add(new ImportanceScorer.ScoringEntry(event.memoryId(), event.event().description(),
            domain, event.event().tenantId()));

        if (buffer.size() >= batchSize) {
            flush();
        } else if (deadlineTask == null || deadlineTask.isDone()) {
            deadlineTask = scheduler.schedule(this::flush, deadlineMs, TimeUnit.MILLISECONDS);
        }
    }

    synchronized void flush() {
        List<ImportanceScorer.ScoringEntry> batch = new ArrayList<>();
        ImportanceScorer.ScoringEntry entry;
        while ((entry = buffer.poll()) != null) batch.add(entry);
        if (batch.isEmpty()) return;

        try {
            Map<String, Integer> scores = scorer.score(batch);
            for (var e : scores.entrySet()) {
                String tenantId = findTenantId(batch, e.getKey());
                if (tenantId != null) {
                    store.enrichAttributes(e.getKey(),
                        Map.of("importance", String.valueOf(e.getValue())), tenantId);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Importance scoring batch failed", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        flush();
    }

    private static String findTenantId(List<ImportanceScorer.ScoringEntry> batch, String memoryId) {
        for (var entry : batch) {
            if (entry.memoryId().equals(memoryId)) return entry.tenantId();
        }
        return null;
    }
}
