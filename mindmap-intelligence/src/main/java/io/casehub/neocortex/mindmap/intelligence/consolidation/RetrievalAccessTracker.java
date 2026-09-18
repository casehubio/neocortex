package io.casehub.neocortex.mindmap.intelligence.consolidation;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class RetrievalAccessTracker {

    private record State(ConcurrentHashMap<String, AtomicLong> counts,
                         ConcurrentHashMap<String, Instant> lastAccess) {
        State() {this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>());}
    }

    private volatile State state = new State();

    public void recordAccess(String nodeId) {
        State s = state;
        s.counts.computeIfAbsent(nodeId, k -> new AtomicLong()).incrementAndGet();
        s.lastAccess.put(nodeId, Instant.now());
    }

    public AccessSnapshot swapAndReset() {
        State old = state;
        state = new State();

        var snapshot = new HashMap<String, Long>();
        old.counts.forEach((nodeId, counter) -> snapshot.put(nodeId, counter.get()));

        return new AccessSnapshot(snapshot, Map.copyOf(old.lastAccess));
    }
}
