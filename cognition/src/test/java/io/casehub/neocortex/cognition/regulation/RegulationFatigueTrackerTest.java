package io.casehub.neocortex.cognition.regulation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RegulationFatigueTrackerTest {

    private static final String AGENT = "a1";
    private static final String TENANT = "t1";
    private static final double BASELINE_ALPHA = 0.5;

    private RegulationFatigueTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new RegulationFatigueTracker();
    }

    @Test
    void noFatigueInitially() {
        assertThat(tracker.currentFatigue(AGENT, TENANT)).isEqualTo(0.0);
        assertThat(tracker.effectiveAlpha(AGENT, TENANT, BASELINE_ALPHA)).isEqualTo(BASELINE_ALPHA);
        assertThat(tracker.isExhausted(AGENT, TENANT)).isFalse();
    }

    @Test
    void fatigueAccumulatesWithRegulation() {
        tracker.recordRegulation(AGENT, TENANT, 0.5);

        double expected = 0.5 * RegulationFatigueTracker.FATIGUE_RATE;
        assertThat(tracker.currentFatigue(AGENT, TENANT)).isCloseTo(expected, within(0.001));
        assertThat(tracker.effectiveAlpha(AGENT, TENANT, BASELINE_ALPHA))
                .isCloseTo(BASELINE_ALPHA * (1.0 - expected), within(0.001));
    }

    @Test
    void repeatedRegulationDegradeCapacity() {
        double initialAlpha = tracker.effectiveAlpha(AGENT, TENANT, BASELINE_ALPHA);

        for (int i = 0; i < 5; i++) {
            tracker.recordRegulation(AGENT, TENANT, 0.6);
        }

        double degradedAlpha = tracker.effectiveAlpha(AGENT, TENANT, BASELINE_ALPHA);
        assertThat(degradedAlpha).isLessThan(initialAlpha);
        assertThat(degradedAlpha).isGreaterThan(0.0);
    }

    @Test
    void recoveryRestoresCapacity() {
        tracker.recordRegulation(AGENT, TENANT, 0.8);
        double afterRegulation = tracker.effectiveAlpha(AGENT, TENANT, BASELINE_ALPHA);

        tracker.recordRecovery(AGENT, TENANT);
        double afterRecovery = tracker.effectiveAlpha(AGENT, TENANT, BASELINE_ALPHA);

        assertThat(afterRecovery).isGreaterThan(afterRegulation);
    }

    @Test
    void fullRecoveryResetsToBaseline() {
        tracker.recordRegulation(AGENT, TENANT, 0.3);
        tracker.recordRecovery(AGENT, TENANT);
        tracker.recordRecovery(AGENT, TENANT);

        assertThat(tracker.currentFatigue(AGENT, TENANT)).isEqualTo(0.0);
        assertThat(tracker.effectiveAlpha(AGENT, TENANT, BASELINE_ALPHA)).isEqualTo(BASELINE_ALPHA);
    }

    @Test
    void exhaustionAfterSustainedSuppression() {
        for (int i = 0; i < 15; i++) {
            tracker.recordRegulation(AGENT, TENANT, 0.8);
        }

        assertThat(tracker.isExhausted(AGENT, TENANT)).isTrue();
        assertThat(tracker.effectiveAlpha(AGENT, TENANT, BASELINE_ALPHA)).isEqualTo(0.0);
    }

    @Test
    void fatigueClampsAtOne() {
        for (int i = 0; i < 20; i++) {
            tracker.recordRegulation(AGENT, TENANT, 1.0);
        }

        assertThat(tracker.currentFatigue(AGENT, TENANT)).isEqualTo(1.0);
    }

    @Test
    void independentPerAgent() {
        tracker.recordRegulation(AGENT, TENANT, 0.8);

        assertThat(tracker.currentFatigue(AGENT, TENANT)).isGreaterThan(0.0);
        assertThat(tracker.currentFatigue("other", TENANT)).isEqualTo(0.0);
    }
}
