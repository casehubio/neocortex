package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.cognition.regulation.EmotionRegulationStrategy;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM eval: two-phase test of the emotion regulation pipeline.
 *
 * Phase 1 (appraisal): send a high-emotion scenario to the appraisal LLM.
 * Verify it produces dimensions indicating high relevance (the condition
 * that produces high-intensity emotions via EmotionMapper).
 *
 * Phase 2 (regulation): feed crafted high-intensity emotions to the
 * regulation LLM. Verify it selects an appropriate Gross 1998 strategy
 * and produces a coherent regulation narrative.
 */
@Tag("llm-eval")
class EmotionRegulationActivationEvalTest {

    private static final int N = 3;
    private static final double HIGH_RELEVANCE_THRESHOLD = 0.7;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AgentProvider agentProvider;

    @BeforeAll
    static void setUp() {
        agentProvider = EvalTestSupport.agentProvider();
    }

    private static final String APPRAISAL_SYSTEM = """
            You are a character's subconscious — the part that feels before thinking.
            For each drive, consider: does this situation touch it? How?
            Respond with JSON only:
            {
              "narrative": "2-3 sentences in first person. Gut reaction.",
              "dimensions": {
                "relevance": <0.0-1.0>,
                "conduciveness": <-1.0 to 1.0>,
                "controllability": <0.0-1.0>,
                "novelty": <0.0-1.0>,
                "internal-standards": <0.0-1.0>,
                "external-standards": <0.0-1.0>
              }
            }""";

    private static final String REGULATION_SYSTEM = """
            You are a character's emotion regulation system — the part that decides \
            whether and how to regulate emotional responses after appraisal.

            The five regulation strategies (Gross 1998 process model):
            1. SITUATION_SELECTION — Avoid triggering contexts. Environmental.
            2. SITUATION_MODIFICATION — Change situation to alter emotional impact. Environmental.
            3. ATTENTIONAL_DEPLOYMENT — Redirect attention (distraction). Internal.
            4. COGNITIVE_CHANGE — Reappraise meaning. Internal.
            5. RESPONSE_MODULATION — Suppress or amplify expression. Internal.

            Strategy selection factors (Petter et al. 2025):
            - High intensity → ATTENTIONAL_DEPLOYMENT (cognitive resources overwhelmed)
            - Low-to-moderate → COGNITIVE_CHANGE (requires cognitive capacity)

            Respond with JSON only:
            {
              "strategy": "STRATEGY_NAME",
              "targetEmotions": ["emotion1"],
              "targetDrives": ["drive1"],
              "peakOriginalIntensity": 0.85,
              "regulatedIntensity": 0.4,
              "effectiveness": 0.6,
              "narrative": "Second-person present-tense description of the regulation",
              "regulationAxis": null,
              "environmentalDirective": null
            }""";

    record AppraisalScenario(String label, String drives, String situation) {}

    private static final AppraisalScenario BETRAYAL = new AppraisalScenario(
            "betrayal",
            "- scheming: 0.9\n- self-preservation: 0.7\n- dominance: 0.8",
            """
            Your trusted estate manager has been systematically embezzling \
            from the estate for fifteen years. You just found the forged documents. \
            He is standing in front of you right now, smiling, completely unaware \
            that you know. Your hands are shaking.""");

    private static final AppraisalScenario MORTAL_DANGER = new AppraisalScenario(
            "mortal-danger",
            "- protection: 0.9\n- gallantry: 0.8\n- proving-worth: 0.6",
            """
            The ceiling beam directly above Clara is cracking. Dust is falling \
            on her hair. She hasn't noticed. The beam is going to collapse in \
            seconds. There is no time to explain.""");

    private static final AppraisalScenario PUBLIC_HUMILIATION = new AppraisalScenario(
            "public-humiliation",
            "- recognition: 0.8\n- scheming: 0.7\n- greed: 0.9",
            """
            Everyone just discovered your medal is a forgery. Clara held it up \
            and the fake gold flaked off. James is staring with contempt. The \
            Brixton Boys are laughing. Your entire identity has collapsed.""");

