package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognitive.CognitiveResourcePoolConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DefaultCognitiveResourcePoolTest {

    private DefaultCognitiveResourcePool pool;

    @BeforeEach
    void setUp() {
        var config = CognitiveResourcePoolConfig.defaults();
        CognitiveDefaultsRegistry registry = new CognitiveDefaultsRegistry() {
            @Override
            public Optional<CognitiveDefaults> forAgent(String agentId) {
                return Optional.of(CognitiveDefaults.empty(agentId)
                        .withCognitiveResourcePoolConfig(config));
            }
            @Override
            public CognitiveDefaults forAgentOrDefaults(String agentId) {
                return forAgent(agentId).orElseThrow();
            }
        };
        pool = new DefaultCognitiveResourcePool(registry);
    }

    @Test
    void initially_zero_depletion() {
        assertEquals(0.0, pool.currentDepletion("a1", "t1"));
    }

    @Test
    void recordEffort_increases_depletion() {
        pool.recordEffort("a1", "t1", 1.0);
        assertTrue(pool.currentDepletion("a1", "t1") > 0.0);
    }

    @Test
    void depletion_uses_logarithmic_scaling() {
        pool.recordEffort("a1", "t1", 5.0);
        double depletion = pool.currentDepletion("a1", "t1");
        double expected = Math.log(1 + 5.0) / Math.log(1 + 10);
        assertEquals(expected, depletion, 1e-9);
    }

    @Test
    void recordRecovery_decreases_depletion() {
        pool.recordEffort("a1", "t1", 3.0);
        double before = pool.currentDepletion("a1", "t1");
        pool.recordRecovery("a1", "t1", 0.5);
        assertTrue(pool.currentDepletion("a1", "t1") < before);
    }

    @Test
    void recovery_floors_at_zero() {
        pool.recordEffort("a1", "t1", 0.1);
        pool.recordRecovery("a1", "t1", 10.0);
        assertEquals(0.0, pool.currentDepletion("a1", "t1"));
    }

    @Test
    void agents_are_isolated() {
        pool.recordEffort("a1", "t1", 5.0);
        assertEquals(0.0, pool.currentDepletion("a2", "t1"));
    }

    @Test
    void multi_source_effort_accumulates() {
        pool.recordEffort("a1", "t1", 1.0);
        pool.recordEffort("a1", "t1", 1.0);
        double expected = Math.log(1 + 2.0) / Math.log(1 + 10);
        assertEquals(expected, pool.currentDepletion("a1", "t1"), 1e-9);
    }

    @Test
    void net_contribution_effort_exceeds_recovery() {
        pool.recordEffort("a1", "t1", 3.0);
        double afterEffort = pool.currentDepletion("a1", "t1");
        pool.recordEffort("a1", "t1", 1.0);
        pool.recordRecovery("a1", "t1", 0.3);
        assertTrue(pool.currentDepletion("a1", "t1") > afterEffort);
    }

    @Test
    void negative_effort_ignored() {
        pool.recordEffort("a1", "t1", -5.0);
        assertEquals(0.0, pool.currentDepletion("a1", "t1"));
    }

    @Test
    void zero_effort_ignored() {
        pool.recordEffort("a1", "t1", 0.0);
        assertEquals(0.0, pool.currentDepletion("a1", "t1"));
    }
}
