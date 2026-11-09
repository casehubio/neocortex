package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.cognitive.SearchBudgetConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CognitiveDerivationEngineSearchBudgetTest {

    @Test
    void high_risk_taking_increases_breadth_steepness() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.8, "derived"),
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.3, "derived"));
        var axes = new DispositionAxes("cooperative", "flexible", "bold", "independent", "collaborative");
        var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(
                facets, axes, 0.7);
        assertNotNull(config);
        assertTrue(config.breadthSteepness() >= SearchBudgetConfig.defaults().breadthSteepness(),
                "High Risk Taking + high connectiveBias should increase breadth steepness, got " + config.breadthSteepness());
    }

    @Test
    void strict_rule_following_lowers_depth_midpoint() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.1, "derived"));
        var axes = new DispositionAxes("cooperative", "strict", "conservative", "dependent", "collaborative");
        var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(
                facets, axes, 0.5);
        assertNotNull(config);
        assertTrue(config.depthMidpoint() < SearchBudgetConfig.defaults().depthMidpoint(),
                "Strict ruleFollowing should lower depth midpoint (activate at lower intensity), got " + config.depthMidpoint());
    }

    @Test
    void high_anxiousness_lowers_pathological_threshold() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.9, "derived"),
                new PersonalityFacet(PersonalityFacet.EMOTIONAL_LABILITY, 0.8, "derived"));
        var axes = new DispositionAxes("cooperative", "moderate", "conservative", "dependent", "avoidant");
        var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(
                facets, axes, 0.5);
        assertNotNull(config);
        assertTrue(config.pathologicalThreshold() < SearchBudgetConfig.defaults().pathologicalThreshold(),
                "High Anxiousness + Emotional Lability should lower pathological threshold, got " + config.pathologicalThreshold());
    }

    @Test
    void low_withdrawal_lowers_laterality_midpoint() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.1, "derived"),
                new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.1, "derived"),
                new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.1, "derived"));
        var axes = new DispositionAxes("cooperative", "moderate", "moderate", "independent", "collaborative");
        var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(
                facets, axes, 0.5);
        assertNotNull(config);
        assertTrue(config.lateralityMidpoint() < SearchBudgetConfig.defaults().lateralityMidpoint(),
                "Low Withdrawal + low Intimacy Avoidance should lower laterality midpoint, got " + config.lateralityMidpoint());
    }

    @Test
    void null_facets_returns_null() {
        assertNull(CognitiveDerivationEngine.deriveSearchBudgetDefaults(null, null, 0.5));
    }

    @Test
    void empty_facets_returns_null() {
        assertNull(CognitiveDerivationEngine.deriveSearchBudgetDefaults(List.of(), null, 0.5));
    }

    @Test
    void derive_includes_search_budget_config() {
        var descriptor = new DescriptorView("test-agent",
                new DispositionAxes("cooperative", "strict", "conservative", "dependent", "collaborative"),
                List.of(new WeightedTerm("te", 0.5), new WeightedTerm("si", 0.5)),
                List.of("learn"),
                null,
                List.of(new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.3, "derived")));
        var defaults = CognitiveDerivationEngine.derive(descriptor);
        assertNotNull(defaults.searchBudgetConfig(), "derive() should populate searchBudgetConfig");
    }
// --- channel modulation derivation tests ---

    @Test
    void high_openness_boosts_diversive_magnitudes() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.1, "derived"),
                new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.1, "derived"),
                new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.1, "derived"),
                new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.5, "derived"),
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.5, "derived"));
        var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(facets, null, 0.5);
        assertNotNull(config);
        assertNotNull(config.channelModulation(), "Should derive channel modulation");
        var defaults = io.casehub.neocortex.cognitive.ChannelModulationConfig.defaults();
        assertTrue(config.channelModulation().diversiveBreadthBoost() > defaults.diversiveBreadthBoost(),
                   "High openness should boost diversive breadth magnitude");
        assertTrue(config.channelModulation().diversiveLateralityBoost() > defaults.diversiveLateralityBoost(),
                   "High openness should boost diversive laterality magnitude");
    }

    @Test
    void high_conscientiousness_boosts_specific_depth_magnitude() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.1, "derived"),
                new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.5, "derived"),
                new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.5, "derived"),
                new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.5, "derived"),
                new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.5, "derived"));
        var axes   = new DispositionAxes("cooperative", "strict", "conservative", "dependent", "collaborative");
        var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(facets, axes, 0.5);
        assertNotNull(config);
        assertNotNull(config.channelModulation());
        var defaults = io.casehub.neocortex.cognitive.ChannelModulationConfig.defaults();
        assertTrue(config.channelModulation().specificDepthBoost() > defaults.specificDepthBoost(),
                   "High conscientiousness should boost specific depth magnitude");
        assertTrue(config.channelModulation().highComprehensionConvergenceBoost() > defaults.highComprehensionConvergenceBoost(),
                   "High conscientiousness should boost comprehension convergence magnitude");
    }

    @Test
    void high_anxiousness_boosts_deprivation_convergence_magnitude() {
        var facets = List.of(
                new PersonalityFacet(PersonalityFacet.ANXIOUSNESS, 0.9, "derived"),
                new PersonalityFacet(PersonalityFacet.WITHDRAWAL, 0.5, "derived"),
                new PersonalityFacet(PersonalityFacet.INTIMACY_AVOIDANCE, 0.5, "derived"),
                new PersonalityFacet(PersonalityFacet.SUSPICIOUSNESS, 0.5, "derived"),
                new PersonalityFacet(PersonalityFacet.RISK_TAKING, 0.5, "derived"),
                new PersonalityFacet(PersonalityFacet.IMPULSIVITY, 0.5, "derived"));
        var config = CognitiveDerivationEngine.deriveSearchBudgetDefaults(facets, null, 0.5);
        assertNotNull(config);
        assertNotNull(config.channelModulation());
        var defaults = io.casehub.neocortex.cognitive.ChannelModulationConfig.defaults();
        assertTrue(config.channelModulation().deprivationConvergenceBoost() > defaults.deprivationConvergenceBoost(),
                   "High anxiousness should boost deprivation convergence magnitude");
    }

}
