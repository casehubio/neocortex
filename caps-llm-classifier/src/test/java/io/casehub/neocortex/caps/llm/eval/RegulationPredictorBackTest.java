package io.casehub.neocortex.caps.llm.eval;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.eidos.api.AgentDisposition;
import io.casehub.eidos.api.DispositionValue;
import io.casehub.neocortex.cognition.appraisal.LlmAppraisalStrategy;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationStrategy;
import io.casehub.neocortex.cognition.regulation.RegulationStrategyPredictor;
import io.casehub.neocortex.cognition.salience.ConcernBasedSalienceStrategy;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import io.casehub.platform.agent.session.SessionPipeline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality gate: RegulationStrategyPredictor must agree with LLM strategy
 * selections at ≥80% on confident predictions (Petter et al. 2025 §4).
 *
 * Uses moderate-intensity scenarios so personality drives selection rather
 * than contextual overrides. Tests clear-signal dispositions where the
 * predictor IS confident, and verifies agreement with full LLM regulation.
 */
@Tag("llm-eval")
class RegulationPredictorBackTest {

    private static final String TENANT = "eval-tenant";
    private static final int RUNS_PER_SCENARIO = 3;

    private static SessionPipeline sessionPipeline;
    private static final RegulationStrategyPredictor PREDICTOR = new RegulationStrategyPredictor();

    // High-enough intensity to reliably trigger regulation, but not so extreme
    // that contextual overrides dominate personality preferences
    private static final String CONFRONTATION = """
            Someone you trusted has just publicly contradicted you in front of \
            the whole group, calling your analysis "naive" and your conclusions \
            "embarrassingly wrong." They did it with a smile, as though they \
            were doing you a favour by correcting you. The room has gone quiet. \
            Everyone is looking at you, waiting for your response. Your face is \
            hot. Your hands are shaking slightly. You can feel the anger rising \
            but you know that how you respond in the next ten seconds will \
            define how everyone in this room sees you.""";

    @BeforeAll
    static void setUp() {
        sessionPipeline = EvalTestSupport.sessionPipeline();
    }

    record TestCase(String label, DispositionAxes axes, AgentDisposition disposition) {}

    private static final List<TestCase> CLEAR_SIGNAL_CASES = List.of(
            // Strong COGNITIVE_CHANGE signal — strict + analytical + cooperative + high autonomy
            new TestCase("reappraiser",
                    new DispositionAxes("cooperative", "strict", "calculated", "high", "analytical"),
                    disposition("cooperative", "strict", "calculated", "high", "analytical")),

            // Strong RESPONSE_MODULATION signal — avoidant + conservative + independent + flexible
            new TestCase("suppressor",
                    new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant"),
                    disposition("independent", "flexible", "conservative", "moderate", "avoidant")),

            // RESPONSE_MODULATION — avoidant + conservative + flexible (strong suppression)
            new TestCase("controller",
                    new DispositionAxes("competitive", "flexible", "conservative", "low", "avoidant"),
                    disposition("competitive", "flexible", "conservative", "low", "avoidant")),

            // COGNITIVE_CHANGE — cooperative + cooperative conflict + high autonomy
            new TestCase("empathic-reframer",
                    new DispositionAxes("cooperative", "strict", "calculated", "high", "cooperative"),
                    disposition("cooperative", "strict", "calculated", "high", "cooperative")),

            // RESPONSE_MODULATION — avoidant + conservative + independent
            new TestCase("stoic",
                    new DispositionAxes("independent", "moderate", "conservative", "low", "avoidant"),
                    disposition("independent", "moderate", "conservative", "low", "avoidant")),

            // COGNITIVE_CHANGE — strict + analytical + cooperative social
            new TestCase("analyst",
                    new DispositionAxes("cooperative", "strict", "conservative", "high", "analytical"),
                    disposition("cooperative", "strict", "conservative", "high", "analytical"))
    );

    @Test
    void predictorAgreesWithLlmAtEightyPercent() {
        int totalConfident = 0;
        int totalAgreed = 0;
        var details = new ArrayList<String>();

        for (var tc : CLEAR_SIGNAL_CASES) {
            var prediction = PREDICTOR.predict(tc.axes(), 0.55, 0.0, null);
            if (prediction.isEmpty()) {
                details.add(String.format("  %s: predictor not confident — skipped", tc.label()));
                continue;
            }

            var predictedStrategy = prediction.get().strategy();

            for (int i = 0; i < RUNS_PER_SCENARIO; i++) {
                var llmResult = runLlmRegulation(tc.label() + "-" + i, tc.disposition());
                if (llmResult == null) {
                    details.add(String.format("  %s run %d: LLM did not regulate", tc.label(), i + 1));
                    continue;
                }

                totalConfident++;
                boolean agreed = llmResult == predictedStrategy;
                if (agreed) totalAgreed++;
                details.add(String.format("  %s run %d: predictor=%s llm=%s %s",
                        tc.label(), i + 1, predictedStrategy, llmResult,
                        agreed ? "AGREE" : "DISAGREE"));
            }
        }

        System.out.println("=== Predictor Back-Test Results ===");
        details.forEach(System.out::println);

        double agreementRate = totalConfident > 0 ? (double) totalAgreed / totalConfident : 0.0;
        System.out.printf("%nAgreement: %d/%d (%.0f%%)%n", totalAgreed, totalConfident, agreementRate * 100);

        assertThat(totalConfident)
                .as("Need at least 10 confident-vs-LLM comparisons for a meaningful gate")
                .isGreaterThanOrEqualTo(10);
        assertThat(agreementRate)
                .as("Predictor must agree with LLM at ≥80%% (Petter 2025 quality gate)")
                .isGreaterThanOrEqualTo(0.80);
    }

    private EmotionRegulationStrategy runLlmRegulation(String instanceId,
                                                        AgentDisposition agentDisposition) {
        try {
            var descriptor = AgentDescriptor.builder()
                    .agentId(instanceId).name("BackTestAgent")
                    .slot("eval-slot").tenancyId(TENANT)
                    .disposition(agentDisposition)
                    .build();

            var config = CognitionConfig.none()
                    .with("mood", true)
                    .with("drives", true)
                    .with("appraisal", true)
                    .with("salience", true)
                    .with("regulation", true);

            var core = CognitionCoreTestBuilder.buildCore(config, sessionPipeline);
            core.configureAppraisal(
                    new LlmAppraisalStrategy(sessionPipeline),
                    new ConcernBasedSalienceStrategy());
            // No registry → predictor disabled → pure LLM regulation
            core.configureRegulation();

            core.tick(instanceId, TENANT, descriptor, (a, t) -> Set.of(), null);
            core.tick(instanceId, TENANT, descriptor, (a, t) -> Set.of("other"),
                    CONFRONTATION);

            return core.regulationParticipant().currentResult(instanceId, TENANT)
                    .map(r -> r.strategy()).orElse(null);
        } catch (Exception e) {
            System.err.printf("  LLM regulation failed for %s: %s%n", instanceId, e.getMessage());
            return null;
        }
    }

    private static AgentDisposition disposition(String social, String rules, String risk,
                                                 String autonomy, String conflict) {
        return new AgentDisposition(
                List.of(DispositionValue.of(social)),
                List.of(DispositionValue.of(rules)),
                List.of(DispositionValue.of(risk)),
                List.of(DispositionValue.of(autonomy)),
                List.of(DispositionValue.of(conflict)),
                false, List.of(), List.of(), null, null);
    }
}
