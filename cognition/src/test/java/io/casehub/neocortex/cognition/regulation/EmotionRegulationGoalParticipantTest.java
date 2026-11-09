package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.goal.DriveGoalProposal;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmotionRegulationGoalParticipantTest {

    private EmotionRegulationTickParticipant regulationParticipant;
    private GoalProposalOrchestrator goalOrchestrator;
    private CognitionTickContext context;

    @BeforeEach
    void setUp() {
        regulationParticipant = mock(EmotionRegulationTickParticipant.class);
        goalOrchestrator = mock(GoalProposalOrchestrator.class);
        context = new CognitionTickContext("a1", "t1", null, (a, t) -> Set.of());
    }

    @Test
    void registersGoalForSituationSelection() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SITUATION_SELECTION,
                List.of("anxiety"), List.of(),
                0.9, 0.5, 0.7,
                "You decide to leave", DriveAxis.AFFILIATION,
                "Avoid being alone with Muttley");
        when(regulationParticipant.currentResult("a1", "t1"))
                .thenReturn(Optional.of(result));

        var participant = new EmotionRegulationGoalParticipant(regulationParticipant, goalOrchestrator);
        participant.tick(context);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(goalOrchestrator).registerGoals(eq("a1"), eq("t1"), captor.capture());

        @SuppressWarnings("unchecked")
        List<DriveGoalProposal> goals = captor.getValue();
        assertThat(goals).hasSize(1);
        assertThat(goals.getFirst().axis()).isEqualTo(DriveAxis.AFFILIATION);
        assertThat(goals.getFirst().goalDescription()).contains("Avoid being alone with Muttley");
    }

    @Test
    void registersGoalForSituationModification() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SITUATION_MODIFICATION,
                List.of("frustration"), List.of("competence"),
                0.75, 0.5, 0.5,
                "You try to change the situation", DriveAxis.COMPETENCE,
                "Redirect the conversation");
        when(regulationParticipant.currentResult("a1", "t1"))
                .thenReturn(Optional.of(result));

        var participant = new EmotionRegulationGoalParticipant(regulationParticipant, goalOrchestrator);
        participant.tick(context);

        verify(goalOrchestrator).registerGoals(eq("a1"), eq("t1"), any());
    }

    @Test
    void doesNotRegisterGoalForInternalStrategy() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.RESPONSE_MODULATION,
                List.of("anger"), List.of("gloating"),
                0.85, 0.4, 0.6,
                "You hold back the triumph", null, null);
        when(regulationParticipant.currentResult("a1", "t1"))
                .thenReturn(Optional.of(result));

        var participant = new EmotionRegulationGoalParticipant(regulationParticipant, goalOrchestrator);
        participant.tick(context);

        verify(goalOrchestrator, never()).registerGoals(any(), any(), any());
    }

    @Test
    void doesNothingWhenNoRegulationResult() {
        when(regulationParticipant.currentResult("a1", "t1"))
                .thenReturn(Optional.empty());

        var participant = new EmotionRegulationGoalParticipant(regulationParticipant, goalOrchestrator);
        participant.tick(context);

        verify(goalOrchestrator, never()).registerGoals(any(), any(), any());
    }

    @Test
    void socialStrategy_producesGoalWithAppendSemantics() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SOCIAL_SUPPORT_SEEKING,
                List.of("ANXIETY"), List.of(),
                0.8, 0.4, 0.6,
                "You seek comfort from Penelope",
                DriveAxis.AFFILIATION, null, "penelope-pitstop");
        when(regulationParticipant.currentResult("a1", "t1"))
                .thenReturn(Optional.of(result));

        var participant = new EmotionRegulationGoalParticipant(regulationParticipant, goalOrchestrator);
        participant.tick(context);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(goalOrchestrator).appendGoals(eq("a1"), eq("t1"), captor.capture());

        @SuppressWarnings("unchecked")
        List<DriveGoalProposal> goals = captor.getValue();
        assertThat(goals).hasSize(1);
        assertThat(goals.getFirst().goalName()).contains("social-regulation");
        assertThat(goals.getFirst().goalDescription()).contains("penelope-pitstop");
        verify(goalOrchestrator, never()).registerGoals(any(), any(), any());
    }

    @Test
    void coRegulation_producesGoalWithTarget() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.CO_REGULATION,
                List.of("EMPATHY"), List.of(),
                0.7, 0.3, 0.7,
                "You comfort Dick Dastardly",
                DriveAxis.AFFILIATION, null, "dick-dastardly");
        when(regulationParticipant.currentResult("a1", "t1"))
                .thenReturn(Optional.of(result));

        var participant = new EmotionRegulationGoalParticipant(regulationParticipant, goalOrchestrator);
        participant.tick(context);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(goalOrchestrator).appendGoals(eq("a1"), eq("t1"), captor.capture());

        @SuppressWarnings("unchecked")
        List<DriveGoalProposal> goals = captor.getValue();
        assertThat(goals).hasSize(1);
        assertThat(goals.getFirst().goalName()).contains("co-regulation");
    }

    @Test
    void environmentalStrategy_stillUsesRegisterGoals() {
        var result = new EmotionRegulationResult(
                EmotionRegulationStrategy.SITUATION_SELECTION,
                List.of("ANXIETY"), List.of(),
                0.9, 0.5, 0.7,
                "You leave the room",
                DriveAxis.AUTONOMY, "Avoid the library", null);
        when(regulationParticipant.currentResult("a1", "t1"))
                .thenReturn(Optional.of(result));

        var participant = new EmotionRegulationGoalParticipant(regulationParticipant, goalOrchestrator);
        participant.tick(context);

        verify(goalOrchestrator).registerGoals(eq("a1"), eq("t1"), any());
        verify(goalOrchestrator, never()).appendGoals(any(), any(), any());
    }

}
