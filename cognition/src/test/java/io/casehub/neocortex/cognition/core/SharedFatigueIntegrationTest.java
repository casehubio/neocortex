package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognitive.CognitiveResourcePoolConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SharedFatigueIntegrationTest {

    private DefaultCognitiveResourcePool createPool() {
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
        return new DefaultCognitiveResourcePool(registry);
    }

    @Test
    void regulation_effort_increases_shared_depletion() {
        var pool = createPool();
        for (int i = 0; i < 5; i++) {
            pool.recordEffort("a1", "t1", 0.5 * 0.15);
        }
        assertTrue(pool.currentDepletion("a1", "t1") > 0.0,
                "Regulation effort should increase shared depletion");
    }

    @Test
    void search_intensity_degrades_regulation_effectiveness() {
        var pool = createPool();
        for (int i = 0; i < 5; i++) {
            pool.recordEffort("a1", "t1", 1.0);
        }
        double depletion = pool.currentDepletion("a1", "t1");
        double effectiveAlpha = 0.5 * (1.0 - depletion);
        assertTrue(effectiveAlpha < 0.5,
                "Search-driven depletion should reduce regulation alpha: " + effectiveAlpha);
    }

    @Test
    void dual_source_depletes_faster_than_single() {
        var poolSingle = createPool();
        var poolDual = createPool();

        for (int i = 0; i < 3; i++) {
            poolSingle.recordEffort("a1", "t1", 1.0);
            poolDual.recordEffort("a1", "t1", 1.0);
            poolDual.recordEffort("a1", "t1", 0.5 * 0.15);
        }
        assertTrue(poolDual.currentDepletion("a1", "t1")
                > poolSingle.currentDepletion("a1", "t1"),
                "Dual-source depletion should exceed single-source");
    }

    @Test
    void recovery_when_both_sources_idle() {
        var pool = createPool();
        pool.recordEffort("a1", "t1", 3.0);
        double peak = pool.currentDepletion("a1", "t1");
        pool.recordRecovery("a1", "t1", 0.5);
        pool.recordRecovery("a1", "t1", 0.5);
        assertTrue(pool.currentDepletion("a1", "t1") < peak,
                "Recovery from both sources should reduce depletion");
    }

    @Test
    void sustained_effort_prevents_recovery() {
        var pool = createPool();
        pool.recordEffort("a1", "t1", 3.0);
        double afterEffort = pool.currentDepletion("a1", "t1");
        pool.recordEffort("a1", "t1", 1.0);
        pool.recordRecovery("a1", "t1", 0.3);
        assertTrue(pool.currentDepletion("a1", "t1") >= afterEffort,
                "Active effort should prevent net recovery");
    }
}
