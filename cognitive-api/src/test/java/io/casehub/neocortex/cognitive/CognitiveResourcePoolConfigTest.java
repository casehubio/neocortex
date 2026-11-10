package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveResourcePoolConfigTest {

    @Test
    void defaults_produces_valid_config() {
        var config = CognitiveResourcePoolConfig.defaults();
        assertEquals(10, config.maxEffort());
        assertEquals(0.5, config.recoveryRate());
    }

    @Test
    void maxEffort_must_be_positive() {
        assertThrows(IllegalArgumentException.class,
                () -> CognitiveResourcePoolConfig.defaults().withMaxEffort(0));
    }

    @Test
    void negative_recoveryRate_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> CognitiveResourcePoolConfig.defaults().withRecoveryRate(-0.1));
    }

    @Test
    void zero_recoveryRate_allowed() {
        var config = CognitiveResourcePoolConfig.defaults().withRecoveryRate(0.0);
        assertEquals(0.0, config.recoveryRate());
    }

    @Test
    void withMaxEffort_returns_copy() {
        var config = CognitiveResourcePoolConfig.defaults().withMaxEffort(20);
        assertEquals(20, config.maxEffort());
        assertEquals(0.5, config.recoveryRate());
    }
}
