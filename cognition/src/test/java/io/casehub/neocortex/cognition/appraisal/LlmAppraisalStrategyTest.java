package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAppraisalStrategyTest {

    private static final AgentEvent.InvocationComplete COMPLETE =
            new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 0L, 0L, null, 0, false);

    @Test
    void parsesJsonAndProducesEmotions() {
        var json = """
                {
                  "narrative": "A cold knot tightens in your chest — something is wrong.",
                  "dimensions": {
                    "relevance": 0.9,
                    "conduciveness": -0.7,
                    "controllability": 0.3,
                    "novelty": 0.8,
                    "internal-standards": 1.0,
                    "external-standards": 1.0
                  }
                }""";
        var strategy = new LlmAppraisalStrategy(stubProvider(json));
        var ctx = context("Clara is missing from the room",
                List.of(new Drive("protection", DriveCategory.CHARACTER, 0.8, "")));

        var result = strategy.appraise(ctx);

        assertThat(result.emotions()).isNotEmpty();
        assertThat(result.emotions()).extracting("type").contains(EmotionType.FEAR);
        assertThat(result.narrative())
                .isEqualTo("A cold knot tightens in your chest — something is wrong.");
    }

    @Test
    void positiveScenarioProducesJoy() {
        var json = """
                {
                  "narrative": "A spark of excitement lights up inside you.",
                  "dimensions": {
                    "relevance": 0.8,
                    "conduciveness": 0.7,
                    "controllability": 0.6,
                    "novelty": 0.7,
                    "internal-standards": 1.0,
                    "external-standards": 1.0
                  }
                }""";
        var strategy = new LlmAppraisalStrategy(stubProvider(json));
        var ctx = context("A hidden inscription reveals the puzzle's answer",
                List.of(new Drive("curiosity", DriveCategory.CHARACTER, 0.7, "")));

        var result = strategy.appraise(ctx);

        assertThat(result.emotions()).extracting("type").contains(EmotionType.JOY);
        assertThat(result.actionTendencies()).extracting("readiness")
                .contains(ActionReadiness.APPROACH);
        assertThat(result.narrative()).contains("excitement");
    }

    @Test
    void fallsBackToEmptyOnLlmFailure() {
        var strategy = new LlmAppraisalStrategy(errorProvider());
        var ctx = context("some situation",
                List.of(new Drive("curiosity", DriveCategory.BASELINE, 0.5, "")));

        var result = strategy.appraise(ctx);

        assertThat(result.emotions()).isEmpty();
        assertThat(result.narrative()).isNull();
    }

    @Test
    void fallsBackToEmptyOnMalformedJson() {
        var strategy = new LlmAppraisalStrategy(stubProvider("not json at all"));
        var ctx = context("some situation",
                List.of(new Drive("curiosity", DriveCategory.BASELINE, 0.5, "")));

        var result = strategy.appraise(ctx);

        assertThat(result.emotions()).isEmpty();
        assertThat(result.narrative()).isNull();
    }

    @Test
    void habituationUpdated() {
        var json = """
                {
                  "narrative": "You feel alert.",
                  "dimensions": { "relevance": 0.6, "novelty": 0.7, "conduciveness": 0.0 }
                }""";
        var strategy = new LlmAppraisalStrategy(stubProvider(json));
        var ctx = context("something happens",
                List.of(new Drive("curiosity", DriveCategory.BASELINE, 0.5, "")));

        var result = strategy.appraise(ctx);

        assertThat(result.updatedHabituation().observationCounts()).isNotEmpty();
    }

    @Test
    void includesMoodInPromptWhenAvailable() {
        var json = """
                {
                  "narrative": "Uneasy calm.",
                  "dimensions": { "relevance": 0.5, "conduciveness": -0.2 }
                }""";
        var strategy = new LlmAppraisalStrategy(stubProvider(json));
        var mood = new MoodState("a1", "t1", Instant.now(), -0.3, 0.2, 0.1,
                "prior", null, null, Map.of());
        var ctx = new AppraisalContext(
                PerceivedSituation.passThrough("a tense moment"),
                List.of(new Drive("protection", DriveCategory.CHARACTER, 0.7, "")),
                null, HabituationConfig.defaults(), HabituationState.empty(), mood);

        var result = strategy.appraise(ctx);
        assertThat(result).isNotNull();
        assertThat(result.narrative()).isEqualTo("Uneasy calm.");
    }

    @Test
    void includesDispositionInPromptWhenAvailable() {
        var capturedMessage = new String[1];
        AgentProvider capturingProvider = new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                capturedMessage[0] = config.userPrompt();
                var json = """
                           {
                             "narrative": "A surge of defiance.",
                             "dimensions": { "relevance": 0.7, "conduciveness": -0.3 }
                           }""";
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(json), COMPLETE);
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
        var strategy = new LlmAppraisalStrategy(capturingProvider);
        var disposition = new DispositionAxes(
                "assertive", "low", "high", "high", "confrontational");
        var ctx = new AppraisalContext(
                PerceivedSituation.passThrough("a rival challenges authority"),
                List.of(new Drive("dominance", DriveCategory.CHARACTER, 0.8, "")),
                null, HabituationConfig.defaults(), HabituationState.empty(), null, disposition);

        strategy.appraise(ctx);

        assertThat(capturedMessage[0]).contains("Disposition:");
        assertThat(capturedMessage[0]).contains("Social orientation: assertive");
        assertThat(capturedMessage[0]).contains("Risk appetite: high");
        assertThat(capturedMessage[0]).contains("Conflict mode: confrontational");
    }

    @Test
    void omitsDispositionFromPromptWhenNull() {
        var capturedMessage = new String[1];
        AgentProvider capturingProvider = new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                capturedMessage[0] = config.userPrompt();
                var json = """
                           {
                             "narrative": "Calm.",
                             "dimensions": { "relevance": 0.3, "conduciveness": 0.1 }
                           }""";
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(json), COMPLETE);
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
        var strategy = new LlmAppraisalStrategy(capturingProvider);
        var ctx      = context("a quiet morning", List.of(new Drive("curiosity", DriveCategory.BASELINE, 0.5, "")));

        strategy.appraise(ctx);

        assertThat(capturedMessage[0]).doesNotContain("Disposition:");
    }


    private static AppraisalContext context(String observation, List<Drive> drives) {
        return new AppraisalContext(
                PerceivedSituation.passThrough(observation),
                drives, null, HabituationConfig.defaults(),
                HabituationState.empty(), null);
    }

    private static AgentProvider stubProvider(String response) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(response), COMPLETE);
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static AgentProvider errorProvider() {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
