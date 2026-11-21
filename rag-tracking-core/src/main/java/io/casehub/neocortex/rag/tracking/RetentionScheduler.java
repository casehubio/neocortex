package io.casehub.neocortex.rag.tracking;

import io.casehub.neocortex.rag.RetrievalTracker;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Purges retrieval records older than the retention period: first after one hour, then every 24
 * hours, on a daemon thread ({@code rag-retention-purge}). Plain class: the Quarkus and Spring
 * modules call {@link #start()} and {@link #stop()} from their lifecycles.
 */
public class RetentionScheduler {

    private static final Logger LOG = Logger.getLogger(RetentionScheduler.class);

    private final RetrievalTracker tracker;
    private final int retentionDays;

    private volatile ScheduledExecutorService executor;

    /**
     * @param retentionDays records older than this many days are purged; {@code <= 0} disables purging
     */
    public RetentionScheduler(RetrievalTracker tracker, int retentionDays) {
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.retentionDays = retentionDays;
    }

    public void start() {
        if (retentionDays <= 0) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rag-retention-purge");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleAtFixedRate(this::purge, 1, 24, TimeUnit.HOURS);
        LOG.infof("Retention scheduler started: %d-day retention, purge every 24h", retentionDays);
    }

    public void stop() {
        if (executor != null) executor.shutdown();
    }

    boolean started() {
        return executor != null;
    }

    void purge() {
        try {
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            int deleted = tracker.purgeOlderThan(cutoff);
            LOG.infof("Retention purge: deleted %d records older than %s", deleted, cutoff);
        } catch (Exception e) {
            LOG.error("Retention purge failed — will retry next cycle", e);
        }
    }
}
