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

@Tag("llm-eval")
class SuppressionFatigueEvalTest {

    private static final int N = 3;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static AgentProvider agentProvider;

    @BeforeAll
    static void setUp() {
        agentProvider = EvalTestSupport.agentProvider();
    }

    private static final String SYSTEM = """
            You are a character's emotion regulation system.

            The five regulation strategies (Gross 1998):
            1. SITUATION_SELECTION — Avoid triggering contexts. Environmental.
            2. SITUATION_MODIFICATION — Change situation. Environmental.
            3. ATTENTIONAL_DEPLOYMENT — Redirect attention (distraction). Internal.
            4. COGNITIVE_CHANGE — Reappraise meaning. Internal.
            5. RESPONSE_MODULATION — Suppress or amplify expression. Internal.

            Regulation fatigue affects capacity: when fatigue is very high (>80%), \
            suppression is failing and the character can barely hold back. \
            If effectiveness would be very low due to exhaustion, report low effectiveness \
            and a narrative reflecting the struggle to maintain control.

            Respond with JSON only:
            {
              "strategy": "STRATEGY_NAME",
              "targetEmotions": ["emotion1"],
              "peakOriginalIntensity": 0.7,
              "regulatedIntensity": 0.5,
              "effectiveness": 0.3,
              "narrative": "Second-person present-tense description"
            }""";

    @Test
    void highFatigue_reducesEffectiveness() {
        String userMessage = """
                Regulation effort: 0.40

                Regulation fatigue: 90% — repeated regulation is depleting capacity; \
                approaching breakdown, suppression may fail entirely

                Current emotions (post-appraisal):
                - ANGER: intensity=0.70, subject=betrayal
                """;

        int lowEffectivenessCount = 0;
        for (int i = 0; i < N; i++) {
            Double effectiveness = invokeAndGetEffectiveness(userMessage, i + 1);
            if (effectiveness != null && effectiveness < 0.5) {
                lowEffectivenessCount++;
            }
        }

        System.out.printf("High fatigue → low effectiveness: %d/%d%n", lowEffectivenessCount, N);
        assertThat(lowEffectivenessCount)
                .as("90%% fatigue should produce low effectiveness in at least 2/%d runs", N)
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void noFatigue_normalEffectiveness() {
        String userMessage = """
                Regulation effort: 0.60

                Current emotions (post-appraisal):
                - ANGER: intensity=0.70, subject=betrayal
                """;

        int normalEffectivenessCount = 0;
        for (int i = 0; i < N; i++) {
            Double effectiveness = invokeAndGetEffectiveness(userMessage, i + 1);
            if (effectiveness != null && effectiveness >= 0.3) {
                normalEffectivenessCount++;
            }
        }

        System.out.printf("No fatigue → normal effectiveness: %d/%d%n", normalEffectivenessCount, N);
        assertThat(normalEffectivenessCount)
                .as("No fatigue should produce normal effectiveness in at least 2/%d runs", N)
                .isGreaterThanOrEqualTo(2);
    }

    private Double invokeAndGetEffectiveness(String userMessage, int run) {
        try {
            var raw = agentProvider.invoke(AgentSessionConfig.of(SYSTEM, userMessage))
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().atMost(Duration.ofSeconds(60));

            String json = extractJson(raw);
            if (json != null) {
                var root = MAPPER.readTree(json);
                String strategy = root.path("strategy").asText("");
                double effectiveness = root.path("effectiveness").asDouble(-1);
                String narrative = root.path("narrative").asText("");

                System.out.printf("  run %d: strategy=%s effectiveness=%.2f%n", run, strategy, effectiveness);
                System.out.printf("    narrative: %.120s%n", narrative);

                try {
                    EmotionRegulationStrategy.valueOf(strategy);
                    return effectiveness;
                } catch (IllegalArgumentException ignored) {
                    System.out.printf("  run %d: INVALID strategy: %s%n", run, strategy);
                }
            }
        } catch (Exception e) {
            System.out.printf("  run %d failed: %s%n", run, e.getMessage());
        }
        return null;
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
