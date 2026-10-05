package io.casehub.neocortex.cognition.appraisal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LlmAppraisalStrategy implements AppraisalStrategy {

    private static final Logger LOG = Logger.getLogger(LlmAppraisalStrategy.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a character's subconscious — the part that feels before thinking.

            You will receive:
            - A list of drives (what this character cares about, each with an intensity)
            - The current situation (what just happened)
            - The character's current mood (if available)

            For each drive, consider: does this situation touch it? How? What does it \
            make the character want to do? Consider ALL drives, not just the strongest one.

            Respond with JSON only:
            {
              "narrative": "2-3 sentences in first person. Gut reaction, internal sensation, \
            not analysis. Include ALL activated drives.",
              "dimensions": {
                "relevance": <0.0-1.0, how much this situation matters to the character>,
                "conduciveness": <-1.0 to 1.0, does it advance or block goals>,
                "controllability": <0.0-1.0, can the character influence the outcome>,
                "novelty": <0.0-1.0, how new/surprising is this>,
                "internal-standards": <0.0-1.0, does this align with self-standards (1.0=fine)>,
                "external-standards": <0.0-1.0, does this align with social norms (1.0=fine)>
              }
            }""";

    private final AgentProvider agentProvider;

    public LlmAppraisalStrategy(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public AppraisalResult appraise(AppraisalContext context) {
        try {
            String response = invokeLlm(context);
            if (response == null) return AppraisalResult.empty();
            return parseResponse(response, context);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM appraisal failed, returning empty", e);
            return AppraisalResult.empty();
        }
    }

    private AppraisalResult parseResponse(String response, AppraisalContext context) throws Exception {
        var root = MAPPER.readTree(response);

        String narrative = root.has("narrative") ? root.get("narrative").asText(null) : null;

        var dimsNode = root.path("dimensions");
        if (dimsNode.isMissingNode() || !dimsNode.isObject()) {
            return new AppraisalResult(List.of(), List.of(),
                    updateHabituation(context, 1.0), narrative);
        }

        var dims = parseDimensions(dimsNode);
        var secResults = List.of(new SecResult("llm-appraisal", dims));

        String subjectId = context.situation().narrative();
        var emotions = EmotionMapper.mapEmotions(secResults, subjectId);
        var tendencies = EmotionMapper.mapTendencies(secResults);

        double novelty = dims.getOrDefault(SecDimensions.NOVELTY, 1.0);
        var habituation = updateHabituation(context, novelty);

        return new AppraisalResult(emotions, tendencies, habituation, narrative);
    }

    private static HabituationState updateHabituation(AppraisalContext context, double novelty) {
        var hash = Integer.toHexString(context.situation().narrative().hashCode());
        return context.habituation() != null
                ? context.habituation().withObservation(hash, novelty)
                : HabituationState.empty().withObservation(hash, novelty);
    }

    private static Map<String, Double> parseDimensions(JsonNode dimsNode) {
        var dims = new HashMap<String, Double>();
        dimsNode.fields().forEachRemaining(entry -> {
            if (entry.getValue().isNumber()) {
                dims.put(entry.getKey(), clamp(entry.getValue().asDouble()));
            }
        });
        return dims;
    }

    private String invokeLlm(AppraisalContext context) {
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

    private static String buildUserMessage(AppraisalContext context) {
        var sb = new StringBuilder();

        sb.append("Drives:\n");
        for (var drive : context.drives()) {
            sb.append("- ").append(drive.name()).append(": ").append(drive.intensity()).append("\n");
        }

        sb.append("\nSituation:\n").append(context.situation().narrative());

        if (context.currentMood() != null) {
            var m = context.currentMood();
            sb.append("\n\nCurrent mood: pleasure=").append(String.format("%.2f", m.pleasure()))
                    .append(", arousal=").append(String.format("%.2f", m.arousal()))
                    .append(", dominance=").append(String.format("%.2f", m.dominance()));
        }

        return sb.toString();
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }
}
