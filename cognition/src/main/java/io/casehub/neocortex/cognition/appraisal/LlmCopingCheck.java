package io.casehub.neocortex.cognition.appraisal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.SessionPipeline;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private final @Nullable SessionPipeline sessionPipeline;

    public LlmCopingCheck(@Nullable SessionPipeline sessionPipeline) {
        this.sessionPipeline = sessionPipeline;
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
        var pipeline = this.sessionPipeline;
        if (pipeline == null) return null;
        var session = pipeline.openSession("llm-coping", AgentSessionInit.of(SYSTEM_PROMPT));
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
