package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


class SearchBudgetConfigTest {

    @Test
    void defaults_returns_valid_config() {
        var config = SearchBudgetConfig.defaults();
        assertEquals(6.0, config.breadthSteepness());
        assertEquals(0.5, config.breadthMidpoint());
        assertEquals(6.0, config.lateralitySteepness());
        assertEquals(0.5, config.lateralityMidpoint());
        assertEquals(0.7, config.lateralityThreatFloor());
        assertEquals(3.0, config.depthSteepness());
        assertEquals(0.5, config.depthMidpoint());
        assertEquals(6.0, config.convergenceSteepness());
        assertEquals(0.5, config.convergenceMidpoint());
        assertEquals(1.4, config.pathologicalThreshold());
    }

    @Test
    void defaults_include_extreme_fields() {
        var config = SearchBudgetConfig.defaults();
        assertEquals(0.85, config.extremePeak());
        assertNull(config.extremeInteraction());
        assertNull(config.fatigue());
    }

    @Test
    void extremePeak_must_be_unit() {
        assertThrows(IllegalArgumentException.class,
                     () -> SearchBudgetConfig.defaults().withExtremePeak(1.5));
    }

    @Test
    void extremePeak_negative_rejected() {
        assertThrows(IllegalArgumentException.class,
                     () -> SearchBudgetConfig.defaults().withExtremePeak(-0.1));
    }

    @Test
    void withExtremeInteraction_returns_copy() {
        var config = SearchBudgetConfig.defaults()
                                       .withExtremeInteraction(ExtremeInteractionConfig.defaults());
        assertNotNull(config.extremeInteraction());
        assertEquals(0.85, config.extremePeak());
    }

    @Test
    void withFatigue_returns_copy() {
        var config = SearchBudgetConfig.defaults()
                                       .withFatigue(FatigueConfig.defaults());
        assertNotNull(config.fatigue());
        assertEquals(0.85, config.extremePeak());
    }


    @Test
    void rejects_negative_steepness() {
        assertThrows(IllegalArgumentException.class, () ->
                new SearchBudgetConfig(-1.0, 0.5, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.5, 1.4, null, 0.85, null, null));
    }

    @Test
    void rejects_midpoint_outside_unit_interval() {
        assertThrows(IllegalArgumentException.class, () ->
                new SearchBudgetConfig(6.0, 1.5, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.5, 1.4, null, 0.85, null, null));
    }

    @Test
    void rejects_negative_pathological_threshold() {
        assertThrows(IllegalArgumentException.class, () ->
                new SearchBudgetConfig(6.0, 0.5, 6.0, 0.5, 0.7, 3.0, 0.5, 6.0, 0.5, -0.1, null, 0.85, null, null));
    }
}
