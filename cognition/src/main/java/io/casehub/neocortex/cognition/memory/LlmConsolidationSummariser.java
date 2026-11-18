package io.casehub.neocortex.cognition.memory;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.ClearingPolicy;
import io.casehub.platform.agent.session.SessionPipeline;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LlmConsolidationSummariser implements ConsolidationSummariser {

    private static final Logger LOG = Logger.getLogger(LlmConsolidationSummariser.class.getName());

    private static final String SYSTEM_PROMPT = """
            You consolidate memory fragments into a single coherent memory.
            You receive multiple memory texts that describe related experiences.
            Produce a single concise paragraph that preserves the key facts, \
            entities, and relationships from all fragments. Do not add information \
            not present in the originals. Do not use bullet points. Write in the \
            same register as the input texts.""";

    private final SessionPipeline sessionPipeline;

    public LlmConsolidationSummariser(SessionPipeline sessionPipeline) {
        this.sessionPipeline = sessionPipeline;
    }

    @Override
    public String summarise(List<String> texts) {
        if (texts == null || texts.isEmpty()) return "";
        if (texts.size() == 1) return texts.getFirst();
        try {
            return invokeLlm(texts);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "LLM consolidation failed, returning empty", e);
            return "";
        }
    }

    private String invokeLlm(List<String> texts) {
        var userMessage = new StringBuilder("Consolidate these memory fragments:\n\n");
        for (int i = 0; i < texts.size(); i++) {
            userMessage.append("Fragment ").append(i + 1).append(":\n")
                    .append(texts.get(i)).append("\n\n");
        }

        var session = sessionPipeline.openSession(
                "consolidation-" + Thread.currentThread().threadId(),
                AgentSessionInit.of(SYSTEM_PROMPT),
                ClearingPolicy.EVERY_CALL);

        var response = new StringBuilder();
        session.query(userMessage.toString()).subscribe().asStream()
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .forEach(response::append);

        return response.isEmpty() ? "" : response.toString().strip();
    }
}
