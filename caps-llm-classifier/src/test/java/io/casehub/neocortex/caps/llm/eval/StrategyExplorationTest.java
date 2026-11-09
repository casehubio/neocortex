package io.casehub.neocortex.caps.llm.eval;

import io.casehub.neocortex.cognition.regulation.EmotionRegulationStrategy;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.SessionPipeline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Strategy exploration: test ALL five Gross strategies against the judge
 * for each boundary case. Finds which strategy label the judge prefers,
 * independent of narrative quality.
 */
@Tag("llm-eval")
class StrategyExplorationTest {

    private static SessionPipeline sessionPipeline;

    private static final String JUDGE_SYSTEM_PROMPT = """
            You are an emotion regulation expert evaluating whether an AI character's \
            regulation strategy is psychologically appropriate for their personality.

            The Gross (1998) process model defines five regulation strategies:
            - SITUATION_SELECTION: avoid or approach situations likely to trigger specific emotions
            - SITUATION_MODIFICATION: change an existing situation to alter its emotional impact
            - ATTENTIONAL_DEPLOYMENT: redirect attention away from (distraction) or toward emotional aspects
            - COGNITIVE_CHANGE: reappraise the meaning of the situation to change its emotional impact
            - RESPONSE_MODULATION: suppress or amplify the emotional expression after it has been generated

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

    record BoundaryCase(String label, DispositionAxes axes, EmotionType emotion,
                        String personalityDesc, String emotionDesc) {}

    private static final List<BoundaryCase> BOUNDARY_CASES = List.of(
            new BoundaryCase("mixed-anger",
                    new DispositionAxes("cooperative", "moderate", "calculated", "moderate", "cooperative"),
                    EmotionType.ANGER,
                    "moderate across all axes, cooperative orientation",
                    "ANGER at a betrayal of trust"),
            new BoundaryCase("suppressor-shame",
                    new DispositionAxes("cooperative", "moderate", "conservative", "low", "avoidant"),
                    EmotionType.SHAME,
                    "avoidant conflict style, conservative risk appetite, cooperative, moderate rules, low autonomy",
                    "SHAME from failing to meet their own standards"),
            new BoundaryCase("compounding-shame",
                    new DispositionAxes("cooperative", "moderate", "conservative", "low", "avoidant"),
                    EmotionType.SHAME,
                    "avoidant conflict style, conservative risk appetite, cooperative, moderate rules, low autonomy",
                    "SHAME from repeated failures — 4th episode of shame in recent history")
    );

    private static final EmotionRegulationStrategy[] ALL_STRATEGIES = EmotionRegulationStrategy.values();

    @Test
    void exploreAllStrategiesForBoundaryCases() throws Exception {
        record Entry(BoundaryCase tc, EmotionRegulationStrategy strategy, ScoreResult score) {}
        var entries = new ArrayList<Entry>();

        for (var tc : BOUNDARY_CASES) {
            for (var strategy : ALL_STRATEGIES) {
                var score = judgeStrategy(tc, strategy);
                entries.add(new Entry(tc, strategy, score));
            }
        }

        // Print results
        String currentLabel = "";
        for (var e : entries) {
            if (!e.tc().label().equals(currentLabel)) {
                currentLabel = e.tc().label();
                System.out.println("\n=== " + currentLabel + " ===");
                System.out.println("Personality: " + e.tc().personalityDesc());
                System.out.println("Emotion: " + e.tc().emotionDesc());
            }
            System.out.printf("  %-25s → %d  %s%n",
                    e.strategy().name(), e.score().score(), e.score().reasoning());
        }

        // Write to JSON
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var output = mapper.createObjectNode();
        output.put("timestamp", Instant.now().toString());
        for (var e : entries) {
            var caseNode = output.has(e.tc().label())
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) output.get(e.tc().label())
                    : output.putObject(e.tc().label());
            if (!caseNode.has("personality")) {
                caseNode.put("personality", e.tc().personalityDesc());
                caseNode.put("emotion", e.tc().emotionDesc());
            }
            var strategies = caseNode.has("strategies")
                    ? (com.fasterxml.jackson.databind.node.ObjectNode) caseNode.get("strategies")
                    : caseNode.putObject("strategies");
            var sNode = strategies.putObject(e.strategy().name());
            sNode.put("score", e.score().score());
            sNode.put("reasoning", e.score().reasoning());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                java.nio.file.Path.of("target", "strategy-exploration.json").toFile(), output);
    }

    record ScoreResult(int score, String reasoning) {}

    private ScoreResult judgeStrategy(BoundaryCase tc, EmotionRegulationStrategy strategy) {
        try {
            var session = sessionPipeline.openSession("strategy-exploration-judge",
                    AgentSessionInit.of(JUDGE_SYSTEM_PROMPT));

            String userMessage = String.format("""
                    Personality: %s
                    Emotion: %s
                    Selected strategy: %s
                    Emotion intensity: moderate (0.55 on 0-1 scale)

                    Score this strategy selection.""",
                    tc.personalityDesc(), tc.emotionDesc(), strategy.name());

            var sb = new StringBuilder();
            session.query(userMessage).subscribe().asStream()
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .forEach(sb::append);

            return parseJudgeResponse(sb.toString());
        } catch (Exception e) {
            return new ScoreResult(0, "error: " + e.getMessage());
        }
    }

    private static ScoreResult parseJudgeResponse(String response) {
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return new ScoreResult(0, "no JSON: " + response.substring(0, Math.min(100, response.length())));
            }
            var json = response.substring(start, end + 1);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            int score = Math.max(1, Math.min(5, node.path("score").asInt(3)));
            String reasoning = node.path("reasoning").asText("no reasoning");
            return new ScoreResult(score, reasoning);
        } catch (Exception e) {
            return new ScoreResult(0, "parse error: " + e.getMessage());
        }
    }
}
