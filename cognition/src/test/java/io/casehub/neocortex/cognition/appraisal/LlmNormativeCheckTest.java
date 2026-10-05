package io.casehub.neocortex.cognition.appraisal;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class LlmNormativeCheckTest {

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
                        new AgentEvent.TextDelta(
                                "{\"internal_standards\": 1.0, \"external_standards\": 1.0}"),
                        COMPLETE);
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
    void normViolation_returnsLowStandards() {
        var check = new LlmNormativeCheck(mockProvider(Map.of(
                "ignoring the code review",
                "{\"internal_standards\": 0.7, \"external_standards\": 0.3}")));
        var result = check.evaluate(contextFor(
                "Someone on the team keeps ignoring the code review process"));
        assertThat(result.dimension(SecDimensions.EXTERNAL_STANDARDS))
                .isCloseTo(0.3, offset(0.01));
    }

    @Test
    void slaViolation_returnsLowBothStandards() {
        var check = new LlmNormativeCheck(mockProvider(Map.of(
                "violates our SLA",
                "{\"internal_standards\": 0.35, \"external_standards\": 0.2}")));
        var result = check.evaluate(contextFor(
                "The client reported a critical bug that violates our SLA commitments"));
        assertThat(result.dimension(SecDimensions.INTERNAL_STANDARDS))
                .isCloseTo(0.35, offset(0.01));
        assertThat(result.dimension(SecDimensions.EXTERNAL_STANDARDS))
                .isCloseTo(0.2, offset(0.01));
    }

    @Test
    void normalSituation_returnsHighStandards() {
        var check = new LlmNormativeCheck(mockProvider(Map.of(
                "deployed the new feature",
                "{\"internal_standards\": 0.95, \"external_standards\": 0.9}")));
        var result = check.evaluate(contextFor(
                "I successfully deployed the new feature and all tests are passing"));
        assertThat(result.dimension(SecDimensions.INTERNAL_STANDARDS))
                .isGreaterThan(0.9);
    }

    @Test
    void fallsBackToFullComplianceOnError() {
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
        var check = new LlmNormativeCheck(failingProvider);
        var result = check.evaluate(contextFor("anything"));
        assertThat(result.dimension(SecDimensions.INTERNAL_STANDARDS))
                .isCloseTo(1.0, offset(0.01));
    }
}
