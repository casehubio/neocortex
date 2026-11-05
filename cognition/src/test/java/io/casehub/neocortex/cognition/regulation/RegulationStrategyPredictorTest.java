package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegulationStrategyPredictorTest {

    private final RegulationStrategyPredictor predictor = new RegulationStrategyPredictor();

    // --- Personality-driven predictions ---

    @Test
    void strictRuleFollowing_analyticalConflict_predictsCognitiveChange() {
        // strict + analytical → strong COGNITIVE_CHANGE signal
        var axes = new DispositionAxes("cooperative", "strict", "calculated", "high", "analytical");
        var result = predictor.predict(axes, 0.5, 0.0, CognitiveLoadLevel.LOW);

        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
        assertThat(result.get().confidence()).isGreaterThan(0.6);
    }

    @Test
    void avoidantConflict_conservativeRisk_predictsResponseModulation() {
        // avoidant + conservative → strong RESPONSE_MODULATION signal
        var axes = new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant");
        var result = predictor.predict(axes, 0.5, 0.0, CognitiveLoadLevel.LOW);

        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION);
        assertThat(result.get().confidence()).isGreaterThan(0.6);
    }

    @Test
    void boldRisk_competitiveConflict_predictsAttentionalDeployment() {
        // bold + competitive → ATTENTIONAL_DEPLOYMENT
        var axes = new DispositionAxes("competitive", "flexible", "bold", "moderate", "competitive");
        var result = predictor.predict(axes, 0.5, 0.0, CognitiveLoadLevel.LOW);

        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT);
        assertThat(result.get().confidence()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    void ambiguousDisposition_fallsThroughToLlm() {
        // moderate everything → no dominant strategy
        var axes = new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative");
        var result = predictor.predict(axes, 0.5, 0.0, CognitiveLoadLevel.LOW);

        assertThat(result).isEmpty();
    }

    // --- Contextual overrides ---

    @Test
    void highIntensity_overridesPersonality_toAttentionalDeployment() {
        // Even with strong RESPONSE_MODULATION personality, high intensity → distraction
        var axes = new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant");
        var result = predictor.predict(axes, 0.9, 0.0, CognitiveLoadLevel.LOW);

        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT);
        assertThat(result.get().confidence()).isGreaterThanOrEqualTo(0.80);
    }

    @Test
    void highFatigue_forcesAttentionalDeployment() {
        // High fatigue → lowest-cost strategy regardless of personality
        var axes = new DispositionAxes("cooperative", "strict", "calculated", "high", "analytical");
        var result = predictor.predict(axes, 0.5, 0.8, CognitiveLoadLevel.LOW);

        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT);
        assertThat(result.get().confidence()).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    void highCognitiveLoad_constrainsToLowCostStrategies() {
        // High load → only RESPONSE_MODULATION or ATTENTIONAL_DEPLOYMENT
        var axes = new DispositionAxes("cooperative", "strict", "calculated", "high", "analytical");
        var result = predictor.predict(axes, 0.5, 0.0, CognitiveLoadLevel.HIGH);

        assertThat(result).isPresent();
        assertThat(result.get().strategy()).satisfiesAnyOf(
                s -> assertThat(s).isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION),
                s -> assertThat(s).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT));
    }

    @Test
    void highLoad_avoidantConflict_prefersResponseModulation() {
        var axes = new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "avoidant");
        var result = predictor.predict(axes, 0.5, 0.0, CognitiveLoadLevel.HIGH);

        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION);
    }

    @Test
    void highLoad_boldRisk_prefersAttentionalDeployment() {
        var axes = new DispositionAxes("competitive", "flexible", "bold", "moderate", "competitive");
        var result = predictor.predict(axes, 0.5, 0.0, CognitiveLoadLevel.HIGH);

        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT);
    }

    // --- Environmental strategies never predicted ---

    @Test
    void neverPredictsSituationSelection() {
        // No DispositionAxes combination should produce SITUATION_SELECTION
        var personalities = List.of(
                new DispositionAxes("cooperative", "strict", "conservative", "high", "analytical"),
                new DispositionAxes("independent", "flexible", "bold", "low", "competitive"),
                new DispositionAxes("competitive", "moderate", "calculated", "moderate", "avoidant"));

        for (var axes : personalities) {
            var result = predictor.predict(axes, 0.5, 0.0, null);
            if (result.isPresent()) {
                assertThat(result.get().strategy())
                        .isNotIn(EmotionRegulationStrategy.SITUATION_SELECTION,
                                EmotionRegulationStrategy.SITUATION_MODIFICATION);
            }
        }
    }

    // --- toResult ---

    @Test
    void toResult_producesCorrectRegulationResult() {
        var prediction = new RegulationPrediction(
                EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.75, 0.55);

        var result = RegulationStrategyPredictor.toResult(
                prediction, List.of("ANGER"), List.of("COMPETENCE"), 0.8);

        assertThat(result.strategy()).isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
        assertThat(result.targetEmotions()).containsExactly("ANGER");
        assertThat(result.targetDrives()).containsExactly("COMPETENCE");
        assertThat(result.peakOriginalIntensity()).isEqualTo(0.8);
        assertThat(result.regulatedIntensity()).isCloseTo(0.36, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.effectiveness()).isEqualTo(0.55);
        assertThat(result.narrative()).isNotEmpty();
        assertThat(result.regulationAxis()).isNull();
        assertThat(result.environmentalDirective()).isNull();
    }

    // --- Null cognitive load ---

    @Test
    void nullCognitiveLoad_usesPersonalityOnly() {
        var axes = new DispositionAxes("cooperative", "strict", "calculated", "high", "analytical");
        var result = predictor.predict(axes, 0.5, 0.0, null);

        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
    }

    // --- Vote computation ---

    @Test
    void computeVotes_neverIncludesEnvironmentalStrategies() {
        var axes = new DispositionAxes("cooperative", "strict", "conservative", "high", "analytical");
        var votes = RegulationStrategyPredictor.computeVotes(axes);

        assertThat(votes).doesNotContainKey(EmotionRegulationStrategy.SITUATION_SELECTION);
        assertThat(votes).doesNotContainKey(EmotionRegulationStrategy.SITUATION_MODIFICATION);
    }

    // --- Custom threshold ---

    @Test
    void higherThreshold_requiresStrongerSignal() {
        var strictPredictor = new RegulationStrategyPredictor(0.9);
        // moderate cooperation → some COGNITIVE_CHANGE signal but not enough at 0.9 threshold
        var axes = new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative");
        var result = strictPredictor.predict(axes, 0.5, 0.0, CognitiveLoadLevel.LOW);

        assertThat(result).isEmpty();
    }
}
