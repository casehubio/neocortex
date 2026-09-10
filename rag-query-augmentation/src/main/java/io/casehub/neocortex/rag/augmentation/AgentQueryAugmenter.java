package io.casehub.neocortex.rag.augmentation;

import io.casehub.neocortex.rag.DocumentQueryAugmenter;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class AgentQueryAugmenter implements DocumentQueryAugmenter {

    private static final Logger LOG = Logger.getLogger(AgentQueryAugmenter.class.getName());
    private static final int MIN_CONTENT_LENGTH = 50;

    private static final String SYSTEM_PROMPT = """
        You are a search query generator. Given a document title and body,
        generate 3-5 search queries that someone might type when looking for
        this document. Return ONLY the queries, one per line, no numbering,
        no explanations.""";

    private final Instance<AgentProvider> agentProviderInstance;

    public AgentQueryAugmenter(Instance<AgentProvider> agentProviderInstance) {
        this.agentProviderInstance = agentProviderInstance;
    }

    @Override
    public Optional<List<String>> generateQueries(String title, String body, String path) {
        if (agentProviderInstance.isUnsatisfied()) return Optional.empty();

        String content = (title != null ? title : "") + "\n" + (body != null ? body : "");
        if (content.trim().length() < MIN_CONTENT_LENGTH) return Optional.empty();

        String userPrompt = "Title: " + (title != null ? title : "(untitled)") + "\n\nBody:\n" +
            (body != null ? body.substring(0, Math.min(body.length(), 2000)) : "");

        try {
            AgentProvider provider = agentProviderInstance.get();
            var events = provider.invoke(AgentSessionConfig.of(SYSTEM_PROMPT, userPrompt))
                .collect().asList()
                .await().atMost(Duration.ofMinutes(1));

            boolean hasError = events.stream()
                .filter(AgentEvent.InvocationComplete.class::isInstance)
                .map(AgentEvent.InvocationComplete.class::cast)
                .anyMatch(AgentEvent.InvocationComplete::isError);
            if (hasError) {
                LOG.warning("Query generation LLM invocation completed with error");
                return Optional.of(List.of());
            }

            String text = events.stream()
                .filter(AgentEvent.TextDelta.class::isInstance)
                .map(AgentEvent.TextDelta.class::cast)
                .map(AgentEvent.TextDelta::text)
                .collect(Collectors.joining());

            if (text.isBlank()) return Optional.of(List.of());

            List<String> queries = Arrays.stream(text.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.matches("^\\d+\\..*"))
                .toList();

            return Optional.of(queries);
        } catch (Exception e) {
            LOG.warning("Query generation failed: " + e.getMessage());
            return Optional.of(List.of());
        }
    }
}
