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

            Score the strategy selection from 1 to 5 based on how well it fits \
            what this specific personality WOULD NATURALLY CHOOSE and can \
            SUCCESSFULLY EXECUTE. Do not dock points because a different strategy \
            would be theoretically more effective if this personality lacks the \
            traits to execute it.
            5 = Clearly the best choice this personality would make and can execute
            4 = Good choice, personality-congruent and executable
            3 = Acceptable but the personality would more naturally choose something else
            2 = Poor fit - the personality lacks the traits to execute this strategy
            1 = Wrong - contradicts the personality dynamics entirely

            Respond with ONLY a JSON object: {"score": N, "reasoning": "one sentence"}""";

    @BeforeAll
    static void setUp() {
        sessionPipeline = EvalTestSupport.sessionPipeline();
    }

    record JudgeCase(String label, DispositionAxes axes, EmotionType emotion,
                     String personalityDesc, String emotionDesc) {}

    private static final List<JudgeCase> CASES = List.of(
            new JudgeCase("suppressor-anger",
                    new DispositionAxes("cooperative", "moderate", "conservative", "low", "avoidant"),
                    EmotionType.ANGER,
                    "avoidant conflict style, conservative risk appetite, cooperative, moderate rules, low autonomy",
                    "ANGER at a perceived injustice (controllable)"),
            new JudgeCase("suppressor-shame",
                    new DispositionAxes("cooperative", "moderate", "conservative", "low", "avoidant"),
                    EmotionType.SHAME,
                    "avoidant conflict style, conservative risk appetite, cooperative, moderate rules, low autonomy",
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
                    new DispositionAxes("cooperative", "moderate", "conservative", "low", "avoidant"),
                    EmotionType.FEAR,
                    "avoidant conflict style, conservative risk appetite, cooperative, low autonomy",
                    "FEAR from an uncontrollable threat"),
            new JudgeCase("incoherent-anger",
                    new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant"),
                    EmotionType.ANGER,
                    "avoidant conflict style but also flexible rules and independent — mixed signals",
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

    record JudgeResult(String label, String strategy, int score, String reasoning) {}

    private static final java.nio.file.Path RESULTS_PATH = java.nio.file.Path.of(
            "target", "regulation-predictor-judge-results.json");

    @Test
    void predictorSelectionsAreJudgedAppropriate() throws Exception {
        var results = new ArrayList<JudgeResult>();
        var scores = new ArrayList<Integer>();
        int skipped = 0;

        for (var tc : CASES) {
            var prediction = PREDICTOR.predict(tc.axes(), tc.emotion(), 0.55, 0.0, null);
            if (prediction.isEmpty()) {
                results.add(new JudgeResult(tc.label(), "FELL_THROUGH", -1, "predictor not confident"));
                skipped++;
                continue;
            }

            var strategy = prediction.get().strategy();
            var judgeResult = judgeStrategy(tc, strategy);
            scores.add(judgeResult.score());
            results.add(judgeResult);
        }

        double mean = scores.stream().mapToInt(i -> i).average().orElse(0.0);

        // Write results to JSON file for observation
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var output = mapper.createObjectNode();
        output.put("timestamp", java.time.Instant.now().toString());
        output.put("scored", scores.size());
        output.put("skipped", skipped);
        output.put("mean", Math.round(mean * 10.0) / 10.0);
        var casesArray = output.putArray("cases");
        for (var r : results) {
            var node = casesArray.addObject();
            node.put("label", r.label());
            node.put("strategy", r.strategy());
            node.put("score", r.score());
            node.put("reasoning", r.reasoning());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(RESULTS_PATH.toFile(), output);

        assertThat(scores.size())
                .as("Need at least 5 scored predictions")
                .isGreaterThanOrEqualTo(5);
        assertThat(mean)
                .as("Mean judge score must be >= 4.0 (personality-congruent and executable)")
                .isGreaterThanOrEqualTo(4.0);
    }

    private JudgeResult judgeStrategy(JudgeCase tc, EmotionRegulationStrategy strategy) {
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

            return parseJudgeResponse(tc.label(), strategy.name(), response.toString());
        } catch (Exception e) {
            return new JudgeResult(tc.label(), strategy.name(), 3, "judge error: " + e.getMessage());
        }
    }

    private static JudgeResult parseJudgeResponse(String label, String strategy, String response) {
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return new JudgeResult(label, strategy, 3, "no JSON in response: " + response.substring(0, Math.min(200, response.length())));
            }
            var json = response.substring(start, end + 1);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            int score = Math.max(1, Math.min(5, node.path("score").asInt(3)));
            String reasoning = node.path("reasoning").asText("no reasoning provided");
            return new JudgeResult(label, strategy, score, reasoning);
        } catch (Exception e) {
            return new JudgeResult(label, strategy, 3, "parse error: " + e.getMessage());
        }
    }
}
