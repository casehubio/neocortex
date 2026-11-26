package io.casehub.neocortex.cognition.mood;

import io.casehub.neocortex.memory.mood.MoodBaseline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MoodOrchestratorTest {

    private MoodOrchestrator orchestrator;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-08-21T00:00:00Z"), ZoneId.of("UTC"));
        orchestrator = new MoodOrchestrator(MoodConfig.defaults(), clock);
    }

    @Test void tick_noState_returnsNoChange() {
        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MoodTick.NoChange.class);
    }

    @Test void tick_withSignal_returnsUpdated() {
        orchestrator.record(
                new MoodSignal.InteractionAppraisal(0.3, 0.1, 0.0, "positive interaction"),
                "agent-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MoodTick.Updated.class);
        var updated = (MoodTick.Updated) result;
        assertThat(updated.signalsApplied()).isEqualTo(1);
        assertThat(updated.moodState().pleasure()).isCloseTo(0.3, within(0.01));
        assertThat(updated.moodState().arousal()).isCloseTo(0.1, within(0.01));
    }

    @Test void tick_multipleSignals_accumulate() {
        orchestrator.record(
                new MoodSignal.InteractionAppraisal(0.2, 0.1, 0.0, "first"),
                "agent-1", "tenant-1");
        orchestrator.record(
                new MoodSignal.InteractionAppraisal(0.3, -0.1, 0.1, "second"),
                "agent-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MoodTick.Updated.class);
        var updated = (MoodTick.Updated) result;
        assertThat(updated.signalsApplied()).isEqualTo(2);
        assertThat(updated.moodState().pleasure()).isCloseTo(0.5, within(0.01));
        assertThat(updated.moodState().arousal()).isCloseTo(0.0, within(0.01));
        assertThat(updated.moodState().dominance()).isCloseTo(0.1, within(0.01));
    }

    @Test void tick_afterDrain_noSignals_returnsNoChange() {
        orchestrator.record(
                new MoodSignal.InteractionAppraisal(0.3, 0.0, 0.0, "first"),
                "agent-1", "tenant-1");
        orchestrator.tick("agent-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MoodTick.NoChange.class);
    }

    @Test void tick_clampsToMaxDisplacement() {
        orchestrator.record(
                new MoodSignal.DirectShift(1.5, 0.0, 0.0, "extreme joy"),
                "agent-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        assertThat(result).isInstanceOf(MoodTick.Updated.class);
        var updated = (MoodTick.Updated) result;
        assertThat(updated.moodState().pleasure()).isEqualTo(1.0);
    }

    @Test void tick_clampsNegativeToMaxDisplacement() {
        orchestrator.record(
                new MoodSignal.DirectShift(-1.5, 0.0, 0.0, "extreme sadness"),
                "agent-1", "tenant-1");

        var result = orchestrator.tick("agent-1", "tenant-1");
        var updated = (MoodTick.Updated) result;
        assertThat(updated.moodState().pleasure()).isEqualTo(-1.0);
    }

    @Test void tick_clampsRelativeToBaseline() {
        var config = new MoodConfig(
                new MoodBaseline(0.5, 0.0, 0.0),
                Duration.ofHours(4), 0.3, 0.3, Duration.ofHours(24));
        var orch = new MoodOrchestrator(config, clock);

        orch.record(new MoodSignal.DirectShift(0.5, 0.0, 0.0, "shift"),
                "agent-1", "tenant-1");

        var result = orch.tick("agent-1", "tenant-1");
        var updated = (MoodTick.Updated) result;
        assertThat(updated.moodState().pleasure()).isCloseTo(0.8, within(0.01));
    }

    @Test void tick_decaysTowardBaseline() {
        var config = new MoodConfig(
                new MoodBaseline(0.0, 0.0, 0.0),
                Duration.ofHours(1), 1.0, 0.3, Duration.ofHours(24));
        var t0 = Instant.parse("2026-08-21T00:00:00Z");
        var orch = new MoodOrchestrator(config,
                Clock.fixed(t0, ZoneId.of("UTC")));

        orch.record(new MoodSignal.DirectShift(0.8, 0.0, 0.0, "spike"),
                "agent-1", "tenant-1");
        orch.tick("agent-1", "tenant-1");

        var current = orch.currentMood("agent-1", "tenant-1");
        assertThat(current).isPresent();
        assertThat(current.get().pleasure()).isCloseTo(0.8, within(0.01));
    }

    @Test void currentMood_returnsEmpty_whenNoState() {
        assertThat(orchestrator.currentMood("agent-1", "tenant-1")).isEmpty();
    }

    @Test void currentMood_returnsInitialBaseline_afterRecord() {
        orchestrator.record(
                new MoodSignal.InteractionAppraisal(0.0, 0.0, 0.0, null),
                "agent-1", "tenant-1");
        var mood = orchestrator.currentMood("agent-1", "tenant-1");
        assertThat(mood).isPresent();
        assertThat(mood.get().pleasure()).isEqualTo(0.0);
    }

    @Test void currentMood_reflectsLastTick() {
        orchestrator.record(
                new MoodSignal.DirectShift(0.5, 0.2, -0.1, "event"),
                "agent-1", "tenant-1");
        orchestrator.tick("agent-1", "tenant-1");

        var mood = orchestrator.currentMood("agent-1", "tenant-1");
        assertThat(mood).isPresent();
        assertThat(mood.get().pleasure()).isCloseTo(0.5, within(0.01));
        assertThat(mood.get().arousal()).isCloseTo(0.2, within(0.01));
        assertThat(mood.get().dominance()).isCloseTo(-0.1, within(0.01));
    }

    @Test void separateAgents_haveSeparateMood() {
        orchestrator.record(
                new MoodSignal.DirectShift(0.8, 0.0, 0.0, "happy"),
                "agent-1", "tenant-1");
        orchestrator.record(
                new MoodSignal.DirectShift(-0.5, 0.0, 0.0, "sad"),
                "agent-2", "tenant-1");
        orchestrator.tick("agent-1", "tenant-1");
        orchestrator.tick("agent-2", "tenant-1");

        assertThat(orchestrator.currentMood("agent-1", "tenant-1").get().pleasure())
                .isCloseTo(0.8, within(0.01));
        assertThat(orchestrator.currentMood("agent-2", "tenant-1").get().pleasure())
                .isCloseTo(-0.5, within(0.01));
    }

    @Test void interactionAppraisal_allowsNullCause() {
        var signal = new MoodSignal.InteractionAppraisal(0.1, 0.0, 0.0, null);
        assertThat(signal.cause()).isNull();
    }

    @Test void directShift_requiresNonBlankCause() {
        assertThatThrownBy(() -> new MoodSignal.DirectShift(0.1, 0.0, 0.0, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void directShift_requiresNonNullCause() {
        assertThatThrownBy(() -> new MoodSignal.DirectShift(0.1, 0.0, 0.0, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void signal_rejectsDeltaOutOfRange() {
        assertThatThrownBy(() -> new MoodSignal.InteractionAppraisal(3.0, 0.0, 0.0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void moodConfig_defaults_valid() {
        var config = MoodConfig.defaults();
        assertThat(config.baseline().pleasure()).isEqualTo(0.0);
        assertThat(config.decayTimeConstant()).isEqualTo(Duration.ofHours(4));
        assertThat(config.maxDisplacement()).isEqualTo(1.0);
        assertThat(config.moodInfluence()).isEqualTo(0.3);
    }

    @Test void moodConfig_rejectsZeroDecay() {
        assertThatThrownBy(() -> new MoodConfig(
                new MoodBaseline(0, 0, 0), Duration.ZERO, 1.0, 0.3, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void moodConfig_rejectsZeroDisplacement() {
        assertThatThrownBy(() -> new MoodConfig(
                new MoodBaseline(0, 0, 0), Duration.ofHours(4), 0.0, 0.3, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void moodConfig_rejectsNegativeMoodInfluence() {
        assertThatThrownBy(() -> new MoodConfig(
                new MoodBaseline(0, 0, 0), Duration.ofHours(4), 1.0, -0.1, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void evictIdleRemovesStaleEntries() {
        orchestrator.record(
                new MoodSignal.InteractionAppraisal(0.3, 0.1, 0.0, "test"),
                "agent-1", "tenant-1");
        orchestrator.tick("agent-1", "tenant-1");
        assertThat(orchestrator.currentMood("agent-1", "tenant-1")).isPresent();

        orchestrator.evictIdle(Duration.ZERO);

        assertThat(orchestrator.currentMood("agent-1", "tenant-1")).isEmpty();
    }

    @Test
    void concurrentRecordAndTickDoNotCorruptState() throws Exception {
        int iterations = 1000;
        var latch      = new java.util.concurrent.CountDownLatch(1);
        var errors     = new java.util.concurrent.atomic.AtomicInteger(0);

        var recorder = Thread.startVirtualThread(() -> {
            try {latch.await();} catch (InterruptedException e) {return;}
            for (int i = 0; i < iterations; i++) {
                try {
                    orchestrator.record(new MoodSignal.DirectShift(0.01, 0.0, 0.0, "test"), "agent-1", "tenant-1");
                } catch (Exception e) {errors.incrementAndGet();}
            }
        });

        var ticker = Thread.startVirtualThread(() -> {
            try {latch.await();} catch (InterruptedException e) {return;}
            for (int i = 0; i < iterations; i++) {
                try {
                    orchestrator.tick("agent-1", "tenant-1");
                } catch (Exception e) {errors.incrementAndGet();}
            }
        });

        latch.countDown();
        recorder.join(5000);
        ticker.join(5000);

        assertThat(errors.get()).as("no exceptions from concurrent access").isZero();
    }

    @Test
    void freshSignalAfterIdleGapRetainsFullStrength() {
        var mutableClock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        var orch         = new MoodOrchestrator(MoodConfig.defaults(), mutableClock);

        // First tick to establish state
        orch.record(new MoodSignal.DirectShift(0.01, 0.0, 0.0, "init"), "agent-1", "tenant-1");
        orch.tick("agent-1", "tenant-1");

        // Advance 2 hours, record a signal, then tick
        mutableClock.advance(Duration.ofHours(2));
        orch.record(new MoodSignal.DirectShift(0.5, 0.0, 0.0, "test"), "agent-1", "tenant-1");
        orch.tick("agent-1", "tenant-1");

        var mood = orch.currentMood("agent-1", "tenant-1");
        assertThat(mood).isPresent();
        assertThat(mood.get().pleasure())
                .as("signal after idle gap should retain most of its strength")
                .isGreaterThan(0.4);
    }

    @Test
    void moodDecaysTowardGoalBiasNotZero() {
        var mutableClock = new MutableClock(Instant.parse("2026-01-01T12:00:00Z"));
        var orch         = new MoodOrchestrator(MoodConfig.defaults(), mutableClock);

        // Set a positive goal bias
        orch.setGoalBias("agent-1", "tenant-1",
                         new io.casehub.neocortex.memory.mood.MoodState("agent-1", "tenant-1",
                                                                        null, 0.3, 0.0, 0.0, "goal-bias", null, java.util.Set.of(), java.util.Map.of()));

        // Push mood positive
        orch.record(new MoodSignal.DirectShift(0.8, 0.0, 0.0, "test"), "agent-1", "tenant-1");
        orch.tick("agent-1", "tenant-1");

        // Let it decay for a long time (24h >> 4h time constant)
        mutableClock.advance(Duration.ofHours(24));
        orch.tick("agent-1", "tenant-1");

        var mood = orch.currentMood("agent-1", "tenant-1");
        assertThat(mood).isPresent();
        assertThat(mood.get().pleasure())
                .as("mood should decay toward goal bias (0.3), not zero")
                .isCloseTo(0.3, within(0.05));
    }


    private static class MutableClock extends Clock {
        private volatile Instant now;
        private final    ZoneId  zone;

        MutableClock(Instant initial) {
            this.now  = initial;
            this.zone = ZoneId.of("UTC");
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone()            {return zone;}

        @Override
        public Clock withZone(ZoneId zone) {return this;}

        @Override
        public Instant instant()           {return now;}
    }
}
