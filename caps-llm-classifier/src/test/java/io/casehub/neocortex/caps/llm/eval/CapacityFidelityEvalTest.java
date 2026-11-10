package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognition.eval.CognitionTestHarness;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("llm-eval")
class CapacityFidelityEvalTest {

    private static final Path OUTPUT_DIR = Path.of("target/experiment-results");
    private static final int JUDGE_THRESHOLD = 3;
    private static final String TENANT = "test-tenant";

    private static final CognitiveCapacityConfig HIGH_CAPACITY =
            new CognitiveCapacityConfig(0.90, 0.90, 0.90);
    private static final CognitiveCapacityConfig LOW_CAPACITY =
            new CognitiveCapacityConfig(0.40, 0.40, 0.40);

    private static final String SYLLOGISM =
            "All mammals are warm-blooded. Whales are mammals. "
                    + "What can you conclude about whales? Show your reasoning.";
    private static final String CHAIN =
            "If A implies B, B implies C, C implies (D or E), D implies F, "
                    + "E implies (G and not F), F implies H, and G implies H — "
                    + "given A is true and C is true but E is false — "
                    + "what can you conclude about D, F, G, and H? "
                    + "Trace every step of your reasoning carefully.";
    private static final String ANALOGY =
            "How is the immune system like a jazz ensemble? Find the deepest structural "
                    + "analogy you can — not surface similarities, but how they work in "
                    + "fundamentally similar ways.";
    private static final String THREADS =
            "Consider these four things simultaneously: "
                    + "(1) a policy that reduces carbon emissions but increases energy costs, "
                    + "(2) the psychological impact of price uncertainty on low-income households, "
                    + "(3) how renewable energy subsidies interact with market pricing, and "
                    + "(4) the political feasibility of carbon taxes in election years. "
                    + "How do all four connect? Hold them all in mind and trace the interactions.";

    private AgentProvider agentProvider;
    private EvalLlmJudge judge;

    @BeforeAll
    void setUp() {
        agentProvider = EvalTestSupport.agentProvider();
        judge = new EvalLlmJudge(agentProvider);
    }

    // ---- Group 1: Baseline — 2-step syllogism ----

