package io.casehub.neocortex.caps.engine;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DegreeAccumulatorTest {

    @Test
    void quietTicksNeverFire() {
        var acc = new DegreeAccumulator(0.2, 1.0, 0.85);
        for (int i = 0; i < 100; i++) {
            assertThat(acc.accumulate(0.1)).isFalse();
        }
        assertThat(acc.currentLevel()).isEqualTo(0.0);
    }

    @Test
    void dramaticEventFiresWithinTwoTicks() {
        var acc = new DegreeAccumulator(0.2, 1.0, 0.85);
        assertThat(acc.accumulate(0.9)).isFalse(); // S = 0.7
        assertThat(acc.accumulate(0.9)).isTrue();  // S = 0.85*0.7 + 0.7 = 1.295 >= 1.0
    }

    @Test
    void resetsAfterFiring() {
        var acc = new DegreeAccumulator(0.2, 1.0, 0.85);
        acc.accumulate(0.9);
        acc.accumulate(0.9); // fires
        assertThat(acc.currentLevel()).isEqualTo(0.0);
    }

    @Test
    void sustainedMinorEventsConvergeBelowThreshold() {
        var acc = new DegreeAccumulator(0.2, 1.0, 0.85);
        for (int i = 0; i < 200; i++) {
            assertThat(acc.accumulate(0.3)).isFalse();
        }
        assertThat(acc.currentLevel()).isLessThan(1.0);
    }

    @Test
    void escalatingTensionFires() {
        var acc = new DegreeAccumulator(0.2, 1.0, 0.85);
        acc.accumulate(0.3); // S = 0.1
        acc.accumulate(0.3); // S = 0.085 + 0.1 = 0.185
        acc.accumulate(0.5); // S = 0.157 + 0.3 = 0.457
        acc.accumulate(0.7); // S = 0.388 + 0.5 = 0.888
        assertThat(acc.accumulate(0.6)).isTrue(); // S = 0.755 + 0.4 = 1.155 >= 1.0
    }

    @Test
    void decayPreventsStaleAccumulation() {
        var acc = new DegreeAccumulator(0.2, 1.0, 0.85);
        acc.accumulate(0.7); // S = 0.5
        for (int i = 0; i < 20; i++) {
            acc.accumulate(0.1);
        }
        assertThat(acc.currentLevel()).isLessThan(0.05);
    }

    @Test
    void floorAtZero() {
        var acc = new DegreeAccumulator(0.2, 1.0, 0.85);
        acc.accumulate(0.0);
        assertThat(acc.currentLevel()).isEqualTo(0.0);
    }

    @Test
    void defaultParametersWork() {
        var acc = DegreeAccumulator.withDefaults();
        assertThat(acc.accumulate(0.1)).isFalse();
    }

    @Test
    void sustainedModerateDegreeFiresSlowChannel() {
        var     config     = new DegreeAccumulatorConfig(0.2, 1.0, 0.85, 0.15, 2.0);
        var     acc        = DegreeAccumulator.fromConfig(config);
        boolean fired      = false;
        int     fireAtTick = -1;
        for (int i = 1; i <= 20; i++) {
            if (acc.accumulate(0.3)) {
                fired      = true;
                fireAtTick = i;
                break;
            }
        }
        assertThat(fired).as("slow channel should fire for sustained degree=0.3").isTrue();
        assertThat(fireAtTick).as("should fire around tick 14").isBetween(12, 16);
    }

    @Test
    void slowChannelDoesNotFireForNoise() {
        var config = new DegreeAccumulatorConfig(0.2, 1.0, 0.85, 0.15, 2.0);
        var acc    = DegreeAccumulator.fromConfig(config);
        for (int i = 0; i < 100; i++) {
            assertThat(acc.accumulate(0.1)).isFalse();
        }
    }

    @Test
    void fromConfigPreservesParameters() {
        var config = new DegreeAccumulatorConfig(0.3, 1.5, 0.9, 0.1, 3.0);
        var acc    = DegreeAccumulator.fromConfig(config);
        // High threshold: degree=0.9 → fast level = 0.6, needs more ticks
        assertThat(acc.accumulate(0.9)).isFalse();
        assertThat(acc.accumulate(0.9)).isFalse(); // 0.6*0.9 + 0.6 = 1.14 < 1.5
        assertThat(acc.accumulate(0.9)).isTrue();  // should exceed 1.5
    }

    @Test
    void firingResetssBothChannels() {
        var config = new DegreeAccumulatorConfig(0.2, 1.0, 0.85, 0.15, 2.0);
        var acc    = DegreeAccumulator.fromConfig(config);
        // Force fire via fast channel
        acc.accumulate(0.9);
        acc.accumulate(0.9); // fires
        assertThat(acc.currentLevel()).isEqualTo(0.0);
        assertThat(acc.slowLevel()).isEqualTo(0.0);
    }

}
