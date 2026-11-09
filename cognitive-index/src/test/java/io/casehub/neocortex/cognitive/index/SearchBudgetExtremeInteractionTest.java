package io.casehub.neocortex.cognitive.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void per_parameter_peaks_are_derived() {
        var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(boldFacets(), boldAxes(), 0.8);
        assertNotNull(config.extremeInteraction().depthPeak());
        assertNotNull(config.extremeInteraction().breadthPeak());
        assertNotNull(config.extremeInteraction().lateralityPeak());
        assertNotNull(config.extremeInteraction().convergencePeak());
    }

    @Test
    void laterality_peaks_earliest_depth_latest() {
        var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(boldFacets(), boldAxes(), 0.8);
        var ext    = config.extremeInteraction();
        assertTrue(ext.lateralityPeak() < ext.depthPeak(),
                   "Laterality (creative association) should peak earlier than depth (complex reasoning): lat=" + ext.lateralityPeak() + " depth=" + ext.depthPeak());
    }

    @Test
    void per_parameter_peaks_in_valid_range() {
        var bold    = CognitiveDerivationEngine.deriveSearchBudgetDefaults(boldFacets(), boldAxes(), 0.8);
        var anxious = CognitiveDerivationEngine.deriveSearchBudgetDefaults(anxiousWorrierFacets(), anxiousWorrierAxes(), 0.3);

        for (var config : List.of(bold, anxious)) {
            var ext = config.extremeInteraction();
            assertTrue(ext.depthPeak() >= 0.5 && ext.depthPeak() <= 0.95, "depthPeak in range: " + ext.depthPeak());
            assertTrue(ext.breadthPeak() >= 0.5 && ext.breadthPeak() <= 0.95, "breadthPeak in range: " + ext.breadthPeak());
            assertTrue(ext.lateralityPeak() >= 0.5 && ext.lateralityPeak() <= 0.95, "lateralityPeak in range: " + ext.lateralityPeak());
            assertTrue(ext.convergencePeak() >= 0.5 && ext.convergencePeak() <= 0.95, "convergencePeak in range: " + ext.convergencePeak());
        }
    }

    @Test
    void conscientiousness_raises_depth_and_convergence_peaks() {
        var disciplined = CognitiveDerivationEngine.deriveSearchBudgetDefaults(disciplinedFacets(), disciplinedAxes(), 0.3);
        var anxious     = CognitiveDerivationEngine.deriveSearchBudgetDefaults(anxiousWorrierFacets(), anxiousWorrierAxes(), 0.3);

        assertTrue(disciplined.extremeInteraction().depthPeak() > anxious.extremeInteraction().depthPeak(),
                   "Same anxiety, higher-C should raise depth peak: disciplined=" + disciplined.extremeInteraction().depthPeak() + " anxious=" + anxious.extremeInteraction().depthPeak());
        assertTrue(disciplined.extremeInteraction().convergencePeak() > anxious.extremeInteraction().convergencePeak(),
                   "Same anxiety, higher-C should raise convergence peak: disciplined=" + disciplined.extremeInteraction().convergencePeak() + " anxious=" + anxious.extremeInteraction().convergencePeak());
    }

    @Test
    void amplification_threshold_is_derived() {
        var bold        = CognitiveDerivationEngine.deriveSearchBudgetDefaults(boldFacets(), boldAxes(), 0.8);
        var anxious     = CognitiveDerivationEngine.deriveSearchBudgetDefaults(anxiousWorrierFacets(), anxiousWorrierAxes(), 0.3);
        var disciplined = CognitiveDerivationEngine.deriveSearchBudgetDefaults(disciplinedFacets(), disciplinedAxes(), 0.3);

        assertNotNull(bold.extremeInteraction().amplificationThreshold());
        assertNotNull(anxious.extremeInteraction().amplificationThreshold());
        assertNotNull(disciplined.extremeInteraction().amplificationThreshold());
    }

    @Test
    void conscientiousness_lowers_amplification_threshold() {
        var disciplined = CognitiveDerivationEngine.deriveSearchBudgetDefaults(disciplinedFacets(), disciplinedAxes(), 0.3);
        var anxious     = CognitiveDerivationEngine.deriveSearchBudgetDefaults(anxiousWorrierFacets(), anxiousWorrierAxes(), 0.3);

        assertTrue(disciplined.extremeInteraction().amplificationThreshold() < anxious.extremeInteraction().amplificationThreshold(),
                   "Higher conscientiousness should lower amplification threshold: disciplined=" + disciplined.extremeInteraction().amplificationThreshold()
                   + " anxious=" + anxious.extremeInteraction().amplificationThreshold());
    }

    @Test
    void amplification_threshold_in_valid_range() {
        var bold    = CognitiveDerivationEngine.deriveSearchBudgetDefaults(boldFacets(), boldAxes(), 0.8);
        var anxious = CognitiveDerivationEngine.deriveSearchBudgetDefaults(anxiousWorrierFacets(), anxiousWorrierAxes(), 0.3);

        for (var config : List.of(bold, anxious)) {
            double t = config.extremeInteraction().amplificationThreshold();
            assertTrue(t >= 0.5 && t <= 0.95, "amplificationThreshold in range: " + t);
        }
    }


    @Test
    void inverted_u_activates_past_per_parameter_peak() {
        var    anxious         = CognitiveDerivationEngine.deriveSearchBudgetDefaults(anxiousWorrierFacets(), anxiousWorrierAxes(), 0.3);
        double lateralityPeak  = anxious.extremeInteraction().lateralityPeak();
        double lateralityDecay = anxious.extremeInteraction().lateralityDecay();

        double atPeak   = activationWithDecay(lateralityPeak, 6.0, 0.5, lateralityDecay, lateralityPeak);
        double pastPeak = activationWithDecay(lateralityPeak + 0.1, 6.0, 0.5, lateralityDecay, lateralityPeak);

        assertTrue(pastPeak < atPeak, "Past laterality peak should decline: at=" + atPeak + " past=" + pastPeak);
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
