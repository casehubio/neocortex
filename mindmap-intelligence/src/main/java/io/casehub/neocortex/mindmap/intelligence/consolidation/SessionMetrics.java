package io.casehub.neocortex.mindmap.intelligence.consolidation;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public class SessionMetrics {

    private static final Logger LOG = Logger.getLogger(SessionMetrics.class.getName());

    private final String phase;
    private final AtomicInteger sessionsOpened = new AtomicInteger();
    private final AtomicInteger sessionsClosed = new AtomicInteger();
    private final AtomicInteger peakConcurrent = new AtomicInteger();
    private final AtomicInteger currentActive = new AtomicInteger();
    private final AtomicInteger totalQueries = new AtomicInteger();
    private final AtomicInteger totalClears = new AtomicInteger();
    private final AtomicLong totalTokensAtClear = new AtomicLong();
    private final AtomicLong wallClockMs = new AtomicLong();

    public SessionMetrics(String phase) {
        this.phase = phase;
    }

    void sessionOpened() {
        sessionsOpened.incrementAndGet();
        int active = currentActive.incrementAndGet();
        peakConcurrent.updateAndGet(peak -> Math.max(peak, active));
    }

    void sessionClosed() {
        sessionsClosed.incrementAndGet();
        currentActive.decrementAndGet();
    }

    void queryStarted() {
        totalQueries.incrementAndGet();
    }

    void clearTriggered(int estimatedTokensAtClear) {
        totalClears.incrementAndGet();
        totalTokensAtClear.addAndGet(estimatedTokensAtClear);
    }

    void executionCompleted(long elapsedMs) {
        wallClockMs.set(elapsedMs);
        LOG.info("[SessionMetrics:" + phase + "] " + summary());
    }

    public int sessionsOpened() { return sessionsOpened.get(); }
    public int sessionsClosed() { return sessionsClosed.get(); }
    public int peakConcurrent() { return peakConcurrent.get(); }
    public int totalQueries() { return totalQueries.get(); }
    public int totalClears() { return totalClears.get(); }
    public long totalTokensAtClear() { return totalTokensAtClear.get(); }
    public long wallClockMs() { return wallClockMs.get(); }

    public String summary() {
        return "sessions=" + sessionsOpened.get()
            + " peakConcurrent=" + peakConcurrent.get()
            + " queries=" + totalQueries.get()
            + " clears=" + totalClears.get()
            + (totalClears.get() > 0 ? " avgTokensAtClear=" + (totalTokensAtClear.get() / totalClears.get()) : "")
            + " wallClock=" + wallClockMs.get() + "ms";
    }
}
