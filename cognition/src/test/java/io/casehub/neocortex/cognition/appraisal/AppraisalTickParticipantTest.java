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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void includesCharacterDrivesFromMindMap() {
        var store = new io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore();
        var sgId = store.createSubgraph(
                new io.casehub.neocortex.mindmap.SubgraphInput("Cognitive",
                                                               io.casehub.neocortex.mindmap.SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(io.casehub.neocortex.mindmap.NodeInput.of("scheming", sgId)
                                                            .withProperties(Map.of(
                                                                    "cognitiveKind", "drive-intensity",
                                                                    "drive-type", "scheming",
                                                                    "intensity", "0.75",
                                                                    "agent-id", "a1")),
                      "t1");

        var capturedDrives = new java.util.concurrent.atomic.AtomicReference<List<Drive>>();
        AppraisalStrategy capturing = ctx -> {
            capturedDrives.set(ctx.drives());
            return AppraisalResult.empty();
        };

        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, capturing, driveOrchestrator,
                moodOrchestrator, null, config, store);

        participant.tick(contextWithObservation("The villain plots"));

        var drives = capturedDrives.get();
        assertThat(drives).isNotNull();
        assertThat(drives).anyMatch(d ->
                                            d.name().equals("scheming") &&
                                            d.category() == DriveCategory.CHARACTER &&
                                            d.intensity() == 0.75);
    }

    @Test
    void worksWithoutMindMapStore() {
        var capturedDrives = new java.util.concurrent.atomic.AtomicReference<List<Drive>>();
        AppraisalStrategy capturing = ctx -> {
            capturedDrives.set(ctx.drives());
            return AppraisalResult.empty();
        };

        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, capturing, driveOrchestrator,
                moodOrchestrator, null, config);

        participant.tick(contextWithObservation("nothing special"));

        var drives = capturedDrives.get();
        assertThat(drives).isNotNull();
        assertThat(drives).allMatch(d -> d.category() == DriveCategory.BASELINE);
    }

    @Test
    void concernSalienceEmptyBeforeTick() {
        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, appraisalStrategy, driveOrchestrator,
                moodOrchestrator, null, config);

        assertThat(participant.currentConcernSalience("a1", "t1")).isEmpty();
    }

    @Test
    void computesConcernSalienceFromDriveDescriptions() {
        var store = new io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore();
        var cogSgId = store.createSubgraph(
                new io.casehub.neocortex.mindmap.SubgraphInput("Cognitive",
                                                               io.casehub.neocortex.mindmap.SubgraphTypes.COGNITIVE, null), "t1");
        store.addNode(io.casehub.neocortex.mindmap.NodeInput.of("scheming", cogSgId)
                                                            .withProperties(Map.of(
                                                                    "cognitiveKind", "drive-intensity",
                                                                    "drive-type", "scheming",
                                                                    "intensity", "0.8",
                                                                    "description", "Compelled to hatch elaborate plans and schemes",
                                                                    "agent-id", "a1")),
                      "t1");

        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, appraisalStrategy, driveOrchestrator,
                moodOrchestrator, null, config, store);

        participant.tick(contextWithObservation("They hatched an elaborate plan to escape"));

        var salience = participant.currentConcernSalience("a1", "t1");
        assertThat(salience).containsKey("scheming");
        assertThat(salience.get("scheming")).isGreaterThan(0.0);
    }

    @Test
    void concernSalienceIncludesActiveGoals() {
        var store = new io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore();
        var goalSgId = store.createSubgraph(
                new io.casehub.neocortex.mindmap.SubgraphInput("Goals",
                                                               io.casehub.neocortex.mindmap.SubgraphTypes.GOAL, null), "t1");
        store.addNode(io.casehub.neocortex.mindmap.NodeInput.of("protect Clara from danger", goalSgId)
                                                            .withProperties(Map.of(
                                                                    "status", "active",
                                                                    "agent-id", "a1")),
                      "t1");

        var config = CognitionConfig.none().with("appraisal", true);
        var participant = new AppraisalTickParticipant(
                salienceStrategy, appraisalStrategy, driveOrchestrator,
                moodOrchestrator, null, config, store);

        participant.tick(contextWithObservation("Clara is in terrible danger"));

        var salience = participant.currentConcernSalience("a1", "t1");
        assertThat(salience).containsKey("protect Clara from danger");
        assertThat(salience.get("protect Clara from danger")).isGreaterThan(0.0);
    }


    private CognitionTickContext contextWithObservation(String observation) {
        return new CognitionTickContext("a1", "t1", null, (a, t) -> java.util.Set.of(), observation);
    }
}
