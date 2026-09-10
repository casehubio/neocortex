package io.casehub.neocortex.mindmap.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class IdleTracker {

    private volatile Instant lastWrite = Instant.EPOCH;

    public void recordWrite() {
        lastWrite = Instant.now();
    }

    void recordWriteAt(Instant instant) {
        lastWrite = instant;
    }

    public boolean isIdle(Duration threshold) {
        return Duration.between(lastWrite, Instant.now()).compareTo(threshold) > 0;
    }
}
