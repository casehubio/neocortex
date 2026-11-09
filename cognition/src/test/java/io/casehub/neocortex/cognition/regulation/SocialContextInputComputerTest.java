package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.prompt.observation.RegulationTracker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SocialContextInputComputerTest {

    @Test
    void socialIsolationFullyIsolatedWhenNoAgents() {
        var ctx = new RegulationContext("library", null, null);
        var result = SocialContextInputComputer.computeIsolation(ctx);
        assertThat(result).isCloseTo(1.0, within(0.01));
    }

    @Test
    void socialIsolationFullyIsolatedWhenEmptyAgents() {
        var ctx = new RegulationContext("library", List.of(), null);
        var result = SocialContextInputComputer.computeIsolation(ctx);
        assertThat(result).isCloseTo(1.0, within(0.01));
    }

    @Test
    void socialIsolationGradientWithAgentCount() {
        assertThat(SocialContextInputComputer.computeIsolation(
            new RegulationContext("lib", List.of("a1"), null)))
            .isCloseTo(0.667, within(0.01));
        assertThat(SocialContextInputComputer.computeIsolation(
            new RegulationContext("lib", List.of("a1", "a2"), null)))
            .isCloseTo(0.333, within(0.01));
        assertThat(SocialContextInputComputer.computeIsolation(
            new RegulationContext("lib", List.of("a1", "a2", "a3"), null)))
            .isCloseTo(0.0, within(0.01));
    }

    @Test
    void socialIsolationSaturatesAtThreeAgents() {
        assertThat(SocialContextInputComputer.computeIsolation(
            new RegulationContext("lib", List.of("a1", "a2", "a3", "a4", "a5"), null)))
            .isCloseTo(0.0, within(0.01));
    }

    @Test
    void perceivedResponsivenessWithTrustAndImproving() {
        double result = SocialContextInputComputer.computeResponsiveness(
            0.8, RegulationTracker.Trajectory.IMPROVING);
        // clamp01(0.6 * 0.8 + 0.4 * 1.0) = clamp01(0.48 + 0.4) = 0.88
        assertThat(result).isCloseTo(0.88, within(0.01));
    }

    @Test
    void perceivedResponsivenessWithLowTrustAndWorsening() {
        double result = SocialContextInputComputer.computeResponsiveness(
            0.2, RegulationTracker.Trajectory.WORSENING);
        // clamp01(0.6 * 0.2 + 0.4 * 0.0) = clamp01(0.12) = 0.12
        assertThat(result).isCloseTo(0.12, within(0.01));
    }

    @Test
    void perceivedResponsivenessStableTrajectory() {
        double result = SocialContextInputComputer.computeResponsiveness(
            0.5, RegulationTracker.Trajectory.STABLE);
        // clamp01(0.6 * 0.5 + 0.4 * 0.5) = clamp01(0.3 + 0.2) = 0.5
        assertThat(result).isCloseTo(0.5, within(0.01));
    }

    @Test
    void perceivedResponsivenessNullTrajectoryDefaultsToStable() {
        double result = SocialContextInputComputer.computeResponsiveness(0.5, null);
        assertThat(result).isCloseTo(0.5, within(0.01));
    }

    @Test
    void targetDistressFromPAD() {
        // pleasure=-0.8, arousal=0.9 → clamp01((-(-0.8) + 0.9) / 2.0) = clamp01(1.7/2) = 0.85
        double result = SocialContextInputComputer.computeDistress(-0.8, 0.9);
        assertThat(result).isCloseTo(0.85, within(0.01));
    }

    @Test
    void targetDistressCalm() {
        // pleasure=0.7, arousal=0.2 → clamp01((-0.7 + 0.2) / 2.0) = clamp01(-0.25) = 0.0
        double result = SocialContextInputComputer.computeDistress(0.7, 0.2);
        assertThat(result).isCloseTo(0.0, within(0.01));
    }

    @Test
    void targetDistressClampsToOne() {
        double result = SocialContextInputComputer.computeDistress(-1.0, 1.0);
        assertThat(result).isCloseTo(1.0, within(0.01));
    }

    @Test
    void powerAsymmetryWithDominance() {
        // dominance=0.8, submission=0.2, bias=0.0 → clamp01((0.8 - 0.2 + 0.0) / 2.0) = 0.3
        double result = SocialContextInputComputer.computePowerAsymmetry(0.8, 0.2, 0.0);
        assertThat(result).isCloseTo(0.3, within(0.01));
    }

    @Test
    void powerAsymmetryWithBias() {
        // dominance=0.5, submission=0.5, bias=0.4 → clamp01((0.5 - 0.5 + 0.4) / 2.0) = 0.2
        double result = SocialContextInputComputer.computePowerAsymmetry(0.5, 0.5, 0.4);
        assertThat(result).isCloseTo(0.2, within(0.01));
    }

    @Test
    void powerAsymmetryClampsToZero() {
        double result = SocialContextInputComputer.computePowerAsymmetry(0.1, 0.9, 0.0);
        assertThat(result).isCloseTo(0.0, within(0.01));
    }

    @Test
    void allZerosWhenNullContext() {
        var ctx = new RegulationContext(null, null, null);
        var inputs = SocialContextInputComputer.computeFromRawInputs(
            ctx, 0.0, null, 0.0, 0.0, 0.0, 0.0, 0.0);
        assertThat(inputs.get("perceived_responsiveness")).isCloseTo(0.2, within(0.01));
        assertThat(inputs.get("target_distress")).isCloseTo(0.0, within(0.01));
        assertThat(inputs.get("social_isolation")).isCloseTo(1.0, within(0.01));
        assertThat(inputs.get("power_asymmetry")).isCloseTo(0.0, within(0.01));
    }
}
