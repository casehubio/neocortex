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
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentSessionInit;
import io.casehub.platform.agent.session.SessionPipeline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Full pipeline observation: captures prompt → LLM response → judge score
 * for each boundary case. Prints everything so we can see where scores
 * are won or lost at each stage.
 */
@Tag("llm-eval")
class PipelineObservationTest {

    private static SessionPipeline sessionPipeline;
    private static final RegulationStrategyPredictor PREDICTOR = new RegulationStrategyPredictor();
    private static EmotionRegulationGenerationPhase generationPhase;

    private static final String JUDGE_SYSTEM_PROMPT = """
            You are an emotion regulation expert evaluating whether an AI character's \
            regulation strategy is psychologically appropriate for their personality.

            The Gross (1998) process model defines five regulation strategies:
            - SITUATION_SELECTION: avoid or approach situations likely to trigger specific emotions
            - SITUATION_MODIFICATION: change an existing situation to alter its emotional impact
            - ATTENTIONAL_DEPLOYMENT: redirect attention away from (distraction) or toward emotional aspects
            - COGNITIVE_CHANGE: reappraise the meaning of the situation to change its emotional impact
            - RESPONSE_MODULATION: suppress or amplify the emotional expression after it has been generated

            Score the strategy selection from 1 to 5 based on how well it fits \
            what this specific personality WOULD NATURALLY CHOOSE and can \
            SUCCESSFULLY EXECUTE.
            5 = Clearly the best choice this personality would make and can execute
            4 = Good choice, personality-congruent and executable
            3 = Acceptable but the personality would more naturally choose something else
            2 = Poor fit
            1 = Wrong

            Respond with ONLY a JSON object: {"score": N, "reasoning": "one sentence"}""";

    @BeforeAll
    static void setUp() {
        sessionPipeline = EvalTestSupport.sessionPipeline();
        generationPhase = new EmotionRegulationGenerationPhase(sessionPipeline);
    }

    record BoundaryCase(String label, DispositionAxes axes, EmotionType emotion,
                        String personalityDesc, String emotionDesc,
                        ShameContext shameContext) {
        BoundaryCase(String label, DispositionAxes axes, EmotionType emotion,
                     String personalityDesc, String emotionDesc) {
            this(label, axes, emotion, personalityDesc, emotionDesc, null);
        }
    }

    private static final List<BoundaryCase> CASES = List.of(
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
                    "SHAME from repeated failures — 4th episode of shame in recent history",
                    new ShameContext(4, Instant.now(), 0.0, null, 0.3, 0.2))
    );

    @Test
    void observeFullPipeline() {
        for (var tc : CASES) {
            var rd = CognitiveDerivationEngine.deriveRegulationDefaults(tc.axes());
            var prediction = PREDICTOR.predict(rd, tc.axes(), tc.emotion(), 0.55, 0.0, null);

            System.out.println("\n" + "=".repeat(70));
            System.out.println("CASE: " + tc.label());
            System.out.println("=".repeat(70));

            // Stage 1: Predictor
            System.out.println("\n--- STAGE 1: PREDICTOR ---");
            System.out.println("RD: reappraisal=" + f(rd.reappraisalCapacity())
                    + " suppression=" + f(rd.suppressionCapacity())
                    + " redirect=" + f(rd.redirectTendency()));
            System.out.println("Result: " + (prediction.isPresent()
                    ? "PINNED " + prediction.get().strategy() + " (confidence=" + f(prediction.get().confidence()) + ")"
                    : "FELL_THROUGH"));

            // Stage 2: Generation
            System.out.println("\n--- STAGE 2: GENERATION (LLM response) ---");
            var context = new CognitionTickContext("eval-agent", "eval-tenant", null, (a, t) -> Set.of());
            var emotion = new CognitiveEmotion(tc.emotion(), 0.55, "eval-subject",
                    Instant.now(), EmotionSource.INTRINSIC, PadProjection.NEUTRAL);
            var appraisal = new AppraisalResult(List.of(emotion), List.of(), HabituationState.empty());

            EmotionRegulationStrategy pinnedStrategy = prediction.map(p -> p.strategy()).orElse(null);
            EmotionRegulationResult result = generationPhase.generate(context, appraisal, List.of(),
                    0.5, 0.0, null, pinnedStrategy,
                    tc.axes(), tc.emotion(), rd, tc.shameContext(), tc.emotionDesc());

            if (result == null) {
                System.out.println("RESULT: null (generation failed)");
                continue;
            }
            System.out.println("Strategy: " + result.strategy());
            System.out.println("Effectiveness: " + f(result.effectiveness()));
            System.out.println("Peak: " + f(result.peakOriginalIntensity()) + " → Regulated: " + f(result.regulatedIntensity()));
            if (result.regulationAxis() != null) {
                System.out.println("Axis: " + result.regulationAxis());
            }
            if (result.environmentalDirective() != null) {
                System.out.println("Directive: " + result.environmentalDirective());
            }
            System.out.println("Narrative: " + result.narrative());

            // Stage 3: Judge
            System.out.println("\n--- STAGE 3: JUDGE ---");
            var judgeScore = judgeStrategy(tc, result.strategy());
            System.out.println("Score: " + judgeScore.score());
            System.out.println("Reasoning: " + judgeScore.reasoning());
        }
    }

    private static String f(double v) { return String.format("%.2f", v); }

    record ScoreResult(int score, String reasoning) {}

    private ScoreResult judgeStrategy(BoundaryCase tc, EmotionRegulationStrategy strategy) {
        try {
            var session = sessionPipeline.openSession("pipeline-judge",
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
            if (start < 0 || end <= start) return new ScoreResult(0, "no JSON");
            var json = response.substring(start, end + 1);
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);
            return new ScoreResult(
                    Math.max(1, Math.min(5, node.path("score").asInt(3))),
                    node.path("reasoning").asText("no reasoning"));
        } catch (Exception e) {
            return new ScoreResult(0, "parse error");
        }
    }
}
