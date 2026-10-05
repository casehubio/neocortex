package io.casehub.neocortex.cognition.appraisal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LlmCopingCheck implements SecCheck {

    private static final Logger LOG = Logger.getLogger(LlmCopingCheck.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a cognitive appraisal evaluator. Assess a situation on two dimensions:

            1. Controllability: How much control does the agent have over this situation?
               0.0 = completely helpless, no options available
               0.5 = moderate control, some options exist
               1.0 = full control, agent can fully determine the outcome

            2. Adjustability: How well can the agent adapt to this situation?
               0.0 = cannot adapt at all, rigid constraints
               0.5 = can partially adapt with effort
               1.0 = highly flexible, many adaptation paths

            Consider implicit coping resources, not just explicit keywords. A locked door \
            with no key implies low controllability even without words like "helpless."

            Respond with JSON only: {"controllability": <number>, "adjustability": <number>}""";

    private static final SecResult NEUTRAL = new SecResult("llm-coping", Map.of(
            SecDimensions.CONTROLLABILITY, 0.5,
            SecDimensions.ADJUSTABILITY, 0.5));

    private final AgentProvider agentProvider;

    public LlmCopingCheck(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public SecResult evaluate(AppraisalContext context) {
        String observation = context.situation().narrative();
        try {
            String response = invokeLlm(observation);
            if (response == null) return NEUTRAL;

            var root = MAPPER.readTree(response);
            double controllability = clamp(root.path("controllability").asDouble(0.5));
            double adjustability = clamp(root.path("adjustability").asDouble(0.5));

            return new SecResult("llm-coping", Map.of(
                    SecDimensions.CONTROLLABILITY, controllability,
                    SecDimensions.ADJUSTABILITY, adjustability));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM coping check failed, returning neutral", e);
            return NEUTRAL;
        }
    }

    private String invokeLlm(String observation) {
        var events = agentProvider.invoke(
                        AgentSessionConfig.of(SYSTEM_PROMPT, "Situation: " + observation))
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

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
