package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
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
        if (result.isEmpty()) return;

        var reg = result.get();
        if (!reg.isEnvironmentalStrategy()) return;
        if (reg.regulationAxis() == null || reg.environmentalDirective() == null) return;

        var proposal = new DriveGoalProposal(
                reg.regulationAxis(),
                "regulation-" + reg.strategy().name().toLowerCase().replace('_', '-'),
                reg.environmentalDirective(),
                "Emotion regulation: " + reg.narrative(),
                reg.regulatedIntensity());

        goalOrchestrator.registerGoals(context.agentId(), context.tenantId(), List.of(proposal));
    }
}
