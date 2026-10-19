package io.casehub.neocortex.rag.expansion;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class LlmQueryExpanderTest {

    @Test
    void expandCallsChatModelWithDefaultPrompt() {
        var capturedPrompt = new String[1];
        AgentProvider mockProvider = stubAgentProvider(prompt -> {
            capturedPrompt[0] = prompt;
            return "Diabetes is a chronic condition characterized by elevated blood sugar.";
        });

        var expander = new LlmQueryExpander(mockProvider, stubConfig(Optional.empty()));
        var results = expander.expand(RetrievalQuery.of("what is diabetes?"));

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.text()).isEqualTo("what is diabetes?");
        assertThat(result.expandedText()).isEqualTo(
            "Diabetes is a chronic condition characterized by elevated blood sugar.");
        assertThat(capturedPrompt[0]).isEqualTo("what is diabetes?");
    }

    @Test
    void expandUsesCustomPromptTemplate() {
        AgentProvider mockProvider = stubAgentProvider(prompt -> "custom response");
        var expander = new LlmQueryExpander(mockProvider,
            stubConfig(Optional.of("Custom: %s")));
        var results = expander.expand(RetrievalQuery.of("test query"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).expandedText()).isEqualTo("custom response");
    }

    @Test
    void expandPreservesOriginalText() {
        AgentProvider mockProvider = stubAgentProvider(prompt -> "hypothetical passage");
        var expander = new LlmQueryExpander(mockProvider, stubConfig(Optional.empty()));

        var query = RetrievalQuery.of("my question");
        var results = expander.expand(query);

        assertThat(results).hasSize(1);
        var result = results.get(0);
        assertThat(result.text()).isEqualTo("my question");
        assertThat(result.searchText()).isEqualTo("hypothetical passage");
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
        };
    }

    @Test
    void expandReturnsMultipleHypotheticals() {
        var callCount = new AtomicInteger();
        AgentProvider provider = stubAgentProvider(prompt -> "hypothetical " + callCount.incrementAndGet());
        var expander = new LlmQueryExpander(provider, stubConfig(Optional.empty(), 3));
        var result = expander.expand(RetrievalQuery.of("test query"));

        assertThat(result).hasSize(3);
        assertThat(result).allSatisfy(q -> {
            assertThat(q.text()).isEqualTo("test query");
            assertThat(q.expandedText()).startsWith("hypothetical ");
        });
        assertThat(result).extracting(r -> r.expandedText())
            .containsExactlyInAnyOrder("hypothetical 1", "hypothetical 2", "hypothetical 3");
    }

    @Test
    void expandDefaultCountIsOne() {
        AgentProvider provider = stubAgentProvider(prompt -> "single hypothetical");
        var expander = new LlmQueryExpander(provider, stubConfig(Optional.empty(), 1));
        var result = expander.expand(RetrievalQuery.of("test"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).expandedText()).isEqualTo("single hypothetical");
    }

    @Test
    void expandRunsConcurrentlyWhenCountGreaterThanOne() throws Exception {
        final int n = 3;
        var allStarted = new CountDownLatch(n);
        var proceed = new CountDownLatch(1);

        AgentProvider provider = stubAgentProvider(prompt -> {
            allStarted.countDown();
            try {
                proceed.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "hypothetical";
        });

        var expander = new LlmQueryExpander(provider, stubConfig(Optional.empty(), n));

        var future = CompletableFuture.supplyAsync(() ->
            expander.expand(RetrievalQuery.of("test")));

        assertThat(allStarted.await(5, TimeUnit.SECONDS))
            .as("All %d calls should start concurrently", n)
            .isTrue();

        proceed.countDown();

        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result).hasSize(n);
        assertThat(result).allSatisfy(q ->
            assertThat(q.expandedText()).isEqualTo("hypothetical"));
    }

    private static ExpansionConfig stubConfig(Optional<String> promptTemplate, int hypotheticalCount) {
        return new ExpansionConfig() {
            @Override public boolean enabled() { return true; }
            @Override public Optional<String> mode() { return Optional.of("llm"); }
            @Override public int hypotheticalCount() { return hypotheticalCount; }
            @Override public Optional<String> promptTemplate() { return promptTemplate; }
            @Override public Optional<String> template() { return Optional.empty(); }
            @Override public Optional<String> stepBackPromptTemplate() { return Optional.empty(); }
            @Override public DriftConfig drift() { return new DriftConfig() { @Override public boolean enabled() { return false; } @Override public double threshold() { return 0.7; } @Override public DriftAction action() { return DriftAction.OBSERVE; } }; }
        };
    }

    private static ExpansionConfig stubConfig(Optional<String> promptTemplate) {
        return stubConfig(promptTemplate, 1);
    }
}
