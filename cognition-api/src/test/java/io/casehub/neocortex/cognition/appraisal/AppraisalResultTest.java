package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.PadProjection;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AppraisalResultTest {

    @Test
    void constructsWithEmotionsAndTendencies() {
        var emotion = new CognitiveEmotion(
                EmotionType.FEAR, 0.8, "dark-corridor", Instant.now(),
                EmotionSource.INTRINSIC, new PadProjection(-0.6, 0.7, -0.4));
        var tendency = new ActionTendency(ActionReadiness.AVOIDANCE, 0.7, "dark-corridor");
        var habituation = HabituationState.empty();

        var result = new AppraisalResult(
                List.of(emotion), List.of(tendency), habituation);

        assertThat(result.emotions()).hasSize(1);
        assertThat(result.emotions().get(0).type()).isEqualTo(EmotionType.FEAR);
        assertThat(result.actionTendencies()).hasSize(1);
        assertThat(result.actionTendencies().get(0).readiness()).isEqualTo(ActionReadiness.AVOIDANCE);
    }

    @Test
    void emptyResult() {
        var result = AppraisalResult.empty();
        assertThat(result.emotions()).isEmpty();
        assertThat(result.actionTendencies()).isEmpty();
        assertThat(result.updatedHabituation().observationCounts()).isEmpty();
    }

    @Test
    void nullListsDefaultToEmpty() {
        var result = new AppraisalResult(null, null, null);
        assertThat(result.emotions()).isEmpty();
        assertThat(result.actionTendencies()).isEmpty();
        assertThat(result.updatedHabituation()).isNotNull();
    }

    @Test
    void perceivedSituationPassThrough() {
        var situation = PerceivedSituation.passThrough("The room is dark");
        assertThat(situation.narrative()).isEqualTo("The room is dark");
        assertThat(situation.salience()).isEmpty();
    }

    @Test
    void perceivedSituationWithSalience() {
        var situation = new PerceivedSituation(
                "You notice the empty chair",
                Map.of("protection", 0.9, "curiosity", 0.3));
        assertThat(situation.salience()).hasSize(2);
        assertThat(situation.salience().get("protection")).isCloseTo(0.9, within(0.001));
    }

    @Test
    void salienceContextRequiresObservation() {
        assertThatThrownBy(() -> new SalienceContext(null, List.of(), null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void appraisalContextRequiresSituation() {
        assertThatThrownBy(() -> new AppraisalContext(null, List.of(), null, null, HabituationState.empty(), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void salienceStrategyFunctionalInterface() {
        SalienceStrategy strategy = ctx -> PerceivedSituation.passThrough(ctx.observation());
        var result = strategy.perceive(new SalienceContext("test", List.of(), null, List.of(), List.of()));
        assertThat(result.narrative()).isEqualTo("test");
    }

    @Test
    void narrativeFieldPreserved() {
        var result = new AppraisalResult(List.of(), List.of(), HabituationState.empty(),
                "You feel a knot of dread in your stomach.");
        assertThat(result.narrative()).isEqualTo("You feel a knot of dread in your stomach.");
    }

    @Test
    void narrativeNullByDefault() {
        var result = new AppraisalResult(List.of(), List.of(), HabituationState.empty());
        assertThat(result.narrative()).isNull();
    }

    @Test
    void emptyFactoryHasNullNarrative() {
        assertThat(AppraisalResult.empty().narrative()).isNull();
    }

    @Test
    void appraisalStrategyFunctionalInterface() {
        AppraisalStrategy strategy = ctx -> AppraisalResult.empty();
        var situation = PerceivedSituation.passThrough("test");
        var result = strategy.appraise(new AppraisalContext(situation, List.of(), null, null, HabituationState.empty(), null));
        assertThat(result.emotions()).isEmpty();
    }
}
