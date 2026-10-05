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

public class LlmNormativeCheck implements SecCheck {

    private static final Logger LOG = Logger.getLogger(LlmNormativeCheck.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            You are a cognitive appraisal evaluator. Assess a situation on two norm dimensions:

            1. Internal standards: Does this situation align with the agent's personal values,
               professional ethics, and self-expectations?
               0.0 = severe violation of personal standards (e.g., compromising integrity)
               0.5 = minor tension with personal standards
               1.0 = fully consistent with personal standards

            2. External standards: Does this situation comply with social norms, contractual
               obligations, team agreements, and institutional rules?
               0.0 = severe violation of external standards (e.g., breaking SLA, legal breach)
               0.5 = minor non-compliance
               1.0 = fully compliant with external standards

            Assess the situation itself, not the agent's response to it. "Someone ignoring
            code review" is an external standards violation even if the word "violation"
            doesn't appear.

            Respond with JSON only: {"internal_standards": <number>, "external_standards": <number>}""";

    private static final SecResult COMPLIANT = new SecResult("llm-normative", Map.of(
            SecDimensions.INTERNAL_STANDARDS, 1.0,
            SecDimensions.EXTERNAL_STANDARDS, 1.0));

    private final AgentProvider agentProvider;

    public LlmNormativeCheck(AgentProvider agentProvider) {
        this.agentProvider = agentProvider;
    }

    @Override
    public SecResult evaluate(AppraisalContext context) {
        String observation = context.situation().narrative();
        try {
            String response = invokeLlm(observation);
            if (response == null) return COMPLIANT;

            var root = MAPPER.readTree(response);
            double internal = clamp(root.path("internal_standards").asDouble(1.0));
            double external = clamp(root.path("external_standards").asDouble(1.0));

            return new SecResult("llm-normative", Map.of(
                    SecDimensions.INTERNAL_STANDARDS, internal,
                    SecDimensions.EXTERNAL_STANDARDS, external));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM normative check failed, returning compliant", e);
            return COMPLIANT;
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
