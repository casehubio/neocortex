package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SearchBudgetExtremeInteractionTest {

    @Test
    void bold_and_anxious_worrier_produce_different_extreme_configs() {
        var bold = CognitiveDerivationEngine.deriveSearchBudgetDefaults(boldFacets(), boldAxes(), 0.8);
        var anxious = CognitiveDerivationEngine.deriveSearchBudgetDefaults(anxiousWorrierFacets(), anxiousWorrierAxes(), 0.3);

        assertNotNull(bold.extremeInteraction());
        assertNotNull(anxious.extremeInteraction());

        assertTrue(bold.extremePeak() > anxious.extremePeak(),
                "Bold should have higher peak: bold=" + bold.extremePeak() + " anxious=" + anxious.extremePeak());

        assertTrue(anxious.extremeInteraction().breadthDecay() > bold.extremeInteraction().breadthDecay(),
                "Anxious should have higher breadth decay: anxious=" + anxious.extremeInteraction().breadthDecay() + " bold=" + bold.extremeInteraction().breadthDecay());

        assertTrue(anxious.extremeInteraction().depthDecay() >= bold.extremeInteraction().depthDecay(),
                "Anxious should have >= depth decay: anxious=" + anxious.extremeInteraction().depthDecay() + " bold=" + bold.extremeInteraction().depthDecay());

        assertTrue(bold.extremeInteraction().depthAmplification() > 0,
                "Bold depth amplification should be positive: " + bold.extremeInteraction().depthAmplification());
        assertTrue(anxious.extremeInteraction().depthAmplification() < 0,
                "Anxious depth amplification should be negative: " + anxious.extremeInteraction().depthAmplification());

        assertTrue(bold.fatigue().depthEffect() < 0, "Bold fatigue depth should be negative (low-C): " + bold.fatigue().depthEffect());
        assertTrue(anxious.fatigue().depthEffect() < 0, "Anxious fatigue depth should be negative (low-C): " + anxious.fatigue().depthEffect());

        assertTrue(bold.fatigue().convergenceEffect() > 0,
                "Bold convergence effect should be positive (satisficing): " + bold.fatigue().convergenceEffect());
        assertTrue(anxious.fatigue().convergenceEffect() < 0,
                "Anxious convergence effect should be negative (blocked): " + anxious.fatigue().convergenceEffect());
    }

    @Test
    void conscientiousness_protects_depth_in_extreme_config() {
        var disciplined = CognitiveDerivationEngine.deriveSearchBudgetDefaults(disciplinedFacets(), disciplinedAxes(), 0.3);
        assertNotNull(disciplined.extremeInteraction());
        assertNotNull(disciplined.fatigue());

        assertEquals(0.0, disciplined.extremeInteraction().depthDecay(),
                "High-C should protect depth (decay=0)");

        assertTrue(disciplined.fatigue().depthEffect() > -0.02,
                "High-C fatigue depth effect should be near 0 (protected): " + disciplined.fatigue().depthEffect());

        assertTrue(disciplined.fatigue().convergenceEffect() > 0,
                "High-C convergence effect should be positive (satisficing): " + disciplined.fatigue().convergenceEffect());
    }

    @Test
    void inverted_u_activates_past_personality_peak() {
        var anxious = CognitiveDerivationEngine.deriveSearchBudgetDefaults(anxiousWorrierFacets(), anxiousWorrierAxes(), 0.3);
        double peak = anxious.extremePeak();
        double decay = anxious.extremeInteraction().breadthDecay();

        double belowPeak = activationWithDecay(peak - 0.1, 6.0, 0.5, decay, peak);
        double atPeak = activationWithDecay(peak, 6.0, 0.5, decay, peak);
        double abovePeak = activationWithDecay(peak + 0.1, 6.0, 0.5, decay, peak);

        assertTrue(belowPeak <= atPeak, "Below peak should not exceed peak activation: below=" + belowPeak + " at=" + atPeak);
        assertTrue(abovePeak < atPeak, "Above peak should be less than peak activation: above=" + abovePeak + " at=" + atPeak);
    }

    @Test
    void fatigue_recovery_differentiates_by_personality() {
        var bold = CognitiveDerivationEngine.deriveSearchBudgetDefaults(boldFacets(), boldAxes(), 0.8);
        var disciplined = CognitiveDerivationEngine.deriveSearchBudgetDefaults(disciplinedFacets(), disciplinedAxes(), 0.3);

        assertTrue(disciplined.fatigue().recoveryRate() > bold.fatigue().recoveryRate(),
                "High-C should recover faster: disciplined=" + disciplined.fatigue().recoveryRate() + " bold=" + bold.fatigue().recoveryRate());

        assertTrue(disciplined.fatigue().maxTicks() > bold.fatigue().maxTicks(),
                "High-C should sustain longer: disciplined=" + disciplined.fatigue().maxTicks() + " bold=" + bold.fatigue().maxTicks());
    }

    @Test
    void extreme_peak_range_is_valid() {
        var bold = CognitiveDerivationEngine.deriveSearchBudgetDefaults(boldFacets(), boldAxes(), 0.8);
        var anxious = CognitiveDerivationEngine.deriveSearchBudgetDefaults(anxiousWorrierFacets(), anxiousWorrierAxes(), 0.3);

        assertTrue(bold.extremePeak() >= 0.5 && bold.extremePeak() <= 0.95,
                "Bold peak in valid range: " + bold.extremePeak());
        assertTrue(anxious.extremePeak() >= 0.5 && anxious.extremePeak() <= 0.95,
                "Anxious peak in valid range: " + anxious.extremePeak());
    }

    // --- personality profile factories ---

    private static List<PersonalityFacet> boldFacets() {
        return List.of(
                new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.9, "d"),
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.7, "d"),
                new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.EMOTIONAL_LABILITY, 0.2, "d"));
    }

    private static DispositionAxes boldAxes() {
        return new DispositionAxes("cooperative", "flexible", "bold", "independent", "collaborative");
    }

    private static List<PersonalityFacet> anxiousWorrierFacets() {
        return List.of(
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.8, "d"),
                new PersonalityFacet(PersonalityFacet.EMOTIONAL_LABILITY, 0.7, "d"),
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.7, "d"),
                new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.7, "d"),
                new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.6, "d"),
                new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.7, "d"));
    }

    private static DispositionAxes anxiousWorrierAxes() {
        return new DispositionAxes("cooperative", "flexible", "conservative", "dependent", "avoidant");
    }

    private static List<PersonalityFacet> disciplinedFacets() {
        return List.of(
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.8, "d"),
                new PersonalityFacet(PersonalityFacet.EMOTIONAL_LABILITY, 0.7, "d"),
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.1, "d"),
                new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.7, "d"),
                new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.6, "d"),
                new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.7, "d"));
    }

    private static DispositionAxes disciplinedAxes() {
        return new DispositionAxes("cooperative", "strict", "conservative", "dependent", "avoidant");
    }

    private static double activationWithDecay(double x, double k, double midpoint, double decay, double peak) {
        double base = 1.0 / (1.0 + Math.exp(-k * (x - midpoint)));
        double penalty = Math.exp(-decay * Math.max(0.0, x - peak));
        return base * penalty;
    }
}
