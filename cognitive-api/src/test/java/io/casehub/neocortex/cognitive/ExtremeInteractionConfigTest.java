package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExtremeInteractionConfigTest {

    @Test
    void defaults_produces_valid_config() {
        var config = ExtremeInteractionConfig.defaults();
        assertEquals(0.0, config.depthDecay());
        assertEquals(1.5, config.breadthDecay());
        assertEquals(2.0, config.lateralityDecay());
        assertEquals(0.5, config.convergenceDecay());
        assertEquals(0.5, config.depthAmplification());
        assertEquals(-1.0, config.breadthAmplification());
        assertEquals(-0.8, config.lateralityAmplification());
        assertEquals(-1.5, config.convergenceAmplification());
    }

    @Test
    void negative_depth_decay_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ExtremeInteractionConfig.defaults().withDepthDecay(-1.0));
    }

    @Test
    void negative_breadth_decay_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ExtremeInteractionConfig.defaults().withBreadthDecay(-0.1));
    }

    @Test
    void negative_laterality_decay_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ExtremeInteractionConfig.defaults().withLateralityDecay(-0.5));
    }

    @Test
    void negative_convergence_decay_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ExtremeInteractionConfig.defaults().withConvergenceDecay(-2.0));
    }

    @Test
    void zero_decay_allowed() {
        var config = ExtremeInteractionConfig.defaults().withDepthDecay(0.0);
        assertEquals(0.0, config.depthDecay());
    }

    @Test
    void withDepthDecay_returns_copy_preserving_other_fields() {
        var config = ExtremeInteractionConfig.defaults().withDepthDecay(3.0);
        assertEquals(3.0, config.depthDecay());
        assertEquals(1.5, config.breadthDecay());
        assertEquals(-1.0, config.breadthAmplification());
    }

    @Test
    void withDepthAmplification_allows_negative() {
        var config = ExtremeInteractionConfig.defaults().withDepthAmplification(-2.0);
        assertEquals(-2.0, config.depthAmplification());
    }

    @Test
    void withConvergenceAmplification_allows_positive() {
        var config = ExtremeInteractionConfig.defaults().withConvergenceAmplification(1.0);
        assertEquals(1.0, config.convergenceAmplification());
    }
}
