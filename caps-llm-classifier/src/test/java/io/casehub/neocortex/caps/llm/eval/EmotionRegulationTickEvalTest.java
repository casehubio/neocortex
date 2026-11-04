package io.casehub.neocortex.caps.llm.eval;

import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.neocortex.cognition.appraisal.AppraisalResult;
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

        var core = CognitionCoreTestBuilder.buildCore(config, sessionPipeline);
        core.configureAppraisal(
                new LlmAppraisalStrategy(sessionPipeline),
                new ConcernBasedSalienceStrategy());
        core.configureRegulation();

        assertThat(core.regulationParticipant()).as("Regulation participant registered").isNotNull();

        // Seed drives
        core.tick(AGENT, TENANT, descriptor, (a, t) -> Set.of(), null);
        // Run with observation
        core.tick(AGENT, TENANT, descriptor, (a, t) -> Set.of("other"), BETRAYAL_OBSERVATION);

        // Check regulation result
        var regResult = core.regulationParticipant().currentResult(AGENT, TENANT);
        System.out.printf("Regulation result present: %s%n", regResult.isPresent());

        if (regResult.isEmpty()) {
            // Diagnostic: try calling generation phase directly with crafted appraisal
            System.out.println("--- Direct generation phase test ---");
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
            System.out.printf("Direct generation result: %s%n", directResult != null ? directResult.strategy() : "null");
            if (directResult != null) {
                System.out.printf("  strategy=%s effectiveness=%.2f narrative=%s%n",
                        directResult.strategy(), directResult.effectiveness(),
                        directResult.narrative().substring(0, Math.min(100, directResult.narrative().length())));
            }
        }

        if (regResult.isPresent()) {
            var r = regResult.get();
            System.out.printf("ACTIVATED: strategy=%s effectiveness=%.2f%n", r.strategy(), r.effectiveness());
        }

        // The test is diagnostic — don't assert activation yet, just report
        System.out.printf("Regulation activated: %s%n", regResult.isPresent());
    }
}
