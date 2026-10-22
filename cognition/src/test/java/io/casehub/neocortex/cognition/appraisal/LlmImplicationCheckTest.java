package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognition.belief.StubPipelines;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class LlmImplicationCheckTest {

    private LlmImplicationCheck checkWith(Map<String, String> responses) {
        var check = new LlmImplicationCheck(StubPipelines.create(init -> new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) {
                for (var entry : responses.entrySet()) {
                    if (prompt.contains(entry.getKey())) {
                        return Multi.createFrom().item(new AgentEvent.TextDelta(entry.getValue()));
                    }
                }
                return Multi.createFrom().item(new AgentEvent.TextDelta("{\"conduciveness\": 0.0}"));
            }
            @Override public io.smallrye.mutiny.Uni<Void> interrupt() { return io.smallrye.mutiny.Uni.createFrom().voidItem(); }
            @Override public void close(java.time.Duration timeout) {}
        }));
        return check;
    }

    private AppraisalContext contextFor(String observation) {
        return new AppraisalContext(
                PerceivedSituation.passThrough(observation),
                List.of(), AppraisalWeights.NEUTRAL,
                HabituationConfig.defaults(), HabituationState.empty(), null);
    }

    @Test
    void positiveEvent_returnsPositiveConduciveness() {
        var check = checkWith(Map.of(
                "solved", "{\"relevance\": 0.9, \"conduciveness\": 0.8}"));
        var result = check.evaluate(contextFor("I just solved the problem"));
        assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isCloseTo(0.8, offset(0.01));
        assertThat(result.dimension(SecDimensions.RELEVANCE)).isCloseTo(0.9, offset(0.01));
    }

    @Test
    void negativeEvent_returnsNegativeConduciveness() {
        var check = checkWith(Map.of(
                "failed", "{\"relevance\": 0.85, \"conduciveness\": -0.7}"));
        var result = check.evaluate(contextFor("The deployment failed completely"));
        assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isCloseTo(-0.7, offset(0.01));
        assertThat(result.dimension(SecDimensions.RELEVANCE)).isCloseTo(0.85, offset(0.01));
    }

    @Test
    void neutralEvent_returnsNearZero() {
        var check = checkWith(Map.of(
                "standup", "{\"relevance\": 0.15, \"conduciveness\": 0.05}"));
        var result = check.evaluate(contextFor("A routine standup meeting"));
        assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isCloseTo(0.05, offset(0.01));
        assertThat(result.dimension(SecDimensions.RELEVANCE)).isCloseTo(0.15, offset(0.01));
    }

    @Test
    void fallsBackToNeutralOnError() {
        var check = new LlmImplicationCheck(StubPipelines.create(init -> new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }
            @Override public io.smallrye.mutiny.Uni<Void> interrupt() { return io.smallrye.mutiny.Uni.createFrom().voidItem(); }
            @Override public void close(java.time.Duration timeout) {}
        }));
        var result = check.evaluate(contextFor("anything"));
        assertThat(result.dimension(SecDimensions.CONDUCIVENESS)).isCloseTo(0.0, offset(0.01));
    }
}
