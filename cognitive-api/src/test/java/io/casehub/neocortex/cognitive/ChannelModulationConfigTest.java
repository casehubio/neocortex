package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChannelModulationConfigTest {

    @Test
    void defaults_returns_values_in_expected_range() {
        var config = ChannelModulationConfig.defaults();
        assertTrue(config.specificDepthBoost() > 0 && config.specificDepthBoost() <= 0.25);
        assertTrue(config.specificBreadthPenalty() > 0 && config.specificBreadthPenalty() <= 0.25);
        assertTrue(config.diversiveBreadthBoost() > 0 && config.diversiveBreadthBoost() <= 0.25);
        assertTrue(config.diversiveLateralityBoost() > 0 && config.diversiveLateralityBoost() <= 0.25);
        assertTrue(config.diversiveConvergencePenalty() > 0 && config.diversiveConvergencePenalty() <= 0.25);
        assertTrue(config.deprivationConvergenceBoost() > 0 && config.deprivationConvergenceBoost() <= 0.25);
        assertTrue(config.lowComprehensionLateralityBoost() > 0 && config.lowComprehensionLateralityBoost() <= 0.25);
        assertTrue(config.highComprehensionConvergenceBoost() > 0 && config.highComprehensionConvergenceBoost() <= 0.25);
    }
}
