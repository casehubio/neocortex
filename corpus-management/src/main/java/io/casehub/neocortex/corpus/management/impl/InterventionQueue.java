package io.casehub.neocortex.corpus.management.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InterventionQueue {

    private final ConcurrentHashMap<String, InterventionRequest> pending = new ConcurrentHashMap<>();
    private final Duration timeout;

    public InterventionQueue(Duration timeout) {
        this.timeout = timeout;
    }

    public InterventionRequest submit(String url, String reason, String screenshotRef) {
        String id = "intv-" + url.hashCode() + "-" + System.nanoTime();
        var request = new InterventionRequest(id, url, reason, screenshotRef, Instant.now());
        pending.put(id, request);
        return request;
    }

    public List<InterventionRequest> pending() {
        evictExpired();
        return List.copyOf(pending.values());
    }

    public Optional<InterventionRequest> resolve(String id) {
        evictExpired();
        return Optional.ofNullable(pending.remove(id));
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(timeout);
        pending.entrySet().removeIf(e -> e.getValue().submittedAt().isBefore(cutoff));
    }
}
