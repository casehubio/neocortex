package io.casehub.neocortex.cognition.appraisal;

import io.casehub.neocortex.cognition.belief.StubPipelines;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.memory.mood.MoodState;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSession;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmAppraisalStrategyTest {

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
        var strategy = stubStrategy(json);
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
        var strategy = stubStrategy(json);
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
        var strategy = errorStrategy();
        var ctx = context("some situation",
                List.of(new Drive("curiosity", DriveCategory.BASELINE, 0.5, "")));

        var result = strategy.appraise(ctx);

        assertThat(result.emotions()).isEmpty();
        assertThat(result.narrative()).isNull();
    }

    @Test
    void fallsBackToEmptyOnMalformedJson() {
        var strategy = stubStrategy("not json at all");
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
        var strategy = stubStrategy(json);
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
        var strategy = stubStrategy(json);
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
        var strategy = new LlmAppraisalStrategy();
        strategy.setSessionPipeline(StubPipelines.create(init -> new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) {
                capturedMessage[0] = prompt;
                var json = """
                           {
                             "narrative": "A surge of defiance.",
                             "dimensions": { "relevance": 0.7, "conduciveness": -0.3 }
                           }""";
                return Multi.createFrom().item(new AgentEvent.TextDelta(json));
            }
            @Override public io.smallrye.mutiny.Uni<Void> interrupt() { return io.smallrye.mutiny.Uni.createFrom().voidItem(); }
            @Override public void close(java.time.Duration timeout) {}
        }));
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
        var strategy = new LlmAppraisalStrategy();
        strategy.setSessionPipeline(StubPipelines.create(init -> new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) {
                capturedMessage[0] = prompt;
                var json = """
                           {
                             "narrative": "Calm.",
                             "dimensions": { "relevance": 0.3, "conduciveness": 0.1 }
                           }""";
                return Multi.createFrom().item(new AgentEvent.TextDelta(json));
            }
            @Override public io.smallrye.mutiny.Uni<Void> interrupt() { return io.smallrye.mutiny.Uni.createFrom().voidItem(); }
            @Override public void close(java.time.Duration timeout) {}
        }));
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

    private static LlmAppraisalStrategy stubStrategy(String response) {
        var strategy = new LlmAppraisalStrategy();
        strategy.setSessionPipeline(StubPipelines.create(init -> new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) {
                return Multi.createFrom().item(new AgentEvent.TextDelta(response));
            }
            @Override public io.smallrye.mutiny.Uni<Void> interrupt() { return io.smallrye.mutiny.Uni.createFrom().voidItem(); }
            @Override public void close(java.time.Duration timeout) {}
        }));
        return strategy;
    }

    private static LlmAppraisalStrategy errorStrategy() {
        var strategy = new LlmAppraisalStrategy();
        strategy.setSessionPipeline(StubPipelines.create(init -> new AgentSession() {
            @Override public Multi<AgentEvent> query(String prompt) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }
            @Override public io.smallrye.mutiny.Uni<Void> interrupt() { return io.smallrye.mutiny.Uni.createFrom().voidItem(); }
            @Override public void close(java.time.Duration timeout) {}
        }));
        return strategy;
    }
}
