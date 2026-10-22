package io.casehub.neocortex.cognition.appraisal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.session.AgentSessionInit;
import io.casehub.platform.agent.session.SessionPipeline;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LlmImplicationCheck implements SecCheck {

    private static final Logger LOG = Logger.getLogger(LlmImplicationCheck.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
                                                You are a cognitive appraisal evaluator. Assess a situation on two dimensions:
                                                
                                                1. Relevance: How much does this situation matter to the agent?
                                                   0.0 = completely irrelevant, no personal stake
                                                   0.5 = moderately relevant
                                                   1.0 = highly relevant, directly impacts the agent's goals or wellbeing
                                                
                                                2. Conduciveness: How much does this situation advance or block the agent's goals?
                                                   -1.0 = completely blocks goals, catastrophic for objectives
                                                   0.0 = neutral, no impact on goals
                                                   1.0 = fully advances goals, ideal outcome
                                                
                                                Consider the overall goal impact, not just whether the text contains positive
                                                or negative words. "The deadline was moved up" is negative (blocks goals) even
                                                though no single word is negative. A routine meeting with no stakes is low relevance.
                                                
                                                Respond with JSON only: {"relevance": <number>, "conduciveness": <number>}""";

    private static final SecResult NEUTRAL = new SecResult("llm-implication",
                                                           Map.of(SecDimensions.RELEVANCE, 0.0, SecDimensions.CONDUCIVENESS, 0.0));

    private final @Nullable SessionPipeline sessionPipeline;

    public LlmImplicationCheck(@Nullable SessionPipeline sessionPipeline) {
        this.sessionPipeline = sessionPipeline;
    }

    @Override
    public SecResult evaluate(AppraisalContext context) {
        String observation = context.situation().narrative();
        try {
            String response = invokeLlm(observation);
            if (response == null) {return NEUTRAL;}

            var    root          = MAPPER.readTree(response);
            double relevance     = clampPositive(root.path("relevance").asDouble(0.0));
            double conduciveness = clamp(root.path("conduciveness").asDouble(0.0));

            return new SecResult("llm-implication", Map.of(
                    SecDimensions.RELEVANCE, relevance,
                    SecDimensions.CONDUCIVENESS, conduciveness));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM implication check failed, returning neutral", e);
            return NEUTRAL;
        }
    }

    private String invokeLlm(String observation) {
        var pipeline = this.sessionPipeline;
        if (pipeline == null) return null;
        var session = pipeline.openSession("llm-implication", AgentSessionInit.of(SYSTEM_PROMPT));
        var responseText = new StringBuilder();
        session.query("Situation: " + observation).subscribe().asStream()
            .filter(e -> e instanceof AgentEvent.TextDelta)
            .map(e -> ((AgentEvent.TextDelta) e).text())
            .forEach(responseText::append);
        return responseText.isEmpty() ? null : responseText.toString();
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }

    private static double clampPositive(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

}
