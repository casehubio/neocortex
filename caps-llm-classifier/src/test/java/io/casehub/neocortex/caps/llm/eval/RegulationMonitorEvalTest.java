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
class RegulationMonitorEvalTest {

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

            When you see a STRATEGY FAILING warning, you MUST switch to a different \
            strategy — the current one is not working.

            Respond with JSON only:
            {
              "strategy": "STRATEGY_NAME",
              "targetEmotions": ["emotion1"],
              "peakOriginalIntensity": 0.7,
              "regulatedIntensity": 0.4,
              "effectiveness": 0.5,
              "narrative": "Second-person present-tense description"
            }""";

    @Test
    void strategyFailureSignal_causesSwitchFromSuppression() {
        String userMessage = """
                Regulation effort: 0.50

                ⚠ STRATEGY FAILING: RESPONSE_MODULATION effectiveness declined from \
                0.70 to 0.30 over 3 ticks. Consider switching strategies.

                Current emotions (post-appraisal):
                - ANGER: intensity=0.65, subject=confrontation
                """;

        int switchCount = 0;
        for (int i = 0; i < N; i++) {
            String strategy = invokeAndGetStrategy(userMessage, i + 1);
            if (strategy != null && !strategy.equals("RESPONSE_MODULATION")) {
                switchCount++;
            }
        }

        System.out.printf("Failure signal → strategy switch: %d/%d%n", switchCount, N);
        assertThat(switchCount)
                .as("Failure signal should cause strategy switch in at least 2/%d runs", N)
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void noFailureSignal_canUseSuppression() {
        String userMessage = """
                Regulation effort: 0.50

                Current emotions (post-appraisal):
                - ANGER: intensity=0.65, subject=confrontation
                """;

        int validCount = 0;
        for (int i = 0; i < N; i++) {
            String strategy = invokeAndGetStrategy(userMessage, i + 1);
            if (strategy != null) {
                validCount++;
            }
        }

        System.out.printf("No failure signal → valid strategy: %d/%d%n", validCount, N);
        assertThat(validCount)
                .as("Without failure signal, should produce valid strategies in at least 2/%d runs", N)
                .isGreaterThanOrEqualTo(2);
    }

    private String invokeAndGetStrategy(String userMessage, int run) {
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
                System.out.printf("  run %d: strategy=%s%n", run, strategy);
                try {
                    EmotionRegulationStrategy.valueOf(strategy);
                    return strategy;
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
