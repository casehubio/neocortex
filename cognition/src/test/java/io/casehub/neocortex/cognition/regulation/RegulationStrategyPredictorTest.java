package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegulationStrategyPredictorTest {

    private final RegulationStrategyPredictor predictor = new RegulationStrategyPredictor();

    // --- Suppressor personality (avoidant/conservative/independent) ---

    private static final DispositionAxes SUPPRESSOR =
            new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant");

    @Test
    void suppressor_anger_predictsResponseModulation() {
        var result = predictor.predict(SUPPRESSOR, EmotionType.ANGER, 0.5, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION);
    }

    @Test
    void suppressor_shame_predictsCognitiveChange() {
        // Judge finding: suppressing shame is counterproductive (score 2/5).
        // Even avoidant personalities get CC for shame — it needs reprocessing, not containment.
        var result = predictor.predict(SUPPRESSOR, EmotionType.SHAME, 0.5, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
    }

    @Test
    void suppressor_reproach_predictsResponseModulation() {
        var result = predictor.predict(SUPPRESSOR, EmotionType.REPROACH, 0.5, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION);
    }

    // --- Reappraiser personality (analytical/strict/cooperative/high-autonomy) ---

    private static final DispositionAxes REAPPRAISER =
            new DispositionAxes("cooperative", "strict", "calculated", "high", "analytical");

    @Test
    void reappraiser_anger_predictsCognitiveChange() {
        var result = predictor.predict(REAPPRAISER, EmotionType.ANGER, 0.5, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
    }

    @Test
    void reappraiser_shame_predictsCognitiveChange() {
        var result = predictor.predict(REAPPRAISER, EmotionType.SHAME, 0.5, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
    }

    // --- Fear is always AD regardless of personality ---

    @Test
    void fear_alwaysPredictsAttentionalDeployment() {
        var result = predictor.predict(SUPPRESSOR, EmotionType.FEAR, 0.5, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT);
    }

    @Test
    void distress_alwaysPredictsAttentionalDeployment() {
        var result = predictor.predict(REAPPRAISER, EmotionType.DISTRESS, 0.5, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT);
    }

    // --- Positive emotions produce no regulation ---

    @Test
    void positiveEmotion_noRegulation() {
        var result = predictor.predict(SUPPRESSOR, EmotionType.JOY, 0.5, 0.0, null);
        assertThat(result).isEmpty();
    }

    // --- Competitive+avoidant conflict falls through ---

    @Test
    void competitiveAvoidant_anger_predictsCognitiveChange() {
        // Judge run 1: RM scored 3/5 — "risks passive-aggressive escalation"
        // CC better: "reappraising the contradiction as non-threatening to status"
        var controller = new DispositionAxes("competitive", "flexible", "conservative", "low", "avoidant");
        var result = predictor.predict(controller, EmotionType.ANGER, 0.5, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
    }

    // --- Mild reappraiser predicts CC at lower confidence ---

    @Test
    void mildReappraiser_anger_predictsCognitiveChange() {
        // cooperative social + cooperative conflict = 2 reappraiser signals → mild CC
        var mixed = new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative");
        var result = predictor.predict(mixed, EmotionType.ANGER, 0.5, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
    }

    // --- Truly ambiguous personality falls through ---

    @Test
    void trulyAmbiguous_anger_fallsThrough() {
        var ambiguous = new DispositionAxes("independent", "moderate", "calculated", "moderate", "competitive");
        var result = predictor.predict(ambiguous, EmotionType.ANGER, 0.5, 0.0, null);
        assertThat(result).isEmpty();
    }

    // --- Contextual overrides ---

    @Test
    void highIntensity_overridesEmotionType() {
        var result = predictor.predict(SUPPRESSOR, EmotionType.ANGER, 0.9, 0.0, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT);
    }

    @Test
    void highFatigue_overridesEmotionType() {
        var result = predictor.predict(REAPPRAISER, EmotionType.ANGER, 0.5, 0.8, null);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT);
    }

    @Test
    void highLoad_constrainsToLowCost() {
        var result = predictor.predict(REAPPRAISER, EmotionType.ANGER, 0.5, 0.0, CognitiveLoadLevel.HIGH);
        assertThat(result).isPresent();
        assertThat(result.get().strategy()).satisfiesAnyOf(
                s -> assertThat(s).isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION),
                s -> assertThat(s).isEqualTo(EmotionRegulationStrategy.ATTENTIONAL_DEPLOYMENT));
    }

    // --- toResult ---

    @Test
    void toResult_producesCorrectRegulationResult() {
        var prediction = new RegulationPrediction(
                EmotionRegulationStrategy.COGNITIVE_CHANGE, 0.75, 0.55);
        var result = RegulationStrategyPredictor.toResult(
                prediction, List.of("ANGER"), List.of("COMPETENCE"), 0.8);

        assertThat(result.strategy()).isEqualTo(EmotionRegulationStrategy.COGNITIVE_CHANGE);
        assertThat(result.peakOriginalIntensity()).isEqualTo(0.8);
        assertThat(result.regulatedIntensity()).isCloseTo(0.36, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.effectiveness()).isEqualTo(0.55);
        assertThat(result.narrative()).isNotEmpty();
    }

    // --- Emotion category classification ---

    @Test
    void emotionCategory_classifiesCorrectly() {
        assertThat(RegulationStrategyPredictor.emotionCategory(EmotionType.ANGER))
                .isEqualTo(RegulationStrategyPredictor.EmotionCategory.CONTROLLABLE_NEGATIVE);
        assertThat(RegulationStrategyPredictor.emotionCategory(EmotionType.FEAR))
                .isEqualTo(RegulationStrategyPredictor.EmotionCategory.UNCONTROLLABLE_NEGATIVE);
        assertThat(RegulationStrategyPredictor.emotionCategory(EmotionType.SHAME))
                .isEqualTo(RegulationStrategyPredictor.EmotionCategory.SELF_CONSCIOUS);
        assertThat(RegulationStrategyPredictor.emotionCategory(EmotionType.REPROACH))
                .isEqualTo(RegulationStrategyPredictor.EmotionCategory.OTHER_DIRECTED);
        assertThat(RegulationStrategyPredictor.emotionCategory(EmotionType.JOY))
                .isEqualTo(RegulationStrategyPredictor.EmotionCategory.POSITIVE);
    }
}
