package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class LlmImplicationCheckTest {

    private static final AgentEvent.InvocationComplete COMPLETE =
            new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 0L, 0L, null, 0, false);

    private AgentProvider mockProvider(Map<String, String> responses) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                String prompt = config.userPrompt();
                for (var entry : responses.entrySet()) {
                    if (prompt.contains(entry.getKey())) {
                        return Multi.createFrom().items(
                                new AgentEvent.TextDelta(entry.getValue()), COMPLETE);
                    }
                }
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta("{\"conduciveness\": 0.0}"), COMPLETE);
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private AppraisalContext contextFor(String observation) {
        return new AppraisalContext(
                PerceivedSituation.passThrough(observation),
                List.of(), AppraisalWeights.NEUTRAL,
                HabituationConfig.defaults(), HabituationState.empty(), null);
    }

    @Test
    void positiveEvent_returnsPositiveConduciveness() {
        var check = new LlmImplicationCheck(mockProvider(Map.of(
                "solved", "{\"relevance\": 0.9, \"conduciveness\": 0.8}")));
        var result = check.evaluate(contextFor("I just solved the problem"));
        assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isCloseTo(0.8, offset(0.01));
        assertThat(result.dimension(SecDimensions.RELEVANCE)).isCloseTo(0.9, offset(0.01));
    }

    @Test
    void negativeEvent_returnsNegativeConduciveness() {
        var check = new LlmImplicationCheck(mockProvider(Map.of(
                "failed", "{\"relevance\": 0.85, \"conduciveness\": -0.7}")));
        var result = check.evaluate(contextFor("The deployment failed completely"));
        assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isCloseTo(-0.7, offset(0.01));
        assertThat(result.dimension(SecDimensions.RELEVANCE)).isCloseTo(0.85, offset(0.01));
    }

    @Test
    void neutralEvent_returnsNearZero() {
        var check = new LlmImplicationCheck(mockProvider(Map.of(
                "standup", "{\"relevance\": 0.15, \"conduciveness\": 0.05}")));
        var result = check.evaluate(contextFor("A routine standup meeting"));
        assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isCloseTo(0.05, offset(0.01));
        assertThat(result.dimension(SecDimensions.RELEVANCE)).isCloseTo(0.15, offset(0.01));
    }

    @Test
    void fallsBackToNeutralOnError() {
        AgentProvider failingProvider = new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
        var check = new LlmImplicationCheck(failingProvider);
        var result = check.evaluate(contextFor("anything"));
        assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isCloseTo(0.0, offset(0.01));
    }
}
