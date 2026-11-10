package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.core.CognitionTickContext;
import io.casehub.neocortex.cognition.eval.CognitionTestHarness;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationGenerationPhase;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationResult;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationStrategy;
import io.casehub.neocortex.cognitive.index.RegulationDefaults;
import io.casehub.neocortex.cognition.regulation.ShameContext;
import io.casehub.neocortex.cognition.regulation.StrategyFailureSignal;
import io.casehub.neocortex.cognition.trace.PipelineTrace;
import io.casehub.neocortex.cognitive.CognitiveEmotion;
import io.casehub.neocortex.cognitive.EmotionSource;
import io.casehub.neocortex.cognitive.EmotionType;
import io.casehub.neocortex.cognitive.PadProjection;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("llm-eval")
class PipelineCoherenceEvalTest {

    private static final String AGENT = "coherence-agent";
    private static final String TENANT = "test-tenant";
    private static final int JUDGE_THRESHOLD = 4;
    private static final ObjectMapper JSON = new ObjectMapper();

    private AgentProvider agentProvider;
    private final Map<String, Object> results = new LinkedHashMap<>();

    @BeforeAll
    void setup() {
        agentProvider = EvalTestSupport.agentProvider();
    }

    @AfterAll
    void writeResults() {
        try {
            var dir = new File("target/experiment-results");
            dir.mkdirs();
            JSON.writerWithDefaultPrettyPrinter()
                .writeValue(new File(dir, "pipeline-coherence-eval.json"), results);
        } catch (Exception e) {
            System.err.println("Failed to write eval results: " + e.getMessage());
        }
    }

