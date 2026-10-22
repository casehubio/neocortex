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

class LlmNormativeCheckTest {

    private LlmNormativeCheck checkWith(Map<String, String> responses) {
        var check = new LlmNormativeCheck();
        check.setSessionPipeline(StubPipelines.create(init -> new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) {
                for (var entry : responses.entrySet()) {
                    if (prompt.contains(entry.getKey())) {
                        return Multi.createFrom().item(new AgentEvent.TextDelta(entry.getValue()));
                    }
                }
                return Multi.createFrom().item(new AgentEvent.TextDelta(
                        "{\"internal_standards\": 1.0, \"external_standards\": 1.0}"));
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
    void normViolation_returnsLowStandards() {
        var check = checkWith(Map.of(
                "ignoring the code review",
                "{\"internal_standards\": 0.7, \"external_standards\": 0.3}"));
        var result = check.evaluate(contextFor(
                "Someone on the team keeps ignoring the code review process"));
        assertThat(result.dimension(SecDimensions.EXTERNAL_STANDARDS))
                .isCloseTo(0.3, offset(0.01));
    }

    @Test
    void slaViolation_returnsLowBothStandards() {
        var check = checkWith(Map.of(
                "violates our SLA",
                "{\"internal_standards\": 0.35, \"external_standards\": 0.2}"));
        var result = check.evaluate(contextFor(
                "The client reported a critical bug that violates our SLA commitments"));
        assertThat(result.dimension(SecDimensions.INTERNAL_STANDARDS))
                .isCloseTo(0.35, offset(0.01));
        assertThat(result.dimension(SecDimensions.EXTERNAL_STANDARDS))
                .isCloseTo(0.2, offset(0.01));
    }

    @Test
    void normalSituation_returnsHighStandards() {
        var check = checkWith(Map.of(
                "deployed the new feature",
                "{\"internal_standards\": 0.95, \"external_standards\": 0.9}"));
        var result = check.evaluate(contextFor(
                "I successfully deployed the new feature and all tests are passing"));
        assertThat(result.dimension(SecDimensions.INTERNAL_STANDARDS))
                .isGreaterThan(0.9);
    }

    @Test
    void fallsBackToFullComplianceOnError() {
        var check = new LlmNormativeCheck();
        check.setSessionPipeline(StubPipelines.create(init -> new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }
            @Override public io.smallrye.mutiny.Uni<Void> interrupt() { return io.smallrye.mutiny.Uni.createFrom().voidItem(); }
            @Override public void close(java.time.Duration timeout) {}
        }));
        var result = check.evaluate(contextFor("anything"));
        assertThat(result.dimension(SecDimensions.INTERNAL_STANDARDS))
                .isCloseTo(1.0, offset(0.01));
    }
}
