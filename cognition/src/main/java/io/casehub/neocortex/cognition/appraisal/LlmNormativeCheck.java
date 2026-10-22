package io.casehub.neocortex.cognition.appraisal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.session.AgentSessionInit;
import io.casehub.platform.agent.session.SessionPipeline;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private final @Nullable SessionPipeline sessionPipeline;

    public LlmNormativeCheck(@Nullable SessionPipeline sessionPipeline) {
        this.sessionPipeline = sessionPipeline;
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
        var pipeline = this.sessionPipeline;
        if (pipeline == null) return null;
        var session = pipeline.openSession("llm-normative", AgentSessionInit.of(SYSTEM_PROMPT));
        var responseText = new StringBuilder();
        session.query("Situation: " + observation).subscribe().asStream()
            .filter(e -> e instanceof AgentEvent.TextDelta)
            .map(e -> ((AgentEvent.TextDelta) e).text())
            .forEach(responseText::append);
        return responseText.isEmpty() ? null : responseText.toString();
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
