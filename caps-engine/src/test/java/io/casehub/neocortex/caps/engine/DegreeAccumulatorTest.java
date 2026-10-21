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
}
