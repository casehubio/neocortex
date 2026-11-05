package io.casehub.neocortex.caps.llm.eval;

import io.casehub.neocortex.cognition.regulation.EmotionRegulationStrategy;
import io.casehub.neocortex.cognition.regulation.RegulationStrategyPredictor;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.SessionPipeline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality gate: LLM judge scores the predictor's strategy selections.
 *
 * Instead of comparing predictor vs regulation-LLM (non-deterministic),
 * a separate LLM acts as a psychology expert judging whether the predicted
 * strategy is appropriate for the given personality + emotion combination.
 * Score 1-5 per case. Gate: mean >= 3.5.
 */
@Tag("llm-eval")
class RegulationPredictorBackTest {

    private static SessionPipeline sessionPipeline;
    private static final RegulationStrategyPredictor PREDICTOR = new RegulationStrategyPredictor();

    private static final String JUDGE_SYSTEM_PROMPT = """
            You are an emotion regulation expert evaluating whether a computational \
            system's strategy selection is psychologically appropriate.

            The Gross (1998) process model defines five regulation strategies:
            - SITUATION_SELECTION: avoid triggering contexts
            - SITUATION_MODIFICATION: change the situation
            - ATTENTIONAL_DEPLOYMENT: redirect attention (distraction)
            - COGNITIVE_CHANGE: reappraise the meaning (reframing)
            - RESPONSE_MODULATION: suppress or amplify expression

            Petter et al. (2025) found that strategy selection depends on:
            - Emotion intensity (high -> distraction, low-moderate -> reappraisal viable)
            - Personality (neuroticism -> suppression, openness -> reappraisal)
            - Controllability (controllable -> reappraisal/suppression, uncontrollable -> distraction)

            Score the strategy selection from 1 to 5:
            5 = Clearly the best choice for this personality + emotion
            4 = Good choice, well-aligned with personality and emotion type
            3 = Acceptable but not the most natural fit
            2 = Questionable - a different strategy would fit better
            1 = Wrong - this strategy contradicts the personality or emotion dynamics

            Respond with ONLY a JSON object: {"score": N, "reasoning": "one sentence"}""";

    @BeforeAll
    static void setUp() {
        sessionPipeline = EvalTestSupport.sessionPipeline();
    }

    record JudgeCase(String label, DispositionAxes axes, EmotionType emotion,
                     String personalityDesc, String emotionDesc) {}

    private static final List<JudgeCase> CASES = List.of(
            new JudgeCase("suppressor-anger",
                    new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant"),
                    EmotionType.ANGER,
                    "avoidant conflict style, conservative risk appetite, independent, flexible rules",
                    "ANGER at a perceived injustice (controllable)"),
            new JudgeCase("suppressor-shame",
                    new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant"),
                    EmotionType.SHAME,
                    "avoidant conflict style, conservative risk appetite, independent, flexible rules",
                    "SHAME from failing to meet their own standards"),
            new JudgeCase("reappraiser-anger",
                    new DispositionAxes("cooperative", "strict", "calculated", "high", "analytical"),
                    EmotionType.ANGER,
                    "analytical conflict style, strict rule-following, cooperative, high autonomy",
                    "ANGER at a perceived injustice (controllable)"),
            new JudgeCase("reappraiser-reproach",
                    new DispositionAxes("cooperative", "strict", "calculated", "high", "analytical"),
                    EmotionType.REPROACH,
                    "analytical conflict style, strict rule-following, cooperative, high autonomy",
                    "REPROACH toward someone who failed the agent's standards"),
            new JudgeCase("anyone-fear",
                    new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant"),
                    EmotionType.FEAR,
                    "avoidant conflict style, conservative risk appetite, independent",
                    "FEAR from an uncontrollable threat"),
            new JudgeCase("controller-anger",
                    new DispositionAxes("competitive", "flexible", "conservative", "low", "avoidant"),
                    EmotionType.ANGER,
                    "avoidant conflict style, competitive social orientation, conservative, low autonomy",
                    "ANGER at being publicly contradicted"),
            new JudgeCase("reappraiser-shame",
                    new DispositionAxes("cooperative", "strict", "bold", "high", "analytical"),
                    EmotionType.SHAME,
                    "analytical conflict style, strict rules, bold risk appetite, high autonomy, cooperative",
                    "SHAME from a public failure"),
            new JudgeCase("mixed-anger",
                    new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative"),
                    EmotionType.ANGER,
                    "moderate across all axes, cooperative orientation",
                    "ANGER at a betrayal of trust")
    );

    @Test
    void predictorSelectionsAreJudgedAppropriate() {
        var details = new ArrayList<String>();
        var scores = new ArrayList<Integer>();
        int skipped = 0;

        for (var tc : CASES) {
            var prediction = PREDICTOR.predict(tc.axes(), tc.emotion(), 0.55, 0.0, null);
            if (prediction.isEmpty()) {
                details.add(String.format("  %s: predictor fell through (no confident prediction)", tc.label()));
                skipped++;
                continue;
            }

            var strategy = prediction.get().strategy();
            int score = judgeStrategy(tc, strategy);
            scores.add(score);
            details.add(String.format("  %s: %s -> score %d", tc.label(), strategy, score));
        }

        System.out.println("=== Predictor Judge Evaluation ===");
        details.forEach(System.out::println);

        double mean = scores.stream().mapToInt(i -> i).average().orElse(0.0);
        System.out.printf("%nScored: %d, Skipped: %d, Mean: %.1f%n", scores.size(), skipped, mean);
        System.out.printf("Distribution: %s%n", scores);

        assertThat(scores.size())
                .as("Need at least 5 scored predictions")
                .isGreaterThanOrEqualTo(5);
        assertThat(mean)
                .as("Mean judge score must be >= 3.5 (good-to-excellent)")
                .isGreaterThanOrEqualTo(3.5);
    }

    private int judgeStrategy(JudgeCase tc, EmotionRegulationStrategy strategy) {
        try {
            var session = sessionPipeline.openSession("regulation-judge",
                    AgentSessionInit.of(JUDGE_SYSTEM_PROMPT));

            String userMessage = String.format("""
                    Personality: %s
                    Emotion: %s
                    Selected strategy: %s
                    Emotion intensity: moderate (0.55 on 0-1 scale)

                    Score this strategy selection.""",
                    tc.personalityDesc(), tc.emotionDesc(), strategy.name());

            var response = new StringBuilder();
            session.query(userMessage).subscribe().asStream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .forEach(response::append);

            return parseScore(response.toString());
        } catch (Exception e) {
            System.err.printf("  Judge failed for %s: %s%n", tc.label(), e.getMessage());
            return 3;
        }
    }

    private static int parseScore(String response) {
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end <= start) return 3;
            var json = response.substring(start, end + 1);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            int score = node.path("score").asInt(3);
            String reasoning = node.path("reasoning").asText("");
            if (!reasoning.isEmpty()) {
                System.out.printf("    judge: %s%n", reasoning);
            }
            return Math.max(1, Math.min(5, score));
        } catch (Exception e) {
            return 3;
        }
    }
}
