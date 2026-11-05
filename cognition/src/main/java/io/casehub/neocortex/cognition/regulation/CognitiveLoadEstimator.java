package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.drive.DriveOrchestrator;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognition.mentalmodel.MentalModelOrchestrator;
import org.jspecify.annotations.Nullable;

public class CognitiveLoadEstimator {

    static final int HIGH_THRESHOLD = 8;
    static final int MODERATE_THRESHOLD = 4;

    private final DriveOrchestrator drives;
    private final @Nullable GoalProposalOrchestrator goals;
    private final @Nullable MentalModelOrchestrator mentalModel;

    public CognitiveLoadEstimator(DriveOrchestrator drives,
                                   @Nullable GoalProposalOrchestrator goals,
                                   @Nullable MentalModelOrchestrator mentalModel) {
        this.drives = drives;
        this.goals = goals;
        this.mentalModel = mentalModel;
    }

    public CognitiveLoadEstimate estimate(String agentId, String tenantId) {
        int driveCount = drives.currentDrives(agentId, tenantId)
                .map(p -> p.drives().size())
                .orElse(0);

        int goalCount = (goals != null)
                ? goals.currentProposals(agentId, tenantId)
                        .map(java.util.List::size)
                        .orElse(0)
                : 0;

        int tomCount = (mentalModel != null)
                ? mentalModel.activeSnapshots(agentId, tenantId).size()
                : 0;

        int total = driveCount + goalCount + tomCount;
        CognitiveLoadLevel level;
        if (total >= HIGH_THRESHOLD) {
            level = CognitiveLoadLevel.HIGH;
        } else if (total >= MODERATE_THRESHOLD) {
            level = CognitiveLoadLevel.MODERATE;
        } else {
            level = CognitiveLoadLevel.LOW;
        }

        return new CognitiveLoadEstimate(level, driveCount, goalCount, tomCount);
    }
}
