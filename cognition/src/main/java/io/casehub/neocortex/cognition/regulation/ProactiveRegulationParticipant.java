package io.casehub.neocortex.cognition.regulation;

import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.core.CognitionTickParticipant;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.neocortex.cognition.goal.DriveGoalProposal;
import io.casehub.neocortex.cognition.goal.GoalProposalOrchestrator;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ProactiveRegulationParticipant implements CognitionTickParticipant {

    private final MindMapStore mindMapStore;
    private final GoalProposalOrchestrator goalOrchestrator;
    private final CognitiveDefaultsRegistry defaultsRegistry;
    private final String cognitiveSubgraphId;
    private final ProactiveAvoidanceMode mode;
    private final int decayHorizonTicks;
    private final double tickIntervalSeconds;

    public ProactiveRegulationParticipant(MindMapStore mindMapStore,
                                          GoalProposalOrchestrator goalOrchestrator,
                                          CognitiveDefaultsRegistry defaultsRegistry,
                                          String cognitiveSubgraphId,
                                          ProactiveAvoidanceMode mode,
                                          int decayHorizonTicks,
                                          double tickIntervalSeconds) {
        this.mindMapStore = mindMapStore;
        this.goalOrchestrator = goalOrchestrator;
        this.defaultsRegistry = defaultsRegistry;
        this.cognitiveSubgraphId = cognitiveSubgraphId;
        this.mode = mode;
        this.decayHorizonTicks = decayHorizonTicks;
        this.tickIntervalSeconds = tickIntervalSeconds;
    }

    @Override
    public void tick(CognitionTickContext context) {
        var regCtx = context.regulationContext();
        if (regCtx == null || regCtx.location() == null) return;

        var defaults = defaultsRegistry.forAgent(context.agentId());
        if (defaults.isEmpty()) return;

        var rd = defaults.get().regulationDefaults();
        if (rd == null) return;

        double threshold = 1.0 - rd.redirectTendency();
        var now = Instant.now();

        var nodes = mindMapStore.search(
                MindMapQuery.of(context.tenantId(), 200)
                            .withType(SubgraphTypes.COGNITIVE));

        List<DriveGoalProposal> avoidanceGoals = new ArrayList<>();

        for (var node : nodes) {
            var props = node.properties();
            if (!EmotionRegulationMindMapAdapter.COGNITIVE_KIND.equals(props.get("cognitiveKind"))) continue;
            if (!context.agentId().equals(props.get("agent-id"))) continue;

            String episodeLocation = props.get("regulationLocation");
            if (!regCtx.location().equalsIgnoreCase(episodeLocation)) continue;

            if (mode == ProactiveAvoidanceMode.LOCATION_AND_AGENTS || mode == ProactiveAvoidanceMode.FULL) {
                String episodeAgents = props.get("regulationAgents");
                if (regCtx.interactingAgents() != null && episodeAgents != null) {
                    var episodeAgentList = List.of(episodeAgents.split(","));
                    if (regCtx.interactingAgents().stream().noneMatch(episodeAgentList::contains)) continue;
                }
            }

            String timestampStr = props.get("timestamp");
            if (timestampStr == null) continue;
            Instant episodeTime = Instant.parse(timestampStr);
            double elapsedTicks = Duration.between(episodeTime, now).toSeconds() / tickIntervalSeconds;
            double decayWeight = 1.0 - (elapsedTicks / decayHorizonTicks);
            if (decayWeight <= 0) continue;

            double effectiveness = parseDouble(props.get("effectiveness"));
            double avoidanceScore = (1.0 - effectiveness) * decayWeight;
            if (avoidanceScore <= threshold) continue;

            String emotionType = props.getOrDefault("dominantEmotionType", "UNKNOWN");
            String axisStr = props.get("regulationAxis");
            DriveAxis axis = DriveAxis.AUTONOMY;
            if (axisStr != null) {
                try { axis = DriveAxis.valueOf(axisStr); }
                catch (IllegalArgumentException ignored) {}
            }

            avoidanceGoals.add(new DriveGoalProposal(
                    axis,
                    "proactive-avoidance-" + regCtx.location().toLowerCase() + "-" + emotionType.toLowerCase(),
                    "Avoid " + regCtx.location() + " — previous emotional distress (" + emotionType + ")",
                    "Proactive regulation: memory-based anticipatory avoidance",
                    avoidanceScore));
        }

        if (!avoidanceGoals.isEmpty()) {
            goalOrchestrator.registerParticipantGoals("proactive-regulation", context.agentId(), context.tenantId(), avoidanceGoals);
        }
    }

    private static double parseDouble(String value) {
        if (value == null) return 0.0;
        try { return Double.parseDouble(value); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
