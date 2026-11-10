package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.goal.DriveGoalProposal;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;

import java.util.List;

public class EmotionRegulationGoalParticipant implements CognitionTickParticipant {

    private final EmotionRegulationTickParticipant regulationParticipant;
    private final GoalProposalOrchestrator goalOrchestrator;

    public EmotionRegulationGoalParticipant(EmotionRegulationTickParticipant regulationParticipant,
                                             GoalProposalOrchestrator goalOrchestrator) {
        this.regulationParticipant = regulationParticipant;
        this.goalOrchestrator = goalOrchestrator;
    }

    @Override
    public void tick(CognitionTickContext context) {
        var result = regulationParticipant.currentResult(context.agentId(), context.tenantId());
        if (result.isEmpty()) {return;}

        var reg = result.get();
        if (reg.isEnvironmentalStrategy()) {
            if (reg.regulationAxis() == null || reg.environmentalDirective() == null) {return;}
            var proposal = new DriveGoalProposal(
                    reg.regulationAxis(),
                    "regulation-" + reg.strategy().name().toLowerCase().replace('_', '-'),
                    reg.environmentalDirective(),
                    "Emotion regulation: " + reg.narrative(),
                    reg.regulatedIntensity());
            goalOrchestrator.registerParticipantGoals("emotion-regulation", context.agentId(), context.tenantId(), List.of(proposal));
        } else if (reg.isSocialStrategy()) {
            var    axis        = reg.regulationAxis() != null ? reg.regulationAxis() : DriveAxis.AFFILIATION;
            String goalName    = "social-regulation-" + reg.strategy().name().toLowerCase().replace('_', '-');
            String description = reg.narrative();
            if (reg.targetAgentId() != null) {
                description += " [target: " + reg.targetAgentId() + "]";
            }
            var proposal = new DriveGoalProposal(
                    axis, goalName, description,
                    "Social regulation: " + reg.strategy().name(),
                    reg.regulatedIntensity());
            goalOrchestrator.registerParticipantGoals("emotion-regulation", context.agentId(), context.tenantId(), List.of(proposal));
        }
    }
}
