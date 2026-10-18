package io.casehub.neocortex.rag.expansion;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentInvocationResult;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentEvent;
import io.smallrye.mutiny.Multi;
import io.casehub.neocortex.rag.RetrievalQuery;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class StepBackQueryExpanderTest {

    @Test
    void expandReturnsOriginalAndAbstract() {
        AgentProvider provider = stubAgentProvider(prompt -> "What are NSAIDs?");
        var expander = new StepBackQueryExpander(provider, stubConfig(Optional.empty()));
        var result = expander.expand(RetrievalQuery.of("side effects of ibuprofen for liver patients"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).text()).isEqualTo("side effects of ibuprofen for liver patients");
        assertThat(result.get(0).expandedText()).isNull();
        assertThat(result.get(1).text()).isEqualTo("What are NSAIDs?");
    }

    @Test
    void expandUsesDefaultPromptTemplate() {
        var capturedPrompt = new String[1];
        AgentProvider provider = stubAgentProvider(prompt -> {
            capturedPrompt[0] = prompt;
            return "abstract question";
        });
        var expander = new StepBackQueryExpander(provider, stubConfig(Optional.empty()));
        expander.expand(RetrievalQuery.of("test query"));

        assertThat(capturedPrompt[0]).isEqualTo("test query");
    }

    @Test
    void expandUsesCustomPromptTemplate() {
        AgentProvider provider = stubAgentProvider(prompt -> "custom abstract");
        var expander = new StepBackQueryExpander(provider,
            stubConfig(Optional.of("Step back from: %s")));
        var result = expander.expand(RetrievalQuery.of("specific question"));

        assertThat(result).hasSize(2);
        assertThat(result.get(1).text()).isEqualTo("custom abstract");
    }

    @Test
    void expandPreservesOriginalExpansion() {
        AgentProvider provider = stubAgentProvider(prompt -> "abstract");
        var expander = new StepBackQueryExpander(provider, stubConfig(Optional.empty()));
        var query = new RetrievalQuery("original", "prior expansion", java.util.Map.of());
        var result = expander.expand(query);

        assertThat(result.get(0).text()).isEqualTo("original");
        assertThat(result.get(0).expandedText()).isEqualTo("prior expansion");
        assertThat(result.get(1).text()).isEqualTo("abstract");
    }

    private static AgentProvider stubAgentProvider(Function<String, String> handler) {
        return new AgentProvider() {
            @Override public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                String response = handler.apply(config.userPrompt());
                return Multi.createFrom().items(new AgentEvent.TextDelta(response));
            }
            @Override public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
            @Override public AgentInvocationResult invokeBlocking(String systemPrompt, String userPrompt, java.time.Duration timeout) {
                String response = handler.apply(userPrompt);
                return new AgentInvocationResult(response, 0, 0, 0, 0, 0, null, 0L, null, null);
            }
        };
    }

    private static ExpansionConfig stubConfig(Optional<String> stepBackPromptTemplate) {
        return new ExpansionConfig() {
            @Override public boolean enabled() { return true; }
            @Override public Optional<String> mode() { return Optional.of("step-back"); }
            @Override public int hypotheticalCount() { return 1; }
            @Override public Optional<String> promptTemplate() { return Optional.empty(); }
            @Override public Optional<String> template() { return Optional.empty(); }
            @Override public Optional<String> stepBackPromptTemplate() { return stepBackPromptTemplate; }
            @Override public DriftConfig drift() { return new DriftConfig() { @Override public boolean enabled() { return false; } @Override public double threshold() { return 0.7; } @Override public DriftAction action() { return DriftAction.OBSERVE; } }; }
        };
    }
}
