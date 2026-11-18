package io.casehub.neocortex.cognition.memory;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.ClearingPolicy;
import io.casehub.platform.agent.session.ManagedSession;
import io.casehub.platform.agent.session.SessionPipeline;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmConsolidationSummariserTest {

    private SessionPipeline mockPipeline() {
        var pipeline = mock(SessionPipeline.class);
        var session = mock(ManagedSession.class);
        when(pipeline.openSession(anyString(), any(AgentSessionInit.class), any(ClearingPolicy.class)))
                .thenReturn(session);
        when(session.query(anyString())).thenReturn(
                Multi.createFrom().items(
                        new AgentEvent.TextDelta("Consolidated memory about the incident.")));
        return pipeline;
    }

    @Test
    void summarisesMultipleTexts() {
        var summariser = new LlmConsolidationSummariser(mockPipeline());
        var result = summariser.summarise(List.of(
                "An emergency alert was raised at 3pm.",
                "The alert was resolved by the on-call team."));
        assertThat(result).isEqualTo("Consolidated memory about the incident.");
    }

    @Test
    void singleTextReturnedAsIs() {
        var summariser = new LlmConsolidationSummariser(mockPipeline());
        assertThat(summariser.summarise(List.of("single memory"))).isEqualTo("single memory");
    }

    @Test
    void emptyListReturnsEmpty() {
        var summariser = new LlmConsolidationSummariser(mockPipeline());
        assertThat(summariser.summarise(List.of())).isEmpty();
    }

    @Test
    void nullListReturnsEmpty() {
        var summariser = new LlmConsolidationSummariser(mockPipeline());
        assertThat(summariser.summarise(null)).isEmpty();
    }

    @Test
    void llmFailureReturnsEmpty() {
        var pipeline = mock(SessionPipeline.class);
        when(pipeline.openSession(anyString(), any(AgentSessionInit.class), any(ClearingPolicy.class)))
                .thenThrow(new RuntimeException("LLM unavailable"));
        var summariser = new LlmConsolidationSummariser(pipeline);
        assertThat(summariser.summarise(List.of("text1", "text2"))).isEmpty();
    }
}
