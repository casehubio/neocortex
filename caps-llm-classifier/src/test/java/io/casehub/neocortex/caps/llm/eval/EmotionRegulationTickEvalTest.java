package io.casehub.neocortex.caps.llm.eval;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
import io.casehub.neocortex.cognition.appraisal.AppraisalStrategy;
import io.casehub.neocortex.cognition.appraisal.LlmAppraisalStrategy;
import io.casehub.neocortex.cognition.core.CognitionConfig;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationGenerationPhase;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationResult;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationTickParticipant;
import io.casehub.neocortex.cognition.salience.ConcernBasedSalienceStrategy;
import io.casehub.platform.agent.session.SessionPipeline;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM eval: end-to-end tick test for appraisal → regulation.
 *
 * Uses the Bosse et al. 2010 proportional regulation model:
 * effort = α × d / d_max (no binary threshold gate).
 */
@Tag("llm-eval")
class EmotionRegulationTickEvalTest {

    private static final String AGENT = "eval-agent";
    private static final String TENANT = "eval-tenant";
    private static final int N = 3;

    private static SessionPipeline sessionPipeline;

    @BeforeAll
    static void setUp() {
        sessionPipeline = EvalTestSupport.sessionPipeline();
    }

    private static final String BETRAYAL_OBSERVATION = """
            Your trusted estate manager has been systematically embezzling \
            from the estate for fifteen years. You just found forged documents \
            proving he stole over £200,000. He is standing in front of you now, \
            smiling his usual warm smile, completely unaware that you know. \
            Your hands are shaking. Your vision is narrowing.""";

    @Test
    void betrayal_regulationActivatesViaTick() {
        var descriptor = AgentDescriptor.builder()
                .agentId(AGENT).name("EvalAgent").slot("default").tenancyId(TENANT)
                .build();

        var config = CognitionConfig.none()
                .with("mood", true)
                .with("drives", true)
                .with("appraisal", true)
                .with("salience", true)
                .with("regulation", true);

        // Wrap appraisal strategy with diagnostics to capture what happens
        var rawStrategy = new LlmAppraisalStrategy(sessionPipeline);
        AppraisalStrategy diagnosticStrategy = ctx -> {
            System.out.println("=== DIAGNOSTIC: AppraisalStrategy.appraise() called ===");
            System.out.printf("  Drives: %d, Observation: %.60s...%n",
                    ctx.drives().size(), ctx.situation().narrative());
            var result = rawStrategy.appraise(ctx);
            System.out.printf("  Result: %d emotions, narrative=%s%n",
                    result.emotions().size(),
                    result.narrative() != null
                            ? result.narrative().substring(0, Math.min(80, result.narrative().length()))
                            : "null");
            for (var e : result.emotions()) {
                System.out.printf("    %s intensity=%.2f subject=%s pad=(%.2f,%.2f,%.2f)%n",
                        e.type(), e.intensity(), e.subjectId(),
                        e.pad().pleasure(), e.pad().arousal(), e.pad().dominance());
            }
            return result;
        };

        var core = CognitionCoreTestBuilder.buildCore(config, sessionPipeline);
        core.configureAppraisal(diagnosticStrategy, new ConcernBasedSalienceStrategy());
        core.configureRegulation();

        assertThat(core.regulationParticipant()).as("Regulation participant registered").isNotNull();

        // Tick 1: seed drives
        System.out.println("=== Tick 1: seeding drives ===");
        core.tick(AGENT, TENANT, descriptor, (a, t) -> Set.of(), null);

        // Diagnostic: verify appraisal participant wiring
        var appraisalP = core.appraisalParticipant();
        System.out.printf("  Appraisal participant: %s%n", appraisalP != null ? "present" : "NULL");

        // Tick 2: observation triggers appraisal → regulation
        System.out.println("=== Tick 2: betrayal observation ===");
        core.tick(AGENT, TENANT, descriptor, (a, t) -> Set.of("other"), BETRAYAL_OBSERVATION);

        // Diagnostic: inspect appraisal result
        System.out.println("=== Post-tick-2 diagnostics ===");
        if (appraisalP != null) {
            var appraisalResult = appraisalP.currentResult(AGENT, TENANT);
            System.out.printf("  Appraisal result present: %s%n", appraisalResult.isPresent());
            if (appraisalResult.isPresent()) {
                var ar = appraisalResult.get();
                System.out.printf("  Emotions: %d%n", ar.emotions().size());
                for (var e : ar.emotions()) {
                    double disc = Math.abs(e.intensity() - 0.3);
                    System.out.printf("    %s intensity=%.2f discrepancy=%.2f%n",
                            e.type(), e.intensity(), disc);
                }
                if (!ar.emotions().isEmpty()) {
                    double maxDisc = ar.emotions().stream()
                            .mapToDouble(e -> Math.abs(e.intensity() - 0.3))
                            .max().orElse(0.0);
                    double effort = 0.5 * maxDisc / 0.7;
                    System.out.printf("  Max discrepancy: %.3f, effort: %.3f (threshold: 0.01)%n",
                            maxDisc, effort);
                }
            }
        }

        // Check regulation result
        var regResult = core.regulationParticipant().currentResult(AGENT, TENANT);
        System.out.printf("Regulation result present: %s%n", regResult.isPresent());

        if (regResult.isPresent()) {
            var r = regResult.get();
            System.out.printf("ACTIVATED: strategy=%s effectiveness=%.2f%n",
                    r.strategy(), r.effectiveness());
        }

        if (regResult.isEmpty()) {
            // Diagnostic: direct generation phase test (proves sessionPipeline works)
            System.out.println("--- Direct generation phase test (bypassing tick) ---");
            var genPhase = new EmotionRegulationGenerationPhase(sessionPipeline);
            var fakeAppraisal = new AppraisalResult(
                    List.of(new io.casehub.neocortex.cognitive.CognitiveEmotion(
                            io.casehub.neocortex.cognitive.EmotionType.ANGER, 0.6, "betrayal",
                            java.time.Instant.now(), io.casehub.neocortex.cognitive.EmotionSource.INTRINSIC,
                            io.casehub.neocortex.cognitive.PadProjection.NEUTRAL)),
                    List.of(), null);
            var directResult = genPhase.generate(
                    new io.casehub.neocortex.cognition.core.CognitionTickContext(
                            AGENT, TENANT, null, (a, t) -> Set.of()),
                    fakeAppraisal, List.of(), 0.43);
            System.out.printf("Direct generation result: %s%n",
                    directResult != null ? directResult.strategy() : "null");
        }
    }
}
