package io.casehub.neocortex.cognition.prompt;

import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.goal.CognitiveGoalConfig;
import io.casehub.neocortex.cognition.goal.CognitiveGoalOrchestrator;
import io.casehub.neocortex.cognition.goal.CognitiveGoalState;
import io.casehub.neocortex.cognition.goal.DriveGoalProposal;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class EmergentGoalPromptSection implements CognitionPromptRenderer {

    private final GoalProposalOrchestrator  driveGoals;
    private final CognitiveGoalOrchestrator cognitiveGoals;
    private final CognitiveGoalConfig       config;

    public EmergentGoalPromptSection(GoalProposalOrchestrator driveGoals) {
        this(driveGoals, null, CognitiveGoalConfig.defaults());
    }

    public EmergentGoalPromptSection(GoalProposalOrchestrator driveGoals,
                                     CognitiveGoalOrchestrator cognitiveGoals,
                                     CognitiveGoalConfig config) {
        this.driveGoals     = driveGoals;
        this.cognitiveGoals = cognitiveGoals;
        this.config         = config;
    }

    @Override
    public @Nullable String render(CognitionRenderContext context) {
        String agentId  = context.agentId();
        String tenantId = context.tenantId();

        var unified = new ArrayList<UnifiedGoal>();

        if (driveGoals != null) {
            driveGoals.currentProposals(agentId, tenantId)
                      .ifPresent(proposals -> proposals.forEach(p ->
                              unified.add(UnifiedGoal.fromDrive(p, config.driveWeight()))));
        }

        if (cognitiveGoals != null) {
            Set<String> driveNames = (driveGoals != null)
                    ? driveGoals.registeredGoals(agentId, tenantId).stream()
                            .map(DriveGoalProposal::goalName).collect(java.util.stream.Collectors.toSet())
                    : Set.of();
            cognitiveGoals.currentState(agentId, tenantId)
                          .ifPresent(state -> state.goals().stream()
                                  .filter(ge -> !driveNames.contains(ge.goal().name()))
                                  .forEach(ge -> unified.add(UnifiedGoal.fromCognitive(ge, config.driveWeight()))));
        }

        if (unified.isEmpty()) {return null;}

        unified.sort(Comparator.comparingDouble(UnifiedGoal::compositePriority).reversed());
        return render(unified);
    }

    private static String render(List<UnifiedGoal> goals) {
        var sb = new StringBuilder("Your current goals:");
        for (var goal : goals) {
            sb.append("\n- ");
            var dominant = goal.dominantEmotion();
            if (dominant != null) {
                sb.append(emotionLabel(dominant)).append(" ");
            }
            sb.append(goal.description());
            if (goal.surfacingCount() > 1) {
                sb.append(" (reminded ").append(goal.surfacingCount()).append(" times)");
            }
            sb.append(" [priority: ").append(String.format("%.2f", goal.compositePriority()));
            if (goal.driveAxis() != null) {
                sb.append(", drive: ").append(goal.driveAxis().name().toLowerCase());
            }
            sb.append("]");
        }
        return sb.toString();
    }

    private static String emotionLabel(CognitiveEmotion emotion) {
        return switch (emotion.type()) {
            case FEAR -> "[CONCERNED]";
            case HOPE -> "[HOPEFUL]";
            case SATISFACTION -> "[COMPLETED]";
            case DISAPPOINTMENT -> "[MISSED]";
            case RELIEF -> "[RELIEVED]";
            case FEARS_CONFIRMED -> "[URGENT — MISSED]";
            case DISTRESS -> "[BLOCKED]";
            case PITY -> "[CONCERNED FOR OTHERS]";
            case HAPPY_FOR -> "[GLAD FOR OTHERS]";
            default -> "[" + emotion.type().name() + "]";
        };
    }

    private record UnifiedGoal(
            String description,
            double compositePriority,
            CognitiveEmotion dominantEmotion,
            DriveAxis driveAxis,
            int surfacingCount
    ) {
        static UnifiedGoal fromDrive(DriveGoalProposal p, double driveWeight) {
            double priority = p.driveIntensity() * driveWeight;
            return new UnifiedGoal(p.goalDescription(), priority, null,
                                   p.axis(), 0);
        }

        static UnifiedGoal fromCognitive(CognitiveGoalState.GoalEmotion ge, double driveWeight) {
            var goal = ge.goal();
            double mindmapPriority = goal.property("priority")
                                         .map(v -> {
                                             try {return Double.parseDouble(v);} catch (NumberFormatException e) {
                                                 return 0.5;
                                             }
                                         })
                                         .orElse(0.5);
            double priority = mindmapPriority * (1 - driveWeight);

            CognitiveEmotion dominant = ge.emotions().stream()
                                          .max(Comparator.comparingDouble(CognitiveEmotion::intensity))
                                          .orElse(null);

            int surfacingCount = goal.property("surfaced-count")
                                     .map(v -> {
                                         try {return Integer.parseInt(v);} catch (NumberFormatException e) {return 0;}
                                     })
                                     .orElse(0);

            return new UnifiedGoal(goal.name(), priority, dominant, null, surfacingCount);
        }
    }
}
