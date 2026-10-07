package io.casehub.neocortex.cognition.goal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.cognition.drive.DriveAxis;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LlmDeductiveGoalFormationStrategy implements DeductiveGoalFormationStrategy {

    private static final Logger LOG = Logger.getLogger(LlmDeductiveGoalFormationStrategy.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a character's subconscious goal formation process — the part that \
            decides what to pursue based on who they are and what they know.

            You will receive the character's personality disposition, active drives, \
            beliefs, recent memories, current mood, and existing goals.

            Reason about what goals this character would naturally form given their \
            full cognitive state. Consider:
            - What do their drives push them toward?
            - What do their beliefs and memories make possible or desirable?
            - How does their personality shape what they would pursue?
            - What does their mood incline them toward?

            Only propose goals that are NOVEL — not already in the existing goals list. \
            Each goal should emerge from the intersection of multiple inputs, not just \
            a single drive.

            Respond with a JSON array only:
            [
              {
                "goalName": "short-kebab-case-identifier",
                "description": "what the goal is, in 1-2 sentences",
                "reasoning": "why this character would form this goal, referencing \
            specific inputs (drives, beliefs, personality)",
                "driveContributions": { "AXIS_NAME": 0.0-1.0, ... }
              }
            ]

            Return an empty array [] if no novel goals emerge.""";

    private final AgentProvider agentProvider;

    public LlmDeductiveGoalFormationStrategy(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public List<DeductiveGoalProposal> propose(DeductiveFormationContext context) {
        try {
            String response = invokeLlm(context);
            if (response == null) return List.of();
            return parseResponse(response);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Deductive goal formation failed, returning empty", e);
            return List.of();
        }
    }

    private List<DeductiveGoalProposal> parseResponse(String response) throws Exception {
        var root = MAPPER.readTree(response);
        if (!root.isArray()) return List.of();

        List<DeductiveGoalProposal> proposals = new ArrayList<>();
        for (JsonNode node : root) {
            var goalName = node.path("goalName").asText(null);
            var description = node.path("description").asText(null);
            var reasoning = node.path("reasoning").asText(null);
            if (goalName == null || description == null || reasoning == null) continue;

            Map<DriveAxis, Double> contributions = new HashMap<>();
            var contribNode = node.path("driveContributions");
            if (contribNode.isObject()) {
                contribNode.fields().forEachRemaining(entry -> {
                    try {
                        var axis = DriveAxis.valueOf(entry.getKey());
                        if (entry.getValue().isNumber()) {
                            contributions.put(axis, clamp(entry.getValue().asDouble()));
                        }
                    } catch (IllegalArgumentException ignored) {}
                });
            }

            proposals.add(new DeductiveGoalProposal(
                    goalName, description, reasoning,
                    contributions, null, null));
        }
        return proposals;
    }

    private String invokeLlm(DeductiveFormationContext context) {
        String userMessage = buildUserMessage(context);

        var events = agentProvider.invoke(
                        AgentSessionConfig.of(SYSTEM_PROMPT, userMessage))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(1));

        boolean hasError = events.stream()
                .filter(AgentEvent.InvocationComplete.class::isInstance)
                .map(AgentEvent.InvocationComplete.class::cast)
                .anyMatch(AgentEvent.InvocationComplete::isError);
        if (hasError) return null;

        String text = events.stream()
                .filter(AgentEvent.TextDelta.class::isInstance)
                .map(AgentEvent.TextDelta.class::cast)
                .map(AgentEvent.TextDelta::text)
                .collect(Collectors.joining());

        return text.isEmpty() ? null : text;
    }

    private static String buildUserMessage(DeductiveFormationContext context) {
        var sb = new StringBuilder();

        sb.append("Drives:\n");
        context.driveProfile().drives().forEach((axis, intensity) ->
                sb.append("- ").append(axis.name()).append(": ")
                        .append(String.format("%.2f", intensity.intensity()))
                        .append(" (").append(intensity.trigger()).append(")\n"));

        if (context.disposition() != null) {
            var d = context.disposition();
            sb.append("\nDisposition:");
            if (d.socialOrient() != null) sb.append("\n- Social orientation: ").append(d.socialOrient());
            if (d.ruleFollowing() != null) sb.append("\n- Rule following: ").append(d.ruleFollowing());
            if (d.riskAppetite() != null) sb.append("\n- Risk appetite: ").append(d.riskAppetite());
            if (d.autonomy() != null) sb.append("\n- Autonomy: ").append(d.autonomy());
            if (d.conflictMode() != null) sb.append("\n- Conflict mode: ").append(d.conflictMode());
        }

        if (!context.beliefs().isEmpty()) {
            sb.append("\n\nBeliefs:\n");
            context.beliefs().forEach(b -> sb.append("- ").append(b).append("\n"));
        }

        if (!context.recentMemories().isEmpty()) {
            sb.append("\nRecent memories:\n");
            context.recentMemories().forEach(m -> sb.append("- ").append(m).append("\n"));
        }

        if (context.currentMood() != null) {
            var m = context.currentMood();
            sb.append("\nCurrent mood: pleasure=").append(String.format("%.2f", m.pleasure()))
                    .append(", arousal=").append(String.format("%.2f", m.arousal()))
                    .append(", dominance=").append(String.format("%.2f", m.dominance()));
        }

        if (!context.existingGoals().isEmpty()) {
            sb.append("\n\nExisting goals (do NOT re-propose):\n");
            context.existingGoals().forEach(g -> sb.append("- ").append(g.name()).append("\n"));
        }

        sb.append("\nRemaining goal capacity: ").append(context.remainingCapacity());

        return sb.toString();
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
