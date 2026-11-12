package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchFatigueEffectsTest {

    @Test
    void defaults_produces_valid_config() {
        var config = SearchFatigueEffects.defaults();
        assertEquals(-0.1, config.depthEffect());
        assertEquals(-0.2, config.breadthEffect());
        assertEquals(-0.15, config.lateralityEffect());
        assertEquals(0.1, config.convergenceEffect());
    }

    @Test
    void effect_below_minus_one_rejected() {
        assertThrows(IllegalArgumentException.class,
                     () -> SearchFatigueEffects.defaults().withDepthEffect(-1.5));
    }

    @Test
    void effect_above_one_rejected() {
        assertThrows(IllegalArgumentException.class,
                     () -> SearchFatigueEffects.defaults().withConvergenceEffect(1.5));
    }

    @Test
    void effect_at_boundaries_allowed() {
        var config = SearchFatigueEffects.defaults()
                                         .withDepthEffect(-1.0)
                                         .withConvergenceEffect(1.0);
        assertEquals(-1.0, config.depthEffect());
        assertEquals(1.0, config.convergenceEffect());
    }

    @Test
    void withBreadthEffect_returns_copy() {
        var config = SearchFatigueEffects.defaults().withBreadthEffect(-0.5);
        assertEquals(-0.5, config.breadthEffect());
        assertEquals(-0.1, config.depthEffect());
    }

    @Test
    void withLateralityEffect_returns_copy() {
        var config = SearchFatigueEffects.defaults().withLateralityEffect(0.3);
        assertEquals(0.3, config.lateralityEffect());
        assertEquals(-0.2, config.breadthEffect());
    }
}
