package io.casehub.neocortex.caps.llm.eval;

import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationGenerationPhase;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationResult;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationStrategy;
import io.casehub.neocortex.cognition.regulation.RegulationStrategyPredictor;
import io.casehub.neocortex.cognition.regulation.ShameContext;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.cognitive.index.CognitiveDerivationEngine;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.neocortex.cognitive.index.RegulationDefaults;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.SessionPipeline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end regulation eval: predictor → LLM generation → judge.
 *
 * Fills the eval gap identified in Phase 1 findings: RegulationPredictorBackTest
 * judges the predictor's selection but skips fall-through cases. This test runs
 * the full pipeline and judges the LLM's chosen strategy, including cases where
 * the predictor returns empty and the LLM receives personality context directly.
 */
@Tag("llm-eval")
class RegulationEndToEndEvalTest {

    private static SessionPipeline sessionPipeline;
    private static final RegulationStrategyPredictor PREDICTOR = new RegulationStrategyPredictor();
    private static EmotionRegulationGenerationPhase generationPhase;

    private static final String JUDGE_SYSTEM_PROMPT = """
            You are an emotion regulation expert evaluating whether an AI character's \
            regulation strategy is psychologically appropriate for their personality.

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

    private static final String TEMPORAL_CONTAINMENT_JUDGE = """
            You are evaluating whether a regulation narrative demonstrates temporal \
            containment for a self-conscious emotion (shame/remorse).

            Temporal containment (Webb et al. 2012, Nathanson 1992) means the \
            regulation focuses on:
            - Riding out the emotional wave rather than eliminating it
            - Self-soothing or attentional strategies to survive peak intensity
            - Waiting for the emotion to pass naturally
            - NOT deep cognitive restructuring or challenging core beliefs

            This is appropriate because this personality CANNOT effectively execute \
            cognitive change — they lack the analytical openness and high autonomy \
            needed for genuine reappraisal.

            Score 1-5:
            5 = Clear temporal containment, no forced reappraisal
            4 = Primarily temporal containment with minor reappraisal elements
            3 = Mixed — equal parts containment and reappraisal
            2 = Primarily reappraisal with minor containment
            1 = Pure cognitive restructuring, no containment

            Respond with ONLY a JSON object: {"score": N, "reasoning": "one sentence"}""";

    private static final String SHAME_DEPTH_JUDGE = """
            You are evaluating whether a regulation narrative demonstrates the \
            correct DEPTH of shame processing given the character's personality.

            Score the depth appropriateness from 1 to 5:
            5 = Depth perfectly matches the personality's capacity for self-evaluation
            4 = Good match — minor mismatch between personality and processing depth
            3 = Moderate mismatch — somewhat too deep or too shallow for this personality
            2 = Poor match — depth contradicts the personality's natural processing style
            1 = Completely wrong depth — e.g., deep introspection from a callous personality

            Context dimensions that should influence depth:
            - Memory compounding: repeated shame episodes should show accumulated weight
            - Relational charging: shame from someone the character values should go deeper
            - Surface-level personality (high grandiosity + callousness): shame should bounce off,
              the character should deflect or externalize rather than genuinely process

