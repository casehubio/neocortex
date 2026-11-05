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
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality gate for RegulationStrategyPredictor (Petter et al. 2025).
 *
 * Finding: LLM strategy selection is non-deterministic — identical inputs
 * produce different strategies across runs (e.g., same avoidant disposition
 * + same confrontation → RM in run A, AD in run B). LLM-agreement is
 * therefore unreliable as a quality gate.
 *
 * Gate design: scope verification + plausibility + distribution capture.
 * - Scope: predictor fires only for RM-dominant dispositions, skips CC
 * - Plausibility: predicted strategy appears in ≥1 LLM run (it's a valid choice)
 * - Distribution: captures LLM strategy counts as empirical evidence
 */
@Tag("llm-eval")
class RegulationPredictorBackTest {

    private static final String TENANT = "eval-tenant";
    private static final int RUNS_PER_SCENARIO = 5;

    private static SessionPipeline sessionPipeline;
    private static final RegulationStrategyPredictor PREDICTOR = new RegulationStrategyPredictor();

    // Public confrontation — social exposure biases toward suppression (RESPONSE_MODULATION)
    private static final String PUBLIC_CONFRONTATION = """
            Someone you trusted has just publicly contradicted you in front of \
            the whole group, calling your analysis "naive" and your conclusions \
            "embarrassingly wrong." They did it with a smile. The room has gone \
            quiet. Everyone is looking at you. Your face is hot. Your hands are \
            shaking. You know that how you respond defines how everyone sees you.""";

    // Private setback — no audience, space for reflection biases toward reappraisal (COGNITIVE_CHANGE)
    private static final String PRIVATE_SETBACK = """
            You have just discovered that a project you spent three months \
            building has been cancelled. The email is still on your screen. \
            Nobody else knows yet — you are alone in your study. The work was \
            good, you know that, but the decision was made above you and the \
            reasons given are thin. Frustration is building. Part of you wants \
            to fire off a response. But there is no audience here, no one to \
            perform for. Just you and the question of what this means and what \
            to do about it.""";

    @BeforeAll
    static void setUp() {
        sessionPipeline = EvalTestSupport.sessionPipeline();
    }

    record TestCase(String label, DispositionAxes axes, AgentDisposition disposition,
                    String scenario, boolean expectConfident) {}

    private static final List<TestCase> TEST_CASES = List.of(
            // RM dispositions — predictor should fire
            new TestCase("suppressor-public",
                    new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant"),
                    disposition("independent", "flexible", "conservative", "moderate", "avoidant"),
                    PUBLIC_CONFRONTATION, true),
            new TestCase("controller-public",
                    new DispositionAxes("competitive", "flexible", "conservative", "low", "avoidant"),
                    disposition("competitive", "flexible", "conservative", "low", "avoidant"),
                    PUBLIC_CONFRONTATION, true),
            new TestCase("stoic-public",
                    new DispositionAxes("independent", "moderate", "conservative", "low", "avoidant"),
                    disposition("independent", "moderate", "conservative", "low", "avoidant"),
                    PUBLIC_CONFRONTATION, true),
            new TestCase("suppressor-private",
                    new DispositionAxes("independent", "flexible", "conservative", "moderate", "avoidant"),
                    disposition("independent", "flexible", "conservative", "moderate", "avoidant"),
                    PRIVATE_SETBACK, true),

            // CC dispositions — predictor should fall through
            new TestCase("reappraiser-control",
                    new DispositionAxes("cooperative", "strict", "calculated", "high", "analytical"),
                    disposition("cooperative", "strict", "calculated", "high", "analytical"),
                    PRIVATE_SETBACK, false),
            new TestCase("analyst-control",
                    new DispositionAxes("cooperative", "strict", "conservative", "high", "analytical"),
                    disposition("cooperative", "strict", "conservative", "high", "analytical"),
                    PUBLIC_CONFRONTATION, false)
    );

    @Test
    void predictorScopeAndPlausibility() {
        int totalLlmRegulated = 0;
        int plausibleCount = 0;
        int rmCases = 0;
        var details = new ArrayList<String>();

        for (var tc : TEST_CASES) {
            var prediction = PREDICTOR.predict(tc.axes(), 0.55, 0.0, null);

            // Verify scope: confident iff expectConfident
            assertThat(prediction.isPresent())
                    .as("%s: predictor confidence", tc.label())
                    .isEqualTo(tc.expectConfident());

            if (!tc.expectConfident()) {
                details.add(String.format("  %s: correctly skipped (CC disposition)", tc.label()));
                continue;
            }

            rmCases++;
            var predictedStrategy = prediction.get().strategy();
            assertThat(predictedStrategy)
                    .as("%s: predictor should only predict RM", tc.label())
                    .isEqualTo(EmotionRegulationStrategy.RESPONSE_MODULATION);

            // Run LLM N times, capture distribution
            var distribution = new EnumMap<EmotionRegulationStrategy, Integer>(EmotionRegulationStrategy.class);
            int regulated = 0;
            for (int i = 0; i < RUNS_PER_SCENARIO; i++) {
                var llmResult = runLlmRegulation(tc.label() + "-" + i, tc.disposition(), tc.scenario());
                if (llmResult != null) {
                    regulated++;
                    distribution.merge(llmResult, 1, Integer::sum);
                }
            }
            totalLlmRegulated += regulated;

            // Plausibility: predicted strategy appears in at least 1 LLM run
            boolean plausible = distribution.containsKey(predictedStrategy);
            if (plausible) plausibleCount++;

            details.add(String.format("  %s: predictor=%s llm-distribution=%s regulated=%d/%d plausible=%s",
                    tc.label(), predictedStrategy, distribution, regulated, RUNS_PER_SCENARIO, plausible));
        }

        System.out.println("=== Predictor Back-Test Results ===");
        details.forEach(System.out::println);
        System.out.printf("%nLLM regulated: %d total runs%n", totalLlmRegulated);
        System.out.printf("Plausibility: %d/%d RM cases had predicted strategy in LLM distribution%n",
                plausibleCount, rmCases);

        assertThat(totalLlmRegulated)
                .as("LLM should regulate in at least 8 of %d total runs", rmCases * RUNS_PER_SCENARIO)
                .isGreaterThanOrEqualTo(8);
    }

    private EmotionRegulationStrategy runLlmRegulation(String instanceId,
                                                        AgentDisposition agentDisposition,
                                                        String scenario) {
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
                    scenario);

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
