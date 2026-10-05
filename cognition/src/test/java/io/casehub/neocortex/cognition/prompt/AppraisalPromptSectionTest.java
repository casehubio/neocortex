package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.appraisal.*;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.PadProjection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AppraisalPromptSectionTest {

    @Test
    void rendersNullWhenNoResult() {
        var participant = participantReturning(Optional.empty());
        var section = new AppraisalPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        assertThat(section.render(context)).isNull();
    }

    @Test
    void rendersEmotionsWhenPresent() {
        var emotion = new CognitiveEmotion(
                EmotionType.FEAR, 0.8, "dark-corridor", Instant.now(),
                EmotionSource.INTRINSIC, new PadProjection(-0.6, 0.7, -0.4));
        var tendency = new ActionTendency(ActionReadiness.AVOIDANCE, 0.7, "dark-corridor");
        var result = new AppraisalResult(
                List.of(emotion), List.of(tendency), HabituationState.empty());

        var participant = participantReturning(Optional.of(result));
        var section = new AppraisalPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        var rendered = section.render(context);
        assertThat(rendered).isNotNull();
        assertThat(rendered).contains("fear");
        assertThat(rendered).contains("away");
    }

    @Test
    void rendersEmptyResultAsNull() {
        var result = AppraisalResult.empty();
        var participant = participantReturning(Optional.of(result));
        var section = new AppraisalPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        assertThat(section.render(context)).isNull();
    }

    @Test
    void filtersLowIntensityTendencies() {
        var tendency = new ActionTendency(ActionReadiness.ATTENDING, 0.2, "bookshelf");
        var result = new AppraisalResult(List.of(), List.of(tendency), HabituationState.empty());

        var participant = participantReturning(Optional.of(result));
        var section = new AppraisalPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        assertThat(section.render(context)).isNull();
    }

    @Test
    void rendersMultipleEmotions() {
        var fear = new CognitiveEmotion(
                EmotionType.FEAR, 0.8, "corridor", Instant.now(),
                EmotionSource.INTRINSIC, new PadProjection(-0.6, 0.7, -0.4));
        var anger = new CognitiveEmotion(
                EmotionType.ANGER, 0.5, "blocker", Instant.now(),
                EmotionSource.INTRINSIC, new PadProjection(-0.5, 0.8, 0.3));
        var result = new AppraisalResult(
                List.of(fear, anger), List.of(), HabituationState.empty());

        var participant = participantReturning(Optional.of(result));
        var section = new AppraisalPromptSection(participant);
        var context = new CognitionRenderContext("a1", "t1", null);

        var rendered = section.render(context);
        assertThat(rendered).contains("fear").contains("anger");
    }

    private AppraisalTickParticipant participantReturning(Optional<AppraisalResult> result) {
        return new AppraisalTickParticipant(
                new NoOpSalienceStrategy(), new NoOpAppraisalStrategy(),
                null, null, null, CognitionConfig.none()) {
            @Override
            public Optional<AppraisalResult> currentResult(String agentId, String tenantId) {
                return result;
            }
        };
    }
}