            Respond with ONLY a JSON object: {"score": N, "reasoning": "one sentence"}""";

    @BeforeAll
    static void setUp() {
        sessionPipeline = EvalTestSupport.sessionPipeline();
        generationPhase = new EmotionRegulationGenerationPhase(sessionPipeline);
    }

    record JudgeCase(String label, DispositionAxes axes, EmotionType emotion,
                     String personalityDesc, String emotionDesc,
                     ShameContext shameContext) {
        JudgeCase(String label, DispositionAxes axes, EmotionType emotion,
                  String personalityDesc, String emotionDesc) {
            this(label, axes, emotion, personalityDesc, emotionDesc, null);
        }
    }

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
                    "ANGER at a betrayal of trust"),
            new JudgeCase("compounding-shame",
                    new DispositionAxes("cooperative", "moderate", "conservative", "low", "avoidant"),
                    EmotionType.SHAME,
                    "avoidant conflict style, conservative risk appetite, low autonomy — coherent suppressor",
                    "SHAME from repeated failures — 4th episode of shame in recent history",
                    new ShameContext(4, Instant.now(), 0.0, null, 0.3, 0.2)),
            new JudgeCase("relational-shame",
                    new DispositionAxes("cooperative", "strict", "bold", "high", "analytical"),
                    EmotionType.SHAME,
                    "analytical conflict style, strict rules, bold risk, high autonomy — coherent reappraiser",
                    "SHAME from disappointing a mentor they deeply respect",
                    new ShameContext(1, Instant.now(), 0.8, "mentor", 0.3, 0.2)),
            new JudgeCase("surface-shame",
                    new DispositionAxes("competitive", "flexible", "bold", "high", "avoidant"),
                    EmotionType.SHAME,
                    "high grandiosity (0.8) and callousness (0.8) — shame does not penetrate deeply",
                    "SHAME from being caught in a lie — personality deflects rather than processes",
                    new ShameContext(0, null, 0.0, null, 0.8, 0.8))
    );

    record E2EResult(String label, String predictorStrategy, String llmStrategy,
                     int strategyScore, String reasoning,
                     int temporalScore, String temporalReasoning,
                     int shameDepthScore, String shameDepthReasoning,
                     String narrative) {}

    private static final java.nio.file.Path RESULTS_PATH = java.nio.file.Path.of(
            "target", "regulation-e2e-judge-results.json");

    @Test
    void llmRegulationStrategiesAreAppropriate() throws Exception {
        var results = new ArrayList<E2EResult>();
        var strategyScores = new ArrayList<Integer>();
        var temporalScores = new ArrayList<Integer>();
        int nullResults = 0;

        var shameDepthScores = new ArrayList<Integer>();

        for (var tc : CASES) {
            var rd = CognitiveDerivationEngine.deriveRegulationDefaults(tc.axes());
            var prediction = PREDICTOR.predict(rd, tc.axes(), tc.emotion(), 0.55, 0.0, null);

            var context = new CognitionTickContext("eval-agent", "eval-tenant", null,
                    (a, t) -> Set.of());
            var emotion = new CognitiveEmotion(tc.emotion(), 0.55, "eval-subject",
                    Instant.now(), EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
            var appraisal = new AppraisalResult(List.of(emotion), List.of(),
                    HabituationState.empty());

            EmotionRegulationResult result;
            String predictorOutcome;
            if (prediction.isPresent()) {
                result = generationPhase.generate(context, appraisal, List.of(),
                        0.5, 0.0, null, prediction.get().strategy(),
                        tc.axes(), tc.emotion(), rd, tc.shameContext(),
                        tc.emotionDesc());
                predictorOutcome = prediction.get().strategy().name();
            } else {
                result = generationPhase.generate(context, appraisal, List.of(),
                        0.5, 0.0, null, null,
                        tc.axes(), tc.emotion(), rd, tc.shameContext(),
                        tc.emotionDesc());
                predictorOutcome = "FELL_THROUGH";
            }

            if (result == null) {
                results.add(new E2EResult(tc.label(), predictorOutcome, "NULL",
                        0, "generation returned null", -1, "", -1, "", ""));
                nullResults++;
                continue;
            }

            var strategyJudge = judgeStrategy(tc, result.strategy());
            strategyScores.add(strategyJudge.score());

            int temporalScore = -1;
            String temporalReasoning = "";
            if (prediction.isEmpty() && isSelfConscious(tc.emotion())) {
                var temporal = judgeTemporalContainment(tc, result.narrative());
                temporalScore = temporal.score();
                temporalReasoning = temporal.reasoning();
                temporalScores.add(temporalScore);
            }

            int shameDepthScore = -1;
            String shameDepthReasoning = "";
            if (tc.shameContext() != null) {
                var depth = judgeShameDepth(tc, result.narrative());
                shameDepthScore = depth.score();
                shameDepthReasoning = depth.reasoning();
                shameDepthScores.add(shameDepthScore);
            }

            results.add(new E2EResult(tc.label(), predictorOutcome,
                    result.strategy().name(), strategyJudge.score(),
                    strategyJudge.reasoning(), temporalScore, temporalReasoning,
                    shameDepthScore, shameDepthReasoning,
                    result.narrative()));
        }

        writeResults(results, strategyScores, temporalScores, shameDepthScores, nullResults);

        assertThat(nullResults)
                .as("All cases should produce a regulation result")
                .isZero();
        double strategyMean = strategyScores.stream().mapToInt(i -> i).average().orElse(0.0);
        assertThat(strategyMean)
                .as("Mean strategy score >= 4.0 (personality-congruent and executable)")
                .isGreaterThanOrEqualTo(4.0);
        if (!temporalScores.isEmpty()) {
            double temporalMean = temporalScores.stream().mapToInt(i -> i).average().orElse(0.0);
            assertThat(temporalMean)
                    .as("Mean temporal containment score >= 3.0 for self-conscious fall-throughs")
                    .isGreaterThanOrEqualTo(3.0);
        }
        if (!shameDepthScores.isEmpty()) {
            double shameDepthMean = shameDepthScores.stream().mapToInt(i -> i).average().orElse(0.0);
            assertThat(shameDepthMean)
                    .as("Mean shame depth score >= 3.0 for shame-specific cases")
                    .isGreaterThanOrEqualTo(3.0);
        }
    }

    private ScoreResult judgeStrategy(JudgeCase tc, EmotionRegulationStrategy strategy) {
        try {
            var session = sessionPipeline.openSession("regulation-e2e-judge",
                    AgentSessionInit.of(JUDGE_SYSTEM_PROMPT));

            String userMessage = String.format("""
                    Personality: %s
                    Emotion: %s
                    Selected strategy: %s
                    Emotion intensity: moderate (0.55 on 0-1 scale)

