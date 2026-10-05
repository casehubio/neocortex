package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveIntensity;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.memory.mood.MoodState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AppraisalTickParticipantTest {

    private DriveOrchestrator driveOrchestrator;
    private MoodOrchestrator moodOrchestrator;
    private SalienceStrategy salienceStrategy;
    private AppraisalStrategy appraisalStrategy;

    @BeforeEach
    void setUp() {
        driveOrchestrator = mock(DriveOrchestrator.class);
        moodOrchestrator = mock(MoodOrchestrator.class);
        salienceStrategy = new NoOpSalienceStrategy();
        appraisalStrategy = new NoOpAppraisalStrategy();

        var profile = new DriveProfile("a1", "t1",
                Map.of(DriveAxis.CURIOSITY, new DriveIntensity(DriveAxis.CURIOSITY, 0.7, "gaps")),
                0.7, DriveAxis.CURIOSITY, Instant.now());
        when(driveOrchestrator.currentDrives("a1", "t1")).thenReturn(Optional.of(profile));
        when(moodOrchestrator.currentMood("a1", "t1")).thenReturn(Optional.empty());
    }

    @Test
    void skipsWhenAppraisalDisabled() {
        var config = CognitionConfig.none();
        var participant = new AppraisalTickParticipant(
                salienceStrategy, appraisalStrategy, driveOrchestrator,
                moodOrchestrator, null, config);

        participant.tick(contextWithObservation("The room is dark"));

        assertThat(participant.currentResult("a1", "t1")).isEmpty();
    }

    @Test
    void skipsWhenNoObservation() {
        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, appraisalStrategy, driveOrchestrator,
                moodOrchestrator, null, config);

        participant.tick(new CognitionTickContext("a1", "t1", null, (a, t) -> java.util.Set.of()));

        assertThat(participant.currentResult("a1", "t1")).isEmpty();
    }

    @Test
    void producesResultWithObservation() {
        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, appraisalStrategy, driveOrchestrator,
                moodOrchestrator, null, config);

        participant.tick(contextWithObservation("The room is dark"));

        assertThat(participant.currentResult("a1", "t1")).isPresent();
    }

    @Test
    void passesThroughObservationWithSalienceDisabled() {
        var capturingSalience = Mockito.spy(salienceStrategy);
        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                capturingSalience, appraisalStrategy, driveOrchestrator,
                moodOrchestrator, null, config);

        participant.tick(contextWithObservation("dark corridor"));

        verify(capturingSalience, never()).perceive(any());
    }

    @Test
    void runsSalienceWhenEnabled() {
        var capturingSalience = Mockito.spy(salienceStrategy);
        var config = CognitionConfig.none().with("appraisal", true).with("salience", true);
        var participant = new AppraisalTickParticipant(
                capturingSalience, appraisalStrategy, driveOrchestrator,
                moodOrchestrator, null, config);

        participant.tick(contextWithObservation("dark corridor"));

        verify(capturingSalience).perceive(any());
    }

    @Test
    void bridgesEmotionsToMood() {
        var emotion = new CognitiveEmotion(
                EmotionType.FEAR, 0.8, "corridor", Instant.now(),
                EmotionSource.INTRINSIC, new PadProjection(-0.6, 0.7, -0.4));
        AppraisalStrategy emotionProducing = ctx -> new AppraisalResult(
                List.of(emotion), List.of(), HabituationState.empty());

        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, emotionProducing, driveOrchestrator,
                moodOrchestrator, null, config);

        participant.tick(contextWithObservation("dark corridor"));

        verify(moodOrchestrator).record(any(), eq("a1"), eq("t1"));
    }

    @Test
    void noMoodBridgeWhenNoEmotions() {
        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, appraisalStrategy, driveOrchestrator,
                moodOrchestrator, null, config);

        participant.tick(contextWithObservation("boring room"));

        verify(moodOrchestrator, never()).record(any(), any(), any());
    }

    @Test
    void habituationStatePersistsAcrossTicks() {
        AppraisalStrategy trackingStrategy = ctx -> {
            var hash = Integer.toHexString(ctx.situation().narrative().hashCode());
            var count = ctx.habituation().observationCounts().getOrDefault(hash, 0);
            return new AppraisalResult(List.of(), List.of(),
                    ctx.habituation().withObservation(hash, 1.0 - count * 0.2));
        };

        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, trackingStrategy, driveOrchestrator,
                moodOrchestrator, null, config);

        participant.tick(contextWithObservation("bookshelf"));
        participant.tick(contextWithObservation("bookshelf"));

        var result = participant.currentResult("a1", "t1").orElseThrow();
        assertThat(result.updatedHabituation().observationCounts().values())
                .allSatisfy(count -> assertThat(count).isEqualTo(2));
    }

    private CognitionTickContext contextWithObservation(String observation) {
        return new CognitionTickContext("a1", "t1", null, (a, t) -> java.util.Set.of(), observation);
    }
}
