package io.casehub.neocortex.rag.expansion;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.neocortex.rag.QueryExpander;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@ApplicationScoped
@IfBuildProperty(name = "casehub.rag.expansion.mode", stringValue = "llm")
public class LlmQueryExpander implements QueryExpander {

    private static final Duration TIMEOUT = Duration.ofMinutes(1);

    static final String DEFAULT_SYSTEM_PROMPT =
        "You are a document author. Write a short passage (3-5 sentences) "
            + "that would directly answer the user's question. Write as if the passage "
            + "comes from an authoritative document. Do not include the question itself.";

    private final AgentProvider agentProvider;
    private final ExpansionConfig config;

    @Inject
    public LlmQueryExpander(AgentProvider agentProvider, ExpansionConfig config) {
        this.agentProvider = agentProvider;
        this.config = config;
    }

    @Override
    public List<RetrievalQuery> expand(final RetrievalQuery query) {
        final int n = config.hypotheticalCount();
        final String systemPrompt = config.promptTemplate()
                .map(t -> t.contains("%s") ? t.formatted("the user's question") : t)
                .orElse(DEFAULT_SYSTEM_PROMPT);

        if (n == 1) {
            String hypothetical = agentProvider.invokeBlocking(
                    systemPrompt, query.text(), TIMEOUT).text();
            return List.of(query.withExpansion(hypothetical));
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<RetrievalQuery>> futures = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                futures.add(executor.submit(() -> {
                    String hypothetical = agentProvider.invokeBlocking(
                            systemPrompt, query.text(), TIMEOUT).text();
                    return query.withExpansion(hypothetical);
                }));
            }
            List<RetrievalQuery> expansions = new ArrayList<>(n);
            for (var future : futures) {
                expansions.add(future.get());
            }
            return expansions;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