                    Score this strategy selection.""",
                    tc.personalityDesc(), tc.emotionDesc(), strategy.name());

            var response = collectResponse(session, userMessage);
            return parseJudgeResponse(response);
        } catch (Exception e) {
            return new ScoreResult(3, "judge error: " + e.getMessage());
        }
    }

    private ScoreResult judgeShameDepth(JudgeCase tc, String narrative) {
        try {
            var session = sessionPipeline.openSession("regulation-shame-depth-judge",
                    AgentSessionInit.of(SHAME_DEPTH_JUDGE));

            var contextDesc = new StringBuilder();
            if (tc.shameContext().isCompounding()) {
                contextDesc.append("Memory compounding: ").append(tc.shameContext().episodeCount())
                        .append(" prior episodes. ");
            }
            if (tc.shameContext().isRelationallyCharged()) {
                contextDesc.append("Relationally charged: subject is ")
                        .append(tc.shameContext().relationalRole())
                        .append(" (intimacy=").append(tc.shameContext().relationalCloseness()).append("). ");
            }
            if (tc.shameContext().isSurfaceLevel()) {
                contextDesc.append("Surface-level personality: grandiosity=")
                        .append(tc.shameContext().grandiosity())
                        .append(", callousness=").append(tc.shameContext().callousness()).append(". ");
            }

            String userMessage = String.format("""
                    Personality: %s
                    Emotion: %s
                    Shame context: %s

                    Regulation narrative:
                    %s

                    Score the depth appropriateness.""",
                    tc.personalityDesc(), tc.emotionDesc(), contextDesc, narrative);

            var response = collectResponse(session, userMessage);
            return parseJudgeResponse(response);
        } catch (Exception e) {
            return new ScoreResult(3, "shame depth judge error: " + e.getMessage());
        }
    }

    private ScoreResult judgeTemporalContainment(JudgeCase tc, String narrative) {
        try {
            var session = sessionPipeline.openSession("regulation-temporal-judge",
                    AgentSessionInit.of(TEMPORAL_CONTAINMENT_JUDGE));

            String userMessage = String.format("""
                    Personality: %s
                    Emotion: %s

                    Regulation narrative:
                    %s

                    Score this narrative for temporal containment.""",
                    tc.personalityDesc(), tc.emotionDesc(), narrative);

            var response = collectResponse(session, userMessage);
            return parseJudgeResponse(response);
        } catch (Exception e) {
            return new ScoreResult(3, "temporal judge error: " + e.getMessage());
        }
    }

    private static String collectResponse(io.casehub.platform.agent.AgentSession session,
                                           String userMessage) {
        var sb = new StringBuilder();
        session.query(userMessage).subscribe().asStream()
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .forEach(sb::append);
        return sb.toString();
    }

    record ScoreResult(int score, String reasoning) {}

    private static ScoreResult parseJudgeResponse(String response) {
        try {
            int start = response.indexOf('{');
            int end = response.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return new ScoreResult(3, "no JSON in response: " +
                        response.substring(0, Math.min(200, response.length())));
            }
            var json = response.substring(start, end + 1);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            int score = Math.max(1, Math.min(5, node.path("score").asInt(3)));
            String reasoning = node.path("reasoning").asText("no reasoning provided");
            return new ScoreResult(score, reasoning);
        } catch (Exception e) {
            return new ScoreResult(3, "parse error: " + e.getMessage());
        }
    }

    private static boolean isSelfConscious(EmotionType type) {
        return type == EmotionType.SHAME || type == EmotionType.REMORSE;
    }

    private void writeResults(List<E2EResult> results, List<Integer> strategyScores,
                              List<Integer> temporalScores, List<Integer> shameDepthScores,
                              int nullResults) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var output = mapper.createObjectNode();
        output.put("timestamp", Instant.now().toString());
        output.put("scored", strategyScores.size());
        output.put("nullResults", nullResults);
        output.put("strategyMean", strategyScores.isEmpty() ? 0.0 :
                Math.round(strategyScores.stream().mapToInt(i -> i).average().orElse(0.0) * 10.0) / 10.0);
        if (!temporalScores.isEmpty()) {
            output.put("temporalMean",
                    Math.round(temporalScores.stream().mapToInt(i -> i).average().orElse(0.0) * 10.0) / 10.0);
        }
        if (!shameDepthScores.isEmpty()) {
            output.put("shameDepthMean",
                    Math.round(shameDepthScores.stream().mapToInt(i -> i).average().orElse(0.0) * 10.0) / 10.0);
        }
        var casesArray = output.putArray("cases");
        for (var r : results) {
            var node = casesArray.addObject();
            node.put("label", r.label());
            node.put("predictorStrategy", r.predictorStrategy());
            node.put("llmStrategy", r.llmStrategy());
            node.put("strategyScore", r.strategyScore());
            node.put("reasoning", r.reasoning());
            if (r.temporalScore() >= 0) {
                node.put("temporalScore", r.temporalScore());
                node.put("temporalReasoning", r.temporalReasoning());
            }
            if (r.shameDepthScore() >= 0) {
                node.put("shameDepthScore", r.shameDepthScore());
                node.put("shameDepthReasoning", r.shameDepthReasoning());
            }
            node.put("narrative", r.narrative());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(RESULTS_PATH.toFile(), output);
    }
}
