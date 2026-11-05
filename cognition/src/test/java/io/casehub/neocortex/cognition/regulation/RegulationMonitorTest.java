package io.casehub.neocortex.cognition.regulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegulationMonitorTest {

    private static final String AGENT = "a1";
    private static final String TENANT = "t1";

    private RegulationMonitor monitor;

    @BeforeEach
    void setUp() {
        monitor = new RegulationMonitor();
    }

    @Test
    void noSignalOnFirstResult() {
        var result = regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.7);
        var signal = monitor.record(AGENT, TENANT, result);
        assertThat(signal).isNull();
    }

    @Test
    void noSignalWhenEffectivenessImproves() {
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.5));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.6));
        var signal = monitor.record(AGENT, TENANT,
                regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.7));
        assertThat(signal).isNull();
    }

    @Test
    void signalAfterThreeConsecutiveDeclines() {
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.7));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.5));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.4));
        var signal = monitor.record(AGENT, TENANT,
                regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.3));

        assertThat(signal).isNotNull();
        assertThat(signal.failingStrategy()).isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION);
        assertThat(signal.consecutiveDeclines()).isEqualTo(3);
        assertThat(signal.initialEffectiveness()).isEqualTo(0.7);
        assertThat(signal.currentEffectiveness()).isEqualTo(0.3);
        assertThat(signal.breakthrough()).isFalse();
    }

    @Test
    void declineCountResetsOnImprovement() {
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.7));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.5));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.6));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.5));
        var signal = monitor.record(AGENT, TENANT,
                regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.4));

        assertThat(signal).isNull();
    }

    @Test
    void declineCountResetsOnStrategyChange() {
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.7));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.5));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.3));
        var signal = monitor.record(AGENT, TENANT,
                regulation(EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.6));

        assertThat(signal).isNull();
    }

    @Test
    void breakthroughBelowFloor() {
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.5));
        var signal = monitor.record(AGENT, TENANT,
                regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.05));

        assertThat(signal).isNotNull();
        assertThat(signal.breakthrough()).isTrue();
        assertThat(signal.currentEffectiveness()).isEqualTo(0.05);
    }

    @Test
    void independentPerAgent() {
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.7));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.5));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.3));

        var signal = monitor.record("other", TENANT,
                regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.3));

        assertThat(signal).isNull();
    }

    @Test
    void resetClearsState() {
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.7));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.5));
        monitor.reset(AGENT, TENANT);

        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.7));
        monitor.record(AGENT, TENANT, regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.5));
        var signal = monitor.record(AGENT, TENANT,
                regulation(EmotionRegulationStrategy.RESPONSE_MODULATION, 0.3));

        assertThat(signal).isNull();
    }

    private static EmotionRegulationResult regulation(EmotionRegulationStrategy strategy,
                                                       double effectiveness) {
        return new EmotionRegulationResult(strategy, List.of("ANGER"), List.of(),
                0.7, 0.7 * (1 - effectiveness), effectiveness,
                "test narrative", null, null);
    }
}
