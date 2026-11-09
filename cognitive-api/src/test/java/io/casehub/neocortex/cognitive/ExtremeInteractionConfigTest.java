package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    void defaults_have_null_peaks() {
        var config = ExtremeInteractionConfig.defaults();
        assertNull(config.depthPeak());
        assertNull(config.breadthPeak());
        assertNull(config.lateralityPeak());
        assertNull(config.convergencePeak());
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

    @Test
    void peak_or_default_returns_peak_when_set() {
        var config = ExtremeInteractionConfig.defaults().withDepthPeak(0.7);
        assertEquals(0.7, config.depthPeakOrDefault(0.85), 1e-10);
    }

    @Test
    void peak_or_default_returns_fallback_when_null() {
        var config = ExtremeInteractionConfig.defaults();
        assertEquals(0.85, config.depthPeakOrDefault(0.85), 1e-10);
        assertEquals(0.85, config.breadthPeakOrDefault(0.85), 1e-10);
        assertEquals(0.85, config.lateralityPeakOrDefault(0.85), 1e-10);
        assertEquals(0.85, config.convergencePeakOrDefault(0.85), 1e-10);
    }

    @Test
    void peak_out_of_range_rejected() {
        assertThrows(IllegalArgumentException.class,
                     () -> ExtremeInteractionConfig.defaults().withDepthPeak(1.5));
        assertThrows(IllegalArgumentException.class,
                     () -> ExtremeInteractionConfig.defaults().withBreadthPeak(-0.1));
    }

    @Test
    void withDepthPeak_preserves_other_fields() {
        var config = ExtremeInteractionConfig.defaults().withDepthPeak(0.7);
        assertEquals(0.7, config.depthPeak());
        assertNull(config.breadthPeak());
        assertEquals(1.5, config.breadthDecay());
        assertEquals(-1.0, config.breadthAmplification());
    }

    @Test
    void withBreadthPeak_preserves_other_fields() {
        var config = ExtremeInteractionConfig.defaults().withBreadthPeak(0.8);
        assertEquals(0.8, config.breadthPeak());
        assertNull(config.depthPeak());
    }

    @Test
    void withLateralityPeak_preserves_other_fields() {
        var config = ExtremeInteractionConfig.defaults().withLateralityPeak(0.6);
        assertEquals(0.6, config.lateralityPeak());
        assertNull(config.convergencePeak());
    }

    @Test
    void withConvergencePeak_preserves_other_fields() {
        var config = ExtremeInteractionConfig.defaults().withConvergencePeak(0.9);
        assertEquals(0.9, config.convergencePeak());
        assertNull(config.lateralityPeak());
    }

}
