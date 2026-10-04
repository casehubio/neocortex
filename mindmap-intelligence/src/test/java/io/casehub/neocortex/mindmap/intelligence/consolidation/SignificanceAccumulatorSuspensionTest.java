package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.Observation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SignificanceAccumulatorSuspensionTest {

    @Test
    void suspendedAccumulatorIgnoresEvents() {
        var triggered = new AtomicInteger(0);
        var acc = new SignificanceAccumulator(
            e -> 1.0, 3.0, t -> triggered.incrementAndGet(), false);

        acc.suspend();
        for (int i = 0; i < 10; i++) acc.onExperienceRecorded(testEvent());

        assertThat(triggered.get()).isZero();
    }

    @Test
    void resumedAccumulatorAcceptsEvents() {
        var triggered = new AtomicInteger(0);
        var acc = new SignificanceAccumulator(
            e -> 1.0, 3.0, t -> triggered.incrementAndGet(), false);

        acc.suspend();
        for (int i = 0; i < 5; i++) acc.onExperienceRecorded(testEvent());
        assertThat(triggered.get()).isZero();

        acc.resume();
        for (int i = 0; i < 5; i++) acc.onExperienceRecorded(testEvent());
        assertThat(triggered.get()).isEqualTo(1);
    }

    private ExperienceRecorded testEvent() {
        var obs = new Observation("a", "t", null, null,
            Instant.now(), "test", 0.5, Map.of(), "sub");
        return new ExperienceRecorded(obs, "mem1");
    }
}