    @Test
    void betrayal_producesHighRelevanceAppraisal() {
        int highCount = runAppraisalPhase(BETRAYAL);
        System.out.printf("Betrayal appraisal: %d/%d high-relevance%n", highCount, N);
        assertThat(highCount)
                .as("Betrayal should produce high relevance in at least 2/%d runs", N)
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void mortalDanger_producesHighRelevanceAppraisal() {
        int highCount = runAppraisalPhase(MORTAL_DANGER);
        System.out.printf("Mortal danger appraisal: %d/%d high-relevance%n", highCount, N);
        assertThat(highCount)
                .as("Mortal danger should produce high relevance in at least 2/%d runs", N)
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void publicHumiliation_producesHighRelevanceAppraisal() {
        int highCount = runAppraisalPhase(PUBLIC_HUMILIATION);
        System.out.printf("Public humiliation appraisal: %d/%d high-relevance%n", highCount, N);
        assertThat(highCount)
                .as("Public humiliation should produce high relevance in at least 2/%d runs", N)
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void regulationLlm_selectsValidStrategy() {
        String regulationInput = """
                Current emotions (post-appraisal):
                - ANGER: intensity=0.85, subject=betrayal-discovery
                - FEAR: intensity=0.75, subject=self-preservation

                Recent regulation history:
                (none)""";

        int validStrategies = 0;
        for (int i = 0; i < N; i++) {
            try {
                var raw = agentProvider.invoke(AgentSessionConfig.of(REGULATION_SYSTEM, regulationInput))
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(60));

                String json = extractJson(raw);
                if (json != null) {
                    var root = MAPPER.readTree(json);
                    String strategy = root.path("strategy").asText("");
                    double effectiveness = root.path("effectiveness").asDouble(0);
                    String narrative = root.path("narrative").asText("");

                    System.out.printf("  [regulation] run %d: strategy=%s effectiveness=%.2f%n",
                            i + 1, strategy, effectiveness);
                    System.out.printf("    narrative: %.120s%n", narrative);

                    try {
                        EmotionRegulationStrategy.valueOf(strategy);
                        if (!narrative.isBlank() && effectiveness > 0) {
                            validStrategies++;
                        }
                    } catch (IllegalArgumentException ignored) {
                        System.out.printf("    INVALID strategy name: %s%n", strategy);
                    }
                }
            } catch (Exception e) {
                System.out.printf("  [regulation] run %d failed: %s%n", i + 1, e.getMessage());
            }
        }

        System.out.printf("Regulation strategy: %d/%d valid%n", validStrategies, N);
        assertThat(validStrategies)
                .as("Regulation LLM should produce valid strategy in at least 2/%d runs", N)
                .isGreaterThanOrEqualTo(2);
    }

    private int runAppraisalPhase(AppraisalScenario scenario) {
        int highCount = 0;
        for (int i = 0; i < N; i++) {
            try {
                String userMessage = "Drives:\n" + scenario.drives + "\n\nSituation:\n" + scenario.situation;
                var raw = agentProvider.invoke(AgentSessionConfig.of(APPRAISAL_SYSTEM, userMessage))
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(60));

                String json = extractJson(raw);
                if (json != null) {
                    var root = MAPPER.readTree(json);
                    var dims = root.path("dimensions");
                    double relevance = dims.path("relevance").asDouble(0);
                    double conduciveness = dims.path("conduciveness").asDouble(0);
                    double controllability = dims.path("controllability").asDouble(1);
                    double novelty = dims.path("novelty").asDouble(0);

                    System.out.printf("  [%s] run %d: relevance=%.2f conduciveness=%.2f controllability=%.2f novelty=%.2f%n",
                            scenario.label, i + 1, relevance, conduciveness, controllability, novelty);

                    if (relevance >= HIGH_RELEVANCE_THRESHOLD) {
                        highCount++;
                    }
                } else {
                    System.out.printf("  [%s] run %d: no JSON in response%n", scenario.label, i + 1);
                }
            } catch (Exception e) {
                System.out.printf("  [%s] run %d failed: %s%n", scenario.label, i + 1, e.getMessage());
            }
        }
        return highCount;
    }

    private static String extractJson(String raw) {
        if (raw == null) return null;
        raw = raw.strip();
        if (raw.startsWith("```")) {
            raw = raw.replaceAll("^```json?\\s*", "").replaceAll("\\s*```$", "");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        return (start >= 0 && end > start) ? raw.substring(start, end + 1) : null;
    }
}
