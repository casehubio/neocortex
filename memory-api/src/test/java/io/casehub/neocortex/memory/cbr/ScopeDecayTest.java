package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class ScopeDecayTest {

    @Test
    void exponential_exactMatch_returnsOne() {
        var decay = new ScopeDecay.Exponential(0.5);
        assertThat(decay.factor(0)).isEqualTo(1.0);
    }

    @Test
    void exponential_parent_returnsBase() {
        var decay = new ScopeDecay.Exponential(0.5);
        assertThat(decay.factor(1)).isEqualTo(0.5);
    }

    @Test
    void exponential_grandparent_returnsBaseSquared() {
        var decay = new ScopeDecay.Exponential(0.5);
        assertThat(decay.factor(2)).isEqualTo(0.25);
    }

    @Test
    void exponential_baseOne_noDecay() {
        var decay = new ScopeDecay.Exponential(1.0);
        assertThat(decay.factor(5)).isEqualTo(1.0);
    }

    @Test
    void exponential_invalidBase_zero() {
        assertThatThrownBy(() -> new ScopeDecay.Exponential(0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exponential_invalidBase_negative() {
        assertThatThrownBy(() -> new ScopeDecay.Exponential(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void exponential_invalidBase_aboveOne() {
        assertThatThrownBy(() -> new ScopeDecay.Exponential(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linear_exactMatch_returnsOne() {
        var decay = new ScopeDecay.Linear(3);
        assertThat(decay.factor(0)).isEqualTo(1.0);
    }

    @Test
    void linear_parent_decaysLinearly() {
        var decay = new ScopeDecay.Linear(3);
        assertThat(decay.factor(1)).isCloseTo(0.6667, offset(0.001));
    }

    @Test
    void linear_atMaxDepth_returnsZero() {
        var decay = new ScopeDecay.Linear(3);
        assertThat(decay.factor(3)).isEqualTo(0.0);
    }

    @Test
    void linear_beyondMaxDepth_clampedToZero() {
        var decay = new ScopeDecay.Linear(3);
        assertThat(decay.factor(5)).isEqualTo(0.0);
    }

    @Test
    void linear_invalidMaxDepth_zero() {
        assertThatThrownBy(() -> new ScopeDecay.Linear(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step_exactMatch_returnsOne() {
        var decay = new ScopeDecay.Step(0.3);
        assertThat(decay.factor(0)).isEqualTo(1.0);
    }

    @Test
    void step_anyAncestor_returnsBeyondExact() {
        var decay = new ScopeDecay.Step(0.3);
        assertThat(decay.factor(1)).isEqualTo(0.3);
        assertThat(decay.factor(5)).isEqualTo(0.3);
    }

    @Test
    void step_invalidBeyondExact_negative() {
        assertThatThrownBy(() -> new ScopeDecay.Step(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step_invalidBeyondExact_aboveOne() {
        assertThatThrownBy(() -> new ScopeDecay.Step(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void step_boundaryValues_zeroAndOne() {
        assertThatCode(() -> new ScopeDecay.Step(0.0)).doesNotThrowAnyException();
        assertThatCode(() -> new ScopeDecay.Step(1.0)).doesNotThrowAnyException();
    }
}
