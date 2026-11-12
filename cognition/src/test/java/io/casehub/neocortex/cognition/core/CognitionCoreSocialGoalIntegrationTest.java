package io.casehub.neocortex.cognition.core;

import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.PerceivedSituation;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.drive.DriveProfile;
import io.casehub.neocortex.cognition.goal.GoalEscalationConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalConfig;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognition.mood.MoodConfig;
import io.casehub.neocortex.cognition.mood.MoodOrchestrator;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationGenerationPhase;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationStrategy;
import io.casehub.neocortex.cognition.regulation.RegulationContext;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.ManagedSession;
import io.casehub.platform.agent.session.SessionPipeline;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CognitionCoreSocialGoalIntegrationTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void tickRoutesSocialRegulationToGoalOnlyForAnInteractingAgent(boolean friendPresent) {
        var mood = new MoodOrchestrator(MoodConfig.defaults());
        var drives = mock(DriveOrchestrator.class);
        when(drives.currentDrives("agent", "tenant")).thenReturn(Optional.of(new DriveProfile(
                "agent", "tenant", Map.of(), 0.0, DriveAxis.AFFILIATION, Instant.now())));
        var goals = new GoalProposalOrchestrator(drives, List.of(), null, Optional.empty(),
                null, null, null, GoalProposalConfig.defaults(), GoalEscalationConfig.defaults(), Clock.systemUTC());
        var pipeline = mock(SessionPipeline.class);
        var session = mock(ManagedSession.class);
        when(pipeline.openSession(eq("emotion-regulation-agent"), any(AgentSessionInit.class))).thenReturn(session);
        when(session.query(any())).thenReturn(Multi.createFrom().item(new AgentEvent.TextDelta("""
                {"strategy":"SOCIAL_SUPPORT_SEEKING", "targetEmotions":["FEAR"],
                 "peakOriginalIntensity":0.7, "regulatedIntensity":0.35, "effectiveness":0.5,
                 "narrative":"You approach your friend for reassurance", "targetAgentId":"friend"}
                """)));
        var config = CognitionConfig.none().with("appraisal", true).with("regulation", true).with("goals", true);
        var core = new CognitionCore(mood, drives, null, null, null, null, goals, null, null, config, pipeline);
        core.configureAppraisal(context -> new AppraisalResult(List.of(new CognitiveEmotion(
                        EmotionType.FEAR, 0.7, "agent", Instant.now(), EmotionSource.INTRINSIC, PadProjection.NEUTRAL)),
                        List.of(), null),
                context -> PerceivedSituation.passThrough("An alarming conversation"));
        core.configureRegulation(new EmotionRegulationGenerationPhase(pipeline));
        core.configureRegulationGoals();

        core.tick("agent", "tenant", null, (a, t) -> Set.of(), "An alarming conversation", null,
                new RegulationContext("room", friendPresent ? List.of("friend") : List.of(), "conversation"));

        var message = ArgumentCaptor.forClass(String.class);
        verify(session).query(message.capture());
        var trace = core.currentTrace("agent", "tenant").orElseThrow();
        assertThat(trace.appraisalResult().emotions()).singleElement()
                .satisfies(emotion -> assertThat(emotion.type()).isEqualTo(EmotionType.FEAR));
        if (friendPresent) {
            assertThat(message.getValue()).contains("Available agents: friend");
            assertThat(trace.regulationResult().strategy()).isEqualTo(EmotionRegulationStrategy.SOCIAL_SUPPORT_SEEKING);
            assertThat(goals.currentProposals("agent", "tenant").orElseThrow()).singleElement().satisfies(goal -> {
                assertThat(goal.goalName()).isEqualTo("social-regulation-social-support-seeking");
                assertThat(goal.goalDescription()).contains("reassurance", "[target: friend]");
                assertThat(goal.axis()).isEqualTo(DriveAxis.AFFILIATION);
            });
        } else {
            assertThat(message.getValue()).doesNotContain("Available agents:");
            assertThat(trace.regulationResult()).isNull();
            assertThat(goals.currentProposals("agent", "tenant")).isEmpty();
        }
        assertThat(goals.currentProposals("agent", "other-tenant")).isEmpty();
    }
}