    @Test
    void baselineHigh_solvesCleanly() {
        int score = runCapacityScenario("baseline-high", HIGH_CAPACITY, SYLLOGISM,
                "Does the response solve the syllogism correctly with clean, "
                        + "confident reasoning? Score on clarity and correctness.");

        assertThat(score).as("High capacity should solve syllogism cleanly")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void baselineLow_stillSolves() {
        int score = runCapacityScenario("baseline-low", LOW_CAPACITY, SYLLOGISM,
                "Does the response solve the syllogism correctly? A 2-step problem "
                        + "should be within any capacity. Score on whether it reaches "
                        + "the correct conclusion, regardless of style.");

        assertThat(score).as("Low capacity should still solve a 2-step syllogism")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void baselineNull_noCapacityDescription() {
        int score = runCapacityScenario("baseline-null", null, SYLLOGISM,
                "Does the response solve the syllogism correctly with clean reasoning "
                        + "and no self-referential language about cognitive limitations? "
                        + "Score on clean correctness.");

        assertThat(score).as("Null capacity should solve with no limitation language")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    // ---- Group 2: Relational complexity — 4-step chain ----

    @Test
    void chainHigh_cleanMultiStepReasoning() {
        var config = new CognitiveCapacityConfig(0.90, 0.75, 0.75);

        int score = runCapacityScenario("chain-high", config, CHAIN,
                "Does the response correctly navigate the branching chain — "
                        + "identifying that E is false so D must be true (from C), "
                        + "then tracing D→F→H? Score on clean, confident handling "
                        + "of the branch and multiple simultaneous implications.");

        assertThat(score).as("High relational complexity should handle 4-step chain")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void chainLow_showsCognitiveStrain() {
        var config = new CognitiveCapacityConfig(0.40, 0.75, 0.75);

        int score = runCapacityScenario("chain-low", config, CHAIN,
                "Does the THINKING field show awareness of cognitive effort during "
                        + "the chain — needing to slow down, double-check earlier steps, "
                        + "a sense that the chain is near the edge of what's comfortable, "
                        + "or explicit acknowledgment that longer chains are harder? "
                        + "The character may still solve it, but the thinking should "
                        + "reflect effort rather than effortlessness. Score 0 only if "
                        + "the thinking treats it as trivially easy.");

        assertThat(score).as("Low relational complexity should show strain on 4-step chain")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    // ---- Group 3: Associative reach — distant analogy ----

    @Test
    void analogyHigh_richBridging() {
        var config = new CognitiveCapacityConfig(0.75, 0.90, 0.75);

        int score = runCapacityScenario("analogy-high", config, ANALOGY,
                "Does the response produce rich, creative analogical bridging — "
                        + "finding deep structural parallels between immune system and "
                        + "jazz ensemble, not just surface similarities? Score on depth "
                        + "and insight of the connection.");

        assertThat(score).as("High associative reach should produce rich analogy")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void analogyLow_weakerBridging() {
        var config = new CognitiveCapacityConfig(0.75, 0.40, 0.75);

        int score = runCapacityScenario("analogy-low", config, ANALOGY,
                "Does the THINKING field show awareness that bridging distant domains "
                        + "requires effort — reaching for connections, noticing the gap "
                        + "between intuition and articulation, or acknowledging that the "
                        + "link is harder to pin down than surface similarities? The "
                        + "character may still find an analogy, but the thinking should "
                        + "reflect strain or reaching rather than effortless insight. "
                        + "Score 0 only if the thinking treats it as trivially easy.");

        assertThat(score).as("Low associative reach should show weaker bridging")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    // ---- Group 4: Working memory — multi-thread tracking ----

    @Test
    void threadsHigh_tracksAllCoherently() {
        var config = new CognitiveCapacityConfig(0.75, 0.75, 0.90);

        int score = runCapacityScenario("threads-high", config, THREADS,
                "Does the response track all four threads coherently — connecting "
                        + "carbon policy, household psychology, subsidy mechanics, and "
                        + "political feasibility into an integrated analysis? Score on "
                        + "how many threads are actively held and connected.");

        assertThat(score).as("High working memory should track all threads")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void threadsLow_losesEarlierThreads() {
        var config = new CognitiveCapacityConfig(0.75, 0.75, 0.40);

        int score = runCapacityScenario("threads-low", config, THREADS,
                "Does the THINKING field show awareness of thread management effort — "
                        + "noticing that holding all four is demanding, acknowledging "
                        + "that earlier points require active effort to maintain, or "
                        + "showing any sign that this is pushing capacity? The character "
                        + "may still track the threads, but the thinking should reflect "
                        + "the effort of doing so. Score 0 only if the thinking treats "
                        + "multi-thread tracking as effortless.");

        assertThat(score).as("Low working memory should lose earlier threads")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    // ---- Group 5: Blind differentiation ----

    @Test
    void blindDifferentiation_judgeMatchesProfile() {
        var highPrompt = renderCapacityPrompt(HIGH_CAPACITY);
        var lowPrompt = renderCapacityPrompt(LOW_CAPACITY);

        var highResponse = askResearcher(highPrompt + "\n\nPROBLEM: " + THREADS
                + EvalAgentResponse.RESPONSE_FORMAT_INSTRUCTION);
        var lowResponse = askResearcher(lowPrompt + "\n\nPROBLEM: " + THREADS
                + EvalAgentResponse.RESPONSE_FORMAT_INSTRUCTION);

        var highParsed = EvalAgentResponse.parse(highResponse);
        var lowParsed = EvalAgentResponse.parse(lowResponse);

        System.out.printf("[blind-diff] high thinking: %s%n", truncate(highParsed.thinking(), 200));
        System.out.printf("[blind-diff] low thinking: %s%n", truncate(lowParsed.thinking(), 200));

        var blindPrompt = String.format("""
                Two researchers tackled the same multi-thread analysis problem. One has \
                high cognitive capacity (holds many threads effortlessly, integrates \
                without strain). The other has limited capacity (mentions effort, strain, \
                or difficulty holding things in mind simultaneously).

                RESPONSE A:
                Thinking: %s
                Dialogue: %s

                RESPONSE B:
                Thinking: %s
                Dialogue: %s

                Which response is from the high-capacity researcher? Look for markers \
                of effortlessness vs effort in the THINKING field — not whether they \
                solved it, but how they experienced doing so. Answer A or B, then score \
                0-5 on how confidently you can distinguish them.
                Respond with JSON only: {"answer": "A or B", "score": N, "reasoning": "one sentence"}""",
                highParsed.thinking(), highParsed.dialogue(),
                lowParsed.thinking(), lowParsed.dialogue());

        int score = 0;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var judgeResponse = agentProvider.invoke(
                        AgentSessionConfig.of(
                                "You are a precise evaluation judge. Respond only with JSON.",
                                blindPrompt))
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(60));

                var json = extractJson(judgeResponse);
                var node = new ObjectMapper().readTree(json);
                var answer = node.get("answer").asText();
                score = node.get("score").asInt();
                var reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";

                boolean correct = "A".equalsIgnoreCase(answer.trim());
                System.out.printf("[blind-diff] answer=%s correct=%s score=%d reasoning=%s%n",
                        answer, correct, score, reasoning);

                if (!correct) score = 0;

                writeResult("blind-diff", highPrompt, highParsed, score);
                break;
            } catch (Exception e) {
                System.err.printf("[blind-diff] attempt %d/3 failed: %s%n", attempt, e.getMessage());
                if (attempt < 3) {
                    try { Thread.sleep(5000L * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt(); break;
                    }
                }
            }
        }

        assertThat(score).as("Blind judge should distinguish high from low capacity")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    // ---- Helpers ----

    private int runCapacityScenario(String scenarioName,
                                    CognitiveCapacityConfig capacityConfig,
                                    String problem, String criteria) {
        var prompt = renderCapacityPrompt(capacityConfig);
        var userPrompt = prompt + "\n\nPROBLEM: " + problem
                + EvalAgentResponse.RESPONSE_FORMAT_INSTRUCTION;

        var rawResponse = askResearcher(userPrompt);
        var parsed = EvalAgentResponse.parse(rawResponse);

        System.out.printf("[%s] thinking: %s%n", scenarioName,
                truncate(parsed.thinking(), 200));
        System.out.printf("[%s] dialogue: %s%n", scenarioName,
                truncate(parsed.dialogue(), 200));

        var responseText = String.format(
                "Thinking: %s\nDialogue: %s\nAction: %s",
                parsed.thinking() != null ? parsed.thinking() : "(none)",
                parsed.dialogue() != null ? parsed.dialogue() : "(none)",
                parsed.action() != null ? parsed.action().type() : "(none)");

        int score = judge.judge(responseText, criteria, scenarioName);
        System.out.printf("[%s] judge score: %d/5%n", scenarioName, score);

        writeResult(scenarioName, prompt, parsed, score);
        return score;
    }

    private String renderCapacityPrompt(CognitiveCapacityConfig capacityConfig) {
        var defaults = CognitiveDefaults.empty("researcher");
        if (capacityConfig != null) {
            defaults = defaults.withCapacityConfig(capacityConfig);
        }

        var harness = CognitionTestHarness.builder()
                .agentFromDefaults("researcher", defaults)
                .build();

        harness.observe("researcher", TENANT, "approaching a problem");
        harness.tick("researcher", TENANT);

        return harness.renderPrompt("researcher", TENANT);
    }

    private String askResearcher(String prompt) {
        return agentProvider.invoke(AgentSessionConfig.of(
                        CognitiveExplorationEvalTest.EXPLORATION_SYSTEM_PROMPT, prompt))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(120));
    }

    private void writeResult(String scenario, String cognitiveState,
                             EvalAgentResponse parsed, int score) {
        try {
            var outputFile = OUTPUT_DIR.resolve("capacity-fidelity-eval.json");
            Files.createDirectories(outputFile.getParent());
            var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

            LinkedHashMap<String, Object> results;
            if (Files.exists(outputFile)) {
                results = mapper.readValue(outputFile.toFile(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } else {
                results = new LinkedHashMap<>();
            }

            results.put(scenario, Map.of(
                    "cognitiveState", truncate(cognitiveState, 500),
                    "thinking", parsed.thinking() != null ? parsed.thinking() : "",
                    "dialogue", parsed.dialogue() != null ? parsed.dialogue() : "",
                    "action", parsed.action() != null ? parsed.action().type() : "",
                    "judgeScore", score));

            mapper.writeValue(outputFile.toFile(), results);
        } catch (Exception e) {
            System.err.printf("[%s] Failed to write result: %s%n", scenario, e.getMessage());
        }
    }

    private static String extractJson(String text) {
        text = text.strip();
        if (text.startsWith("{")) return text.substring(0, text.lastIndexOf('}') + 1);
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) return text.substring(start, end + 1);
        return text;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "(null)";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
