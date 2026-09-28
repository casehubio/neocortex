package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.mindmap.ActionContext;
import io.casehub.neocortex.mindmap.ActionOutcome;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class HeuristicActionAppraisalTest {

    private final HeuristicActionAppraisal appraisal = new HeuristicActionAppraisal();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private ActionContext selfContext(ActionOutcome outcome, double goalRelevance) {
        return selfContext(outcome, goalRelevance, AppraisalWeights.NEUTRAL);
    }

    private ActionContext selfContext(ActionOutcome outcome, double goalRelevance, AppraisalWeights weights) {
        return new ActionContext("agent-a", "agent-a", "t1", "turn-1",
                "did something", "planning", outcome, goalRelevance,
                PadProjection.NEUTRAL, weights, NOW);
    }

    private ActionContext otherContext(ActionOutcome outcome, double goalRelevance) {
        return otherContext(outcome, goalRelevance, AppraisalWeights.NEUTRAL);
    }

    private ActionContext otherContext(ActionOutcome outcome, double goalRelevance, AppraisalWeights weights) {
        return new ActionContext("agent-b", "agent-a", "t1", "turn-1",
                "helped with task", "planning", outcome, goalRelevance,
                PadProjection.NEUTRAL, weights, NOW);
    }

    @Test
    void selfAppraisal_success_positiveGoalRelevance_producesPride() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.SUCCESS, 0.8));
        assertThat(emotions).extracting(CognitiveEmotion::type).contains(EmotionType.PRIDE);
        var pride = emotions.stream().filter(e -> e.type() == EmotionType.PRIDE).findFirst().orElseThrow();
        assertThat(pride.source()).isEqualTo(EmotionSource.INTRINSIC);
        assertThat(pride.intensity()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
    }

    @Test
    void selfAppraisal_failure_negativeGoalRelevance_producesShame() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.FAILURE, -0.7));
        assertThat(emotions).extracting(CognitiveEmotion::type).contains(EmotionType.SHAME);
        var shame = emotions.stream().filter(e -> e.type() == EmotionType.SHAME).findFirst().orElseThrow();
        assertThat(shame.source()).isEqualTo(EmotionSource.INTRINSIC);
    }

    @Test
    void otherAppraisal_success_positiveGoalRelevance_producesAdmiration() {
        var emotions = appraisal.appraise(otherContext(ActionOutcome.SUCCESS, 0.8));
        assertThat(emotions).extracting(CognitiveEmotion::type).contains(EmotionType.ADMIRATION);
        var admiration = emotions.stream().filter(e -> e.type() == EmotionType.ADMIRATION).findFirst().orElseThrow();
        assertThat(admiration.source()).isEqualTo(EmotionSource.ATTRIBUTED);
    }

    @Test
    void otherAppraisal_failure_negativeGoalRelevance_producesReproach() {
        var emotions = appraisal.appraise(otherContext(ActionOutcome.FAILURE, -0.6));
        assertThat(emotions).extracting(CognitiveEmotion::type).contains(EmotionType.REPROACH);
        var reproach = emotions.stream().filter(e -> e.type() == EmotionType.REPROACH).findFirst().orElseThrow();
        assertThat(reproach.source()).isEqualTo(EmotionSource.ATTRIBUTED);
    }

    @Test
    void neutralOutcome_producesNoEmotion() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.NEUTRAL, 0.8));
        assertThat(emotions).isEmpty();
    }

    @Test
    void zeroGoalRelevance_producesNoEmotion() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.SUCCESS, 0.0));
        assertThat(emotions).isEmpty();
    }

    @Test
    void belowThreshold_producesNoEmotion() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.SUCCESS, 0.1));
        assertThat(emotions).isEmpty();
    }

    @Test
    void asymmetricThresholds_strictAgent_shameEasierThanPride() {
        var strict = new AppraisalWeights(1.0, 1.0, 1.0, 2.0, 1.0);
        var shameEmotions = appraisal.appraise(selfContext(ActionOutcome.FAILURE, -0.15, strict));
        assertThat(shameEmotions).extracting(CognitiveEmotion::type).contains(EmotionType.SHAME);

        var prideEmotions = appraisal.appraise(selfContext(ActionOutcome.SUCCESS, 0.15, strict));
        assertThat(prideEmotions).isEmpty();
    }

    @Test
    void asymmetricThresholds_strictAgent_prideRequiresHighRelevance() {
        var strict = new AppraisalWeights(1.0, 1.0, 1.0, 2.0, 1.0);
        var emotions = appraisal.appraise(selfContext(ActionOutcome.SUCCESS, 0.5, strict));
        assertThat(emotions).extracting(CognitiveEmotion::type).contains(EmotionType.PRIDE);
    }

    @Test
    void compound_pride_withHighGoalRelevance_producesGratification() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.SUCCESS, 0.8));
        assertThat(emotions).extracting(CognitiveEmotion::type)
                .contains(EmotionType.PRIDE, EmotionType.GRATIFICATION);
    }

    @Test
    void compound_shame_withNegativeGoalRelevance_producesRemorse() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.FAILURE, -0.8));
        assertThat(emotions).extracting(CognitiveEmotion::type)
                .contains(EmotionType.SHAME, EmotionType.REMORSE);
    }

    @Test
    void compound_admiration_withHighGoalRelevance_producesGratitude() {
        var emotions = appraisal.appraise(otherContext(ActionOutcome.SUCCESS, 0.8));
        assertThat(emotions).extracting(CognitiveEmotion::type)
                .contains(EmotionType.ADMIRATION, EmotionType.GRATITUDE);
    }

    @Test
    void compound_reproach_withNegativeGoalRelevance_producesAnger() {
        var emotions = appraisal.appraise(otherContext(ActionOutcome.FAILURE, -0.8));
        assertThat(emotions).extracting(CognitiveEmotion::type)
                .contains(EmotionType.REPROACH, EmotionType.ANGER);
    }

    @Test
    void compound_notProduced_whenGoalRelevanceBelowCompoundThreshold() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.SUCCESS, 0.25));
        assertThat(emotions).extracting(CognitiveEmotion::type)
                .contains(EmotionType.PRIDE)
                .doesNotContain(EmotionType.GRATIFICATION);
    }

    @Test
    void padProjection_matchesAlmaTable() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.SUCCESS, 0.8));
        var pride = emotions.stream().filter(e -> e.type() == EmotionType.PRIDE).findFirst().orElseThrow();
        assertThat(pride.pad().pleasure()).isGreaterThan(0.0);
        assertThat(pride.pad().dominance()).isGreaterThan(0.0);
    }

    @Test
    void subjectId_isActingAgent() {
        var emotions = appraisal.appraise(selfContext(ActionOutcome.SUCCESS, 0.8));
        var pride = emotions.stream().filter(e -> e.type() == EmotionType.PRIDE).findFirst().orElseThrow();
        assertThat(pride.subjectId()).isEqualTo("agent-a");
    }
}
