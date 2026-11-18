package io.casehub.neocortex.cognition.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CacheEvictionSchedulerTest {

    private static class TestClock extends Clock {
        private Instant instant;
        TestClock(Instant start) { this.instant = start; }
        void advance(Duration d) { instant = instant.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }

    @Test
    void doesNotEvictWhenOnlyTickThresholdMet() {
        var clock = new TestClock(Instant.now());
        var scheduler = new CacheEvictionScheduler(60, Duration.ofMinutes(5), Duration.ofMinutes(10), clock);
        var evicted = new AtomicInteger();
        scheduler.register(maxIdle -> evicted.incrementAndGet());

        for (int i = 1; i <= 100; i++) {
            scheduler.onTick(i);
        }
        assertThat(evicted.get()).isZero();
    }

    @Test
    void doesNotEvictWhenOnlyTimeThresholdMet() {
        var clock = new TestClock(Instant.now());
        var scheduler = new CacheEvictionScheduler(60, Duration.ofMinutes(5), Duration.ofMinutes(10), clock);
        var evicted = new AtomicInteger();
        scheduler.register(maxIdle -> evicted.incrementAndGet());

        clock.advance(Duration.ofMinutes(10));
        for (int i = 1; i <= 59; i++) {
            scheduler.onTick(i);
        }
        assertThat(evicted.get()).isZero();
    }

    @Test
    void evictsWhenBothThresholdsMet() {
        var clock = new TestClock(Instant.now());
        var scheduler = new CacheEvictionScheduler(60, Duration.ofMinutes(5), Duration.ofMinutes(10), clock);
        var receivedMaxIdle = new AtomicReference<Duration>();
        scheduler.register(receivedMaxIdle::set);

        clock.advance(Duration.ofMinutes(6));
        for (int i = 1; i <= 60; i++) {
            scheduler.onTick(i);
        }
        assertThat(receivedMaxIdle.get()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void resetsThresholdsAfterEviction() {
        var clock = new TestClock(Instant.now());
        var scheduler = new CacheEvictionScheduler(60, Duration.ofMinutes(5), Duration.ofMinutes(10), clock);
        var evicted = new AtomicInteger();
        scheduler.register(maxIdle -> evicted.incrementAndGet());

        // First eviction
        clock.advance(Duration.ofMinutes(6));
        for (int i = 1; i <= 60; i++) {
            scheduler.onTick(i);
        }
        assertThat(evicted.get()).isEqualTo(1);

        // 59 more ticks, no time advance -- should not evict
        for (int i = 61; i <= 119; i++) {
            scheduler.onTick(i);
        }
        assertThat(evicted.get()).isEqualTo(1);

        // Advance time and hit tick threshold again
        clock.advance(Duration.ofMinutes(6));
        scheduler.onTick(120);
        assertThat(evicted.get()).isEqualTo(2);
    }

    @Test
    void noOpWhenNoEvictorsRegistered() {
        var clock = new TestClock(Instant.now());
        var scheduler = new CacheEvictionScheduler(60, Duration.ofMinutes(5), Duration.ofMinutes(10), clock);

        clock.advance(Duration.ofMinutes(10));
        for (int i = 1; i <= 100; i++) {
            scheduler.onTick(i);
        }
    }

    @Test
    void callsAllRegisteredEvictors() {
        var clock = new TestClock(Instant.now());
        var scheduler = new CacheEvictionScheduler(60, Duration.ofMinutes(5), Duration.ofMinutes(10), clock);
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        scheduler.register(maxIdle -> first.incrementAndGet());
        scheduler.register(maxIdle -> second.incrementAndGet());

        clock.advance(Duration.ofMinutes(6));
        for (int i = 1; i <= 60; i++) {
            scheduler.onTick(i);
        }
        assertThat(first.get()).isEqualTo(1);
        assertThat(second.get()).isEqualTo(1);
    }

    @Test
    void survivesEvictorException() {
        var clock     = new TestClock(Instant.now());
        var scheduler = new CacheEvictionScheduler(60, Duration.ofMinutes(5), Duration.ofMinutes(10), clock);
        var second    = new AtomicInteger();
        scheduler.register(maxIdle -> {throw new RuntimeException("boom");});
        scheduler.register(maxIdle -> second.incrementAndGet());

        clock.advance(Duration.ofMinutes(6));
        for (int i = 1; i <= 60; i++) {
            scheduler.onTick(i);
        }
        assertThat(second.get()).isEqualTo(1);
    }


    @Test
    void defaultConstructorUsesStandardThresholds() {
        var scheduler = new CacheEvictionScheduler();
        var evicted = new AtomicInteger();
        scheduler.register(maxIdle -> {
            assertThat(maxIdle).isEqualTo(Duration.ofMinutes(10));
            evicted.incrementAndGet();
        });
        for (int i = 1; i <= 100; i++) {
            scheduler.onTick(i);
        }
        assertThat(evicted.get()).isZero();
    }
}
