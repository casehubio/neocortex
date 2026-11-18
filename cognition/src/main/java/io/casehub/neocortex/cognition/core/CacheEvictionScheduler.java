package io.casehub.neocortex.cognition.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CacheEvictionScheduler {

    static final int DEFAULT_TICK_THRESHOLD = 60;
    static final Duration DEFAULT_TIME_THRESHOLD = Duration.ofMinutes(5);
    static final Duration DEFAULT_MAX_IDLE = Duration.ofMinutes(10);

    private final int tickThreshold;
    private final Duration timeThreshold;
    private final Duration maxIdle;
    private final Clock clock;
    private final List<Consumer<Duration>> evictors = new CopyOnWriteArrayList<>();

    private int lastEvictionTick;
    private Instant lastEvictionTime;

    public CacheEvictionScheduler() {
        this(DEFAULT_TICK_THRESHOLD, DEFAULT_TIME_THRESHOLD, DEFAULT_MAX_IDLE, Clock.systemUTC());
    }

    public CacheEvictionScheduler(int tickThreshold, Duration timeThreshold, Duration maxIdle, Clock clock) {
        this.tickThreshold = tickThreshold;
        this.timeThreshold = timeThreshold;
        this.maxIdle = maxIdle;
        this.clock = clock;
        this.lastEvictionTick = 0;
        this.lastEvictionTime = clock.instant();
    }

    public void register(Consumer<Duration> evictor) {
        evictors.add(evictor);
    }

    public void onTick(int tickNumber) {
        if (evictors.isEmpty()) {return;}
        int      ticksSince = tickNumber - lastEvictionTick;
        Duration timeSince  = Duration.between(lastEvictionTime, clock.instant());
        if (ticksSince >= tickThreshold && timeSince.compareTo(timeThreshold) >= 0) {
            for (var evictor : evictors) {
                try {
                    evictor.accept(maxIdle);
                } catch (Exception e) {
                    java.util.logging.Logger.getLogger(CacheEvictionScheduler.class.getName())
                                            .log(java.util.logging.Level.WARNING, "Cache evictor failed", e);
                }
            }
            lastEvictionTick = tickNumber;
            lastEvictionTime = clock.instant();
        }
    }
}
