package io.casehub.neocortex.mindmap.intelligence.consolidation;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class RetrievalAccessTracker {

    private volatile ConcurrentHashMap<String, AtomicLong> counts = new ConcurrentHashMap<>();
    private volatile ConcurrentHashMap<String, Instant> lastAccess = new ConcurrentHashMap<>();

    public void recordAccess(String nodeId) {
        counts.computeIfAbsent(nodeId, k -> new AtomicLong()).incrementAndGet();
        lastAccess.put(nodeId, Instant.now());
    }

    public AccessSnapshot swapAndReset() {
        var oldCounts = counts;
        var oldLastAccess = lastAccess;
        counts = new ConcurrentHashMap<>();
        lastAccess = new ConcurrentHashMap<>();

        var snapshot = new HashMap<String, Long>();
        oldCounts.forEach((nodeId, counter) -> snapshot.put(nodeId, counter.get()));

        return new AccessSnapshot(snapshot, Map.copyOf(oldLastAccess));
    }
}