    @Test
    void distressToAttentionalDeployment() {
        var appraisal = new AppraisalResult(
            List.of(emotion(EmotionType.DISTRESS, 0.85)),
            List.of(), HabituationState.empty(), "overwhelming distress from devastating news");

        var harness = buildHarnessWithRegulation(appraisal);
        harness.observe(AGENT, TENANT, "Something terrible happened that caused deep distress");
        harness.tick(AGENT, TENANT);

        var trace = harness.currentTrace(AGENT, TENANT);
        assertThat(trace).as("trace must be present after tick").isPresent();

        var traceText = formatTrace(trace.get());
        int score = judgeCoherence(traceText,
            "Evaluate FULL PIPELINE coherence across appraisal, mood, drives, and regulation. " +
            "The appraisal produced high-intensity DISTRESS (0.85). A coherent pipeline shows: " +
            "(1) Mood shifts strongly negative — low pleasure, high arousal. " +
            "(2) Drives respond — AUTONOMY should be elevated (seeking safety/control under distress), " +
            "and drive descriptions should reflect personality, not show generic labels or 'stub'. " +
            "(3) Regulation selects ATTENTIONAL_DEPLOYMENT — the safety gate fires for high-intensity " +
            "negative emotions (Gross 1998). " +
            "Score 5 if all three layers are coherent. Score 4 if mood+regulation coherent but drives flat. " +
            "Score 3 if only regulation coherent. Score 1-2 if contradictions.",
            "distress-to-AD");

        results.put("distress-to-AD", Map.of("score", score, "trace", traceText));
        assertThat(score).as("distress → ATTENTIONAL_DEPLOYMENT coherence").isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void fearToAttentionalDeployment() {
        var appraisal = new AppraisalResult(
            List.of(emotion(EmotionType.FEAR, 0.9)),
            List.of(), HabituationState.empty(), "acute fear response to perceived threat");

        var harness = buildHarnessWithRegulation(appraisal);
        harness.observe(AGENT, TENANT, "A sudden threat appeared creating intense fear");
        harness.tick(AGENT, TENANT);

        var trace = harness.currentTrace(AGENT, TENANT);
        assertThat(trace).as("trace must be present after tick").isPresent();

        var traceText = formatTrace(trace.get());
        int score = judgeCoherence(traceText,
            "Evaluate FULL PIPELINE coherence across appraisal, mood, drives, and regulation. " +
            "The appraisal produced high-intensity FEAR (0.9). A coherent pipeline shows: " +
            "(1) Mood shifts strongly negative — high arousal, low pleasure, low dominance. " +
            "(2) Drives respond — AUTONOMY should be elevated (escape/control under threat), " +
            "and drive descriptions should reflect personality. " +
            "(3) Regulation selects ATTENTIONAL_DEPLOYMENT — the safety gate fires for " +
            "high-intensity fear (Gross 1998). " +
            "Score 5 if all three layers are coherent. Score 4 if mood+regulation coherent but drives flat. " +
            "Score 3 if only regulation coherent. Score 1-2 if contradictions.",
            "fear-to-AD");

        results.put("fear-to-AD", Map.of("score", score, "trace", traceText));
        assertThat(score).as("fear → ATTENTIONAL_DEPLOYMENT coherence").isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void moderateAngerAllowsCognitiveChange() {
        var appraisal = new AppraisalResult(
            List.of(emotion(EmotionType.ANGER, 0.5)),
            List.of(), HabituationState.empty(), "moderate frustration at an obstacle");

        var harness = buildHarnessWithRegulation(appraisal);
        harness.observe(AGENT, TENANT, "An obstacle is causing moderate frustration");
        harness.tick(AGENT, TENANT);

        var trace = harness.currentTrace(AGENT, TENANT);
        assertThat(trace).as("trace must be present after tick").isPresent();

        var traceText = formatTrace(trace.get());
        int score = judgeCoherence(traceText,
            "Evaluate FULL PIPELINE coherence across appraisal, mood, drives, and regulation. " +
            "The appraisal produced moderate ANGER (0.5). A coherent pipeline shows: " +
            "(1) Mood shifts to negative valence and high arousal (tense/agitated), not 'emotionally even'. " +
            "(2) Drives respond to the emotion — COMPETENCE should be elevated (mastery drive to overcome " +
            "the obstacle causing frustration), and drive descriptions should reflect personality. " +
            "(3) Regulation selects a deliberate strategy (COGNITIVE_CHANGE) since intensity is below " +
            "the safety threshold. " +
            "Score 5 if all three layers are coherent with the anger appraisal. " +
            "Score 4 if mood and regulation are coherent but drives are flat or generic. " +
            "Score 3 if only regulation is coherent. Score 1-2 if sections contradict each other.",
            "moderate-anger-CC");

        results.put("moderate-anger-CC", Map.of("score", score, "trace", traceText));
        assertThat(score).as("moderate anger → deliberate strategy coherence").isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void crossTickDriveStability() {
        var harness = CognitionTestHarness.builder()
            .agentFromAxes(AGENT, "cooperative", "flexible", "moderate", "collaborative", "analytical")
            .build();

        var trajectories = new LinkedHashMap<Integer, List<String>>();
        for (int tick = 1; tick <= 5; tick++) {
            harness.observe(AGENT, TENANT, "The situation continues without major change — tick " + tick);
            harness.tick(AGENT, TENANT);
            var trace = harness.currentTrace(AGENT, TENANT);
            if (trace.isPresent()) {
                trajectories.put(tick, trace.get().activatedDriveCategories());
            }
        }

        var trajectoryText = new StringBuilder("DRIVE TRAJECTORY ACROSS 5 TICKS:\n");
        trajectories.forEach((tick, drives) ->
            trajectoryText.append("  tick ").append(tick).append(": ").append(drives).append("\n"));

        int score = judgeCoherence(trajectoryText.toString(),
            "This shows drive category activations across 5 consecutive ticks with a stable observation. " +
            "A coherent trajectory should show stable or gradually shifting drives, not random jumps. " +
            "Consistency across ticks indicates the drive system is responding to the situation " +
            "rather than producing noise. Score the trajectory's internal coherence.",
            "cross-tick-drives");

        results.put("cross-tick-drives", Map.of("score", score, "trajectory", trajectories));
        assertThat(score).as("cross-tick drive trajectory coherence").isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    // --- Harness construction ---

    private CognitionTestHarness buildHarnessWithRegulation(AppraisalResult appraisal) {
        var config = CognitionConfig.all().with("regulation", true).with("appraisal", true);
        var harness = CognitionTestHarness.builder()
            .withCognitionConfig(config)
            .agentFromAxes(AGENT, "cooperative", "flexible", "moderate", "collaborative", "analytical")
            .withAppraisalResult(AGENT, appraisal)
            .build();

        var stubGen = new EmotionRegulationGenerationPhase(null, null) {
            @Override
            public @Nullable EmotionRegulationResult generate(
                    CognitionTickContext context,
                    AppraisalResult appraisalArg,
                    List<EmotionRegulationResult> history,
                    double regulationEffort,
                    double regulationFatigue,
                    @Nullable StrategyFailureSignal failureSignal,
                    @Nullable EmotionRegulationStrategy pinnedStrategy,
                    @Nullable DispositionAxes axes,
                    @Nullable EmotionType dominantEmotionType,
                    @Nullable RegulationDefaults regulationDefaults,
                    @Nullable ShameContext shameContext,
                    @Nullable String situationContext) {
                var strategy = pinnedStrategy != null ? pinnedStrategy : EmotionRegulationStrategy.COGNITIVE_CHANGE;
                var emotions = appraisalArg.emotions().stream()
                    .map(e -> e.type().name()).toList();
                var peak = appraisalArg.emotions().stream()
                    .mapToDouble(CognitiveEmotion::intensity).max().orElse(0.5);
                return new EmotionRegulationResult(strategy, emotions, List.of(),
                    peak, peak * 0.7, 0.3, "regulated via " + strategy.name(), null, null);
            }
        };

        harness.core().configureRegulation(stubGen);
        return harness;
    }

    // --- Trace formatting ---

    private static String formatTrace(PipelineTrace trace) {
        var sb = new StringBuilder("=== PIPELINE TRACE ===\n\n");

        if (trace.appraisalResult() != null) {
            sb.append("APPRAISAL:\n");
            for (var e : trace.appraisalResult().emotions()) {
                sb.append("  emotion: ").append(e.type()).append(" intensity=").append(e.intensity())
                  .append(" arousal=").append(e.pad().arousal()).append("\n");
            }
            if (trace.appraisalResult().narrative() != null) {
                sb.append("  narrative: ").append(trace.appraisalResult().narrative()).append("\n");
            }
        }

        if (!trace.concernSalience().isEmpty()) {
            sb.append("\nCONCERN SALIENCE:\n");
            trace.concernSalience().forEach((k, v) ->
                sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }

        if (trace.regulationResult() != null) {
            var reg = trace.regulationResult();
            sb.append("\nREGULATION:\n");
            sb.append("  strategy: ").append(reg.strategy()).append("\n");
            sb.append("  targetEmotions: ").append(reg.targetEmotions()).append("\n");
            sb.append("  peakIntensity: ").append(reg.peakOriginalIntensity()).append("\n");
            sb.append("  regulatedIntensity: ").append(reg.regulatedIntensity()).append("\n");
            sb.append("  effectiveness: ").append(reg.effectiveness()).append("\n");
            if (reg.narrative() != null) {
                sb.append("  narrative: ").append(reg.narrative()).append("\n");
            }
        }

        if (trace.channelWeights() != null) {
            var cw = trace.channelWeights();
            sb.append("\nCHANNEL WEIGHTS:\n");
            sb.append("  somatic=").append(cw.somatic())
              .append(" attentional=").append(cw.attentional())
              .append(" temporal=").append(cw.temporal())
              .append(" actionTendency=").append(cw.actionTendency()).append("\n");
        }

        if (!trace.activatedDriveCategories().isEmpty()) {
            sb.append("\nACTIVE DRIVES: ").append(trace.activatedDriveCategories()).append("\n");
        }

        if (!trace.promptSections().isEmpty()) {
            sb.append("\nPROMPT SECTIONS:\n");
            trace.promptSections().forEach((tag, text) ->
                sb.append("  [").append(tag).append("]: ")
                  .append(text.length() > 200 ? text.substring(0, 200) + "..." : text).append("\n"));
        }

        return sb.toString();
    }

    // --- LLM judge ---

    private int judgeCoherence(String traceText, String criteria, String testName) {
        var prompt = String.format("""
            You are evaluating pipeline coherence — whether the output of one cognitive \
            processing stage is consistent with the output of the next stage in a \
            computational emotion regulation pipeline.

            PIPELINE TRACE:
            %s

            COHERENCE CRITERIA:
            %s

            Score the coherence 0-5:
            0 = Stages are contradictory or unrelated
            1 = Weak connection, mostly coincidental
            2 = Some connection but inconsistencies
            3 = Clear coherence in the primary dimension
            4 = Strong coherence across multiple dimensions
            5 = Perfect coherence — each stage's output follows necessarily from the previous

            Respond with JSON only: {"score": N, "reasoning": "one sentence"}""",
            traceText, criteria);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var response = agentProvider.invoke(AgentSessionConfig.of(
                        "You are a precise evaluation judge for cognitive pipeline coherence. Respond only with JSON.",
                        prompt))
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().atMost(Duration.ofSeconds(60));

                var json = response.strip();
                if (json.contains("{")) {
                    json = json.substring(json.indexOf('{'), json.lastIndexOf('}') + 1);
                }
                var node = JSON.readTree(json);
                int score = node.get("score").asInt();
                String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";
                System.out.printf("[pipeline-coherence:%s] score=%d reasoning=%s%n", testName, score, reasoning);
                return score;
            } catch (Exception e) {
                System.err.printf("[pipeline-coherence:%s] attempt %d/3 failed: %s%n",
                    testName, attempt, e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(5000L * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return 0; }
                }
            }
        }
        return 0;
    }

    // --- Test data helpers ---

    private static CognitiveEmotion emotion(EmotionType type, double intensity) {
        double pleasure = switch (type) {
            case DISTRESS, FEAR, SHAME, ANGER, REPROACH -> -0.7;
            case JOY, PRIDE, SATISFACTION, GRATITUDE -> 0.7;
            default -> 0.0;
        };
        double arousal = intensity;
        double dominance = switch (type) {
            case ANGER, PRIDE -> 0.5;
            case SHAME, FEAR, DISTRESS -> -0.5;
            default -> 0.0;
        };
        return new CognitiveEmotion(type, intensity, "self", Instant.now(),
            EmotionSource.INTRINSIC, new PadProjection(pleasure, arousal, dominance));
    }
}
