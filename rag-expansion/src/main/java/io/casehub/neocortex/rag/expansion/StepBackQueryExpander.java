package io.casehub.neocortex.rag.expansion;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.neocortex.rag.QueryExpander;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
@IfBuildProperty(name = "casehub.rag.expansion.mode", stringValue = "step-back")
public class StepBackQueryExpander implements QueryExpander {

    private static final Duration TIMEOUT = Duration.ofMinutes(1);

    static final String DEFAULT_SYSTEM_PROMPT =
        "Generate a more general, abstract version of the user's question "
        + "that captures the underlying concept. The step-back question should "
        + "be broader and help retrieve foundational knowledge. "
        + "Return only the step-back question, nothing else.";

    private final AgentProvider agentProvider;
    private final ExpansionConfig config;

    @Inject
    public StepBackQueryExpander(AgentProvider agentProvider, ExpansionConfig config) {
        this.agentProvider = agentProvider;
        this.config = config;
    }

    @Override
    public List<RetrievalQuery> expand(RetrievalQuery query) {
        String systemPrompt = config.stepBackPromptTemplate()
                .map(t -> t.contains("%s") ? t.formatted("the user's question") : t)
                .orElse(DEFAULT_SYSTEM_PROMPT);
        var events = agentProvider.invoke(AgentSessionConfig.of(systemPrompt, query.text(), TIMEOUT))
                .collect().asList()
                .await().atMost(TIMEOUT.plusSeconds(5));
        String stepBack = events.stream()
                .filter(AgentEvent.TextDelta.class::isInstance)
                .map(AgentEvent.TextDelta.class::cast)
                .map(AgentEvent.TextDelta::text)
                .collect(Collectors.joining());
        return List.of(
            query,
            RetrievalQuery.of(stepBack)
        );
    }
}
