package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FatigueConfigTest {

    @Test
    void defaults_produces_valid_config() {
        var config = FatigueConfig.defaults();
        assertEquals(10, config.maxTicks());
        assertEquals(0.5, config.recoveryRate());
        assertEquals(-0.1, config.depthEffect());
        assertEquals(-0.2, config.breadthEffect());
        assertEquals(-0.15, config.lateralityEffect());
        assertEquals(0.1, config.convergenceEffect());
    }

    @Test
    void maxTicks_must_be_positive() {
        assertThrows(IllegalArgumentException.class,
                () -> FatigueConfig.defaults().withMaxTicks(0));
    }

    @Test
    void negative_maxTicks_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FatigueConfig.defaults().withMaxTicks(-5));
    }

    @Test
    void negative_recoveryRate_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FatigueConfig.defaults().withRecoveryRate(-0.1));
    }

    @Test
    void zero_recoveryRate_allowed() {
        var config = FatigueConfig.defaults().withRecoveryRate(0.0);
        assertEquals(0.0, config.recoveryRate());
    }

    @Test
    void effect_below_minus_one_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FatigueConfig.defaults().withDepthEffect(-1.5));
    }

    @Test
    void effect_above_one_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> FatigueConfig.defaults().withConvergenceEffect(1.5));
    }

    @Test
    void effect_at_boundaries_allowed() {
        var config = FatigueConfig.defaults()
                .withDepthEffect(-1.0)
                .withConvergenceEffect(1.0);
        assertEquals(-1.0, config.depthEffect());
        assertEquals(1.0, config.convergenceEffect());
    }

    @Test
    void withMaxTicks_returns_copy_preserving_other_fields() {
        var config = FatigueConfig.defaults().withMaxTicks(20);
        assertEquals(20, config.maxTicks());
        assertEquals(0.5, config.recoveryRate());
        assertEquals(-0.1, config.depthEffect());
    }

    @Test
    void withBreadthEffect_returns_copy() {
        var config = FatigueConfig.defaults().withBreadthEffect(-0.5);
        assertEquals(-0.5, config.breadthEffect());
        assertEquals(10, config.maxTicks());
    }

    @Test
    void withLateralityEffect_returns_copy() {
        var config = FatigueConfig.defaults().withLateralityEffect(0.3);
        assertEquals(0.3, config.lateralityEffect());
    }
}
