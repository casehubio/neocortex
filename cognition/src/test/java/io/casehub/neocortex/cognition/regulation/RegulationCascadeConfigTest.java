package io.casehub.neocortex.cognition.regulation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegulationCascadeConfigTest {

    @Test
    void defaultsHaveExpectedValues() {
        var config = RegulationCascadeConfig.defaults();
        assertEquals(0.3, config.regulationConfidenceThreshold(), 0.001);
        assertEquals(0.15, config.regulationDominanceMargin(), 0.001);
    }

    @Test
    void rejectsNegativeThreshold() {
        assertThrows(IllegalArgumentException.class,
            () -> new RegulationCascadeConfig(-0.1, 0.15));
    }

    @Test
    void rejectsNegativeMargin() {
        assertThrows(IllegalArgumentException.class,
            () -> new RegulationCascadeConfig(0.3, -0.1));
    }
}
