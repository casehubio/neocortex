package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TemporalDecayTest {

    @Test void halfLife_rejectsNull() {
        assertThatThrownBy(() -> new TemporalDecay.HalfLife(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test void halfLife_rejectsZero() {
        assertThatThrownBy(() -> new TemporalDecay.HalfLife(Duration.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void halfLife_rejectsNegative() {
        assertThatThrownBy(() -> new TemporalDecay.HalfLife(Duration.ofDays(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void halfLife_noAge_factorIsOne() {
        var decay = new TemporalDecay.HalfLife(Duration.ofDays(30));
        Instant now = Instant.now();
        assertThat(decay.factor(now, now)).isEqualTo(1.0);
    }

    @Test void halfLife_exactlyOneHalfLife_factorIsHalf() {
        var decay = new TemporalDecay.HalfLife(Duration.ofDays(30));
        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));
        assertThat(decay.factor(thirtyDaysAgo, now)).isCloseTo(0.5, within(0.001));
    }

    @Test void halfLife_twoHalfLives_factorIsQuarter() {
        var decay = new TemporalDecay.HalfLife(Duration.ofDays(30));
        Instant now = Instant.now();
        Instant sixtyDaysAgo = now.minus(Duration.ofDays(60));
        assertThat(decay.factor(sixtyDaysAgo, now)).isCloseTo(0.25, within(0.001));
    }

    @Test void halfLife_futureStoredAt_factorIsOne() {
        var decay = new TemporalDecay.HalfLife(Duration.ofDays(30));
        Instant now = Instant.now();
        Instant future = now.plus(Duration.ofDays(1));
        assertThat(decay.factor(future, now)).isEqualTo(1.0);
    }

    @Test void halfLife_shortHalfLife_decaysFaster() {
        var fast = new TemporalDecay.HalfLife(Duration.ofHours(1));
        var slow = new TemporalDecay.HalfLife(Duration.ofDays(30));
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(Duration.ofHours(1));
        assertThat(fast.factor(oneHourAgo, now)).isLessThan(slow.factor(oneHourAgo, now));
    }

    @Test
    void halfLife_nullStoredAt_returnsOne() {
        var decay = new TemporalDecay.HalfLife(Duration.ofHours(1));
        assertThat(decay.factor(null, Instant.now())).isEqualTo(1.0);
    }

// --- Linear ---

    @Test
    void linear_rejectsNull() {
        assertThatThrownBy(() -> new TemporalDecay.Linear(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void linear_rejectsZero() {
        assertThatThrownBy(() -> new TemporalDecay.Linear(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linear_rejectsNegative() {
        assertThatThrownBy(() -> new TemporalDecay.Linear(Duration.ofDays(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linear_nullStoredAt_returnsOne() {
        var decay = new TemporalDecay.Linear(Duration.ofDays(30));
        assertThat(decay.factor(null, Instant.now())).isEqualTo(1.0);
    }

    @Test
    void linear_zeroAge_returnsOne() {
        var     decay = new TemporalDecay.Linear(Duration.ofDays(30));
        Instant now   = Instant.now();
        assertThat(decay.factor(now, now)).isEqualTo(1.0);
    }

    @Test
    void linear_halfwayToZero() {
        var     decay          = new TemporalDecay.Linear(Duration.ofDays(30));
        Instant now            = Instant.now();
        Instant fifteenDaysAgo = now.minus(Duration.ofDays(15));
        assertThat(decay.factor(fifteenDaysAgo, now)).isCloseTo(0.5, within(0.001));
    }

    @Test
    void linear_atZeroAt_returnsZero() {
        var     decay         = new TemporalDecay.Linear(Duration.ofDays(30));
        Instant now           = Instant.now();
        Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));
        assertThat(decay.factor(thirtyDaysAgo, now)).isCloseTo(0.0, within(0.001));
    }

    @Test
    void linear_beyondZeroAt_returnsZero() {
        var     decay        = new TemporalDecay.Linear(Duration.ofDays(30));
        Instant now          = Instant.now();
        Instant sixtyDaysAgo = now.minus(Duration.ofDays(60));
        assertThat(decay.factor(sixtyDaysAgo, now)).isEqualTo(0.0);
    }

    @Test
    void linear_futureStoredAt_returnsOne() {
        var     decay = new TemporalDecay.Linear(Duration.ofDays(30));
        Instant now   = Instant.now();
        assertThat(decay.factor(now.plus(Duration.ofHours(1)), now)).isEqualTo(1.0);
    }

// --- Step ---

    @Test
    void step_rejectsNullCutoff() {
        assertThatThrownBy(() -> new TemporalDecay.Step(null, 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void step_rejectsZeroCutoff() {
        assertThatThrownBy(() -> new TemporalDecay.Step(Duration.ZERO, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step_rejectsNegativeAfterCutoff() {
        assertThatThrownBy(() -> new TemporalDecay.Step(Duration.ofDays(1), -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step_rejectsAfterCutoffAboveOne() {
        assertThatThrownBy(() -> new TemporalDecay.Step(Duration.ofDays(1), 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step_nullStoredAt_returnsOne() {
        var decay = new TemporalDecay.Step(Duration.ofDays(7), 0.3);
        assertThat(decay.factor(null, Instant.now())).isEqualTo(1.0);
    }

    @Test
    void step_beforeCutoff_returnsOne() {
        var     decay        = new TemporalDecay.Step(Duration.ofDays(7), 0.3);
        Instant now          = Instant.now();
        Instant threeDaysAgo = now.minus(Duration.ofDays(3));
        assertThat(decay.factor(threeDaysAgo, now)).isEqualTo(1.0);
    }

    @Test
    void step_afterCutoff_returnsAfterCutoff() {
        var     decay      = new TemporalDecay.Step(Duration.ofDays(7), 0.3);
        Instant now        = Instant.now();
        Instant tenDaysAgo = now.minus(Duration.ofDays(10));
        assertThat(decay.factor(tenDaysAgo, now)).isEqualTo(0.3);
    }

    @Test
    void step_futureStoredAt_returnsOne() {
        var     decay = new TemporalDecay.Step(Duration.ofDays(7), 0.3);
        Instant now   = Instant.now();
        assertThat(decay.factor(now.plus(Duration.ofHours(1)), now)).isEqualTo(1.0);
    }

    @Test
    void step_afterCutoffZero_returnsZero() {
        var     decay      = new TemporalDecay.Step(Duration.ofDays(7), 0.0);
        Instant now        = Instant.now();
        Instant tenDaysAgo = now.minus(Duration.ofDays(10));
        assertThat(decay.factor(tenDaysAgo, now)).isEqualTo(0.0);
    }

    @Test
    void step_afterCutoffOne_returnsOne() {
        var     decay      = new TemporalDecay.Step(Duration.ofDays(7), 1.0);
        Instant now        = Instant.now();
        Instant tenDaysAgo = now.minus(Duration.ofDays(10));
        assertThat(decay.factor(tenDaysAgo, now)).isEqualTo(1.0);
    }
}
