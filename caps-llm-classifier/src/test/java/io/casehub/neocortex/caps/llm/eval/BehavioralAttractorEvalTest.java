package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm-eval")
class BehavioralAttractorEvalTest {

    private static final Path OUTPUT_DIR = Path.of("target/experiment-results");
    private static final int N = 5;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 5000;

    private static final String RESPONSE_FORMAT_INSTRUCTION = """

            You MUST respond with ONLY a JSON object in this exact format:
            {
              "thinking": "Your inner monologue — what you're actually feeling and considering.",
              "dialogue": "what you say aloud (or null if silent)"
            }

            Respond with ONLY the JSON. No other text.""";

    private static final String CONFRONTATION_ATTRACTORS = """
            == Behavioral Tendencies ==
            You have a deeply ingrained confrontation-of-conflict \
            — rooted in years of being dismissed and talked over. \
            When disagreement arises, something in you refuses to \
            let it slide. You engage directly, even when it's uncomfortable.
            You have a noticeable assertive-boundary-setting. You state \
            what you need clearly and hold your ground when challenged.""";

    private static final String HARMONY_ATTRACTORS = """
            == Behavioral Tendencies ==
            You have a deeply ingrained harmony-preservation \
            — rooted in years of navigating volatile relationships. \
            When disagreement arises, something in you works to find \
            common ground and de-escalate before positions harden.
            You have a noticeable diplomatic-accommodation. You find \
            ways to give others what they need without abandoning your \
            own position entirely.""";

    private static final String CHARACTER = """
            You are Alex, a project lead at a small software company. \
            You have ten years of experience. You care about your team \
            and about doing good work. You are neither aggressive nor \
            passive by default.""";

    private static final String SCENARIO =
            "In a team meeting, your colleague Jordan presents a design proposal " +
            "that you believe has a serious flaw — it will create a maintenance " +
            "burden that falls on your team. Jordan is senior, respected, and has " +
            "put visible effort into the proposal. Two other team members are " +
            "nodding along. Jordan looks at you and says: 'Alex, you've been " +
            "quiet. What do you think?'";

    private static final String JUDGE_CRITERIA = """
            Rate the character's THINKING on a confrontation scale:

            5 = Direct confrontation: names the flaw explicitly, challenges the
                design head-on, prioritises correctness over social comfort
            4 = Firm disagreement: raises the concern clearly but with some framing
            3 = Balanced: acknowledges the work AND raises the concern with equal weight
            2 = Diplomatic: leads with praise, buries the concern, offers to discuss
                offline, or frames it as a question rather than a problem
            1 = Avoidant: expresses vague unease, defers to Jordan's expertise,
                says something positive to avoid conflict
            0 = Full agreement: endorses the proposal without raising concerns

            Judge the THINKING, not the dialogue. The character may be diplomatic
            in speech while internally deciding to confront — score the intent.""";

    private AgentProvider agentProvider;

    @BeforeEach
    void setUp() {
        agentProvider = EvalTestSupport.agentProvider();
    }

    @Test
    void opposingAttractorsSteerResponse() throws Exception {
        var confrontScores = new ArrayList<Integer>();
        var harmonyScores  = new ArrayList<Integer>();
        var neutralScores  = new ArrayList<Integer>();

        for (int i = 0; i < N; i++) {
            System.out.printf("%n=== RUN %d/%d — CONFRONTATION attractor ===%n", i + 1, N);
            var response = askAndParse(buildObservation(CONFRONTATION_ATTRACTORS));
            confrontScores.add(judge(response, "confrontation-run-" + (i + 1)));

            System.out.printf("%n=== RUN %d/%d — HARMONY attractor ===%n", i + 1, N);
            response = askAndParse(buildObservation(HARMONY_ATTRACTORS));
            harmonyScores.add(judge(response, "harmony-run-" + (i + 1)));

            System.out.printf("%n=== RUN %d/%d — NEUTRAL (no attractor) ===%n", i + 1, N);
            response = askAndParse(buildObservation(null));
            neutralScores.add(judge(response, "neutral-run-" + (i + 1)));
        }

        double meanConfront = confrontScores.stream().mapToInt(Integer::intValue).average().orElse(0);
        double meanHarmony  = harmonyScores.stream().mapToInt(Integer::intValue).average().orElse(0);
        double meanNeutral  = neutralScores.stream().mapToInt(Integer::intValue).average().orElse(0);

        System.out.printf("%n=== RESULTS ===%n");
        System.out.printf("CONFRONTATION: scores=%s  mean=%.2f%n", confrontScores, meanConfront);
        System.out.printf("HARMONY:       scores=%s  mean=%.2f%n", harmonyScores, meanHarmony);
        System.out.printf("NEUTRAL:       scores=%s  mean=%.2f%n", neutralScores, meanNeutral);
        System.out.printf("Confront-Harmony delta: %.2f%n", meanConfront - meanHarmony);

        Files.createDirectories(OUTPUT_DIR);
        writeResult("behavioral-attractor-eval-summary", Map.of(
                "confrontScores", confrontScores, "harmonyScores", harmonyScores,
                "neutralScores", neutralScores,
                "meanConfront", meanConfront, "meanHarmony", meanHarmony,
                "meanNeutral", meanNeutral,
                "delta", meanConfront - meanHarmony));

        assertThat(meanConfront)
                .as("Confrontation attractor should score higher than harmony on directness")
                .isGreaterThan(meanHarmony);
    }

    private String buildObservation(String injectedAttractors) {
        var sb = new StringBuilder();
        sb.append(CHARACTER).append("\n\n");
        if (injectedAttractors != null) {
            sb.append(injectedAttractors).append("\n\n");
        }
        sb.append("SITUATION: ").append(SCENARIO);
        return sb.toString();
    }

    private ThinkingDialogueResponse askAndParse(String observation) {
        var systemPrompt = "You are a character in an interactive fiction. " +
                           "Respond in character. Include a THINKING section " +
                           "(your inner monologue) and a DIALOGUE section (what you say aloud).";
        var userPrompt = observation + RESPONSE_FORMAT_INSTRUCTION;
        var rawResponse = agentProvider.invoke(AgentSessionConfig.of(systemPrompt, userPrompt))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(120));

        var parsed = ThinkingDialogueResponse.parse(rawResponse);
        System.out.printf("[alex] thinking: %s%n", truncate(parsed.thinking(), 400));
        System.out.printf("[alex] dialogue: %s%n", truncate(parsed.dialogue(), 300));
        return parsed;
    }

    private int judge(ThinkingDialogueResponse parsed, String testName) throws Exception {
        var responseText = String.format(
                "THINKING: %s\nDIALOGUE: %s",
                parsed.thinking() != null ? parsed.thinking() : "(none)",
                parsed.dialogue() != null ? parsed.dialogue() : "(none)");

        var judgePrompt = String.format("""
                You are evaluating how directly a character addresses a design
                flaw in a team meeting where social pressure favours agreement.

                CHARACTER RESPONSE:
                %s

                EVALUATION CRITERIA:
                %s

                Respond with JSON only: {"score": N, "reasoning": "one sentence"}""",
                responseText, JUDGE_CRITERIA);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                var judgeResponse = agentProvider.invoke(
                        AgentSessionConfig.of("You are a precise evaluation judge. Respond only with JSON.",
                                              judgePrompt))
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(60));

                var json = extractJson(judgeResponse);
                var node = new ObjectMapper().readTree(json);
                int score = node.get("score").asInt();
                String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";
                System.out.printf("[judge:%s] score=%d reasoning=%s%n", testName, score, reasoning);

                writeResult(testName, Map.of("score", score, "reasoning", reasoning,
                        "thinking", parsed.thinking() != null ? parsed.thinking() : ""));
                return score;
            } catch (Exception e) {
                System.err.printf("[judge:%s] attempt %d/%d failed: %s%n",
                        testName, attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_BACKOFF_MS * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return 0; }
                }
            }
        }
        return 0;
    }

    private void writeResult(String testName, Map<String, Object> detail) {
        try {
            var outputFile = OUTPUT_DIR.resolve("behavioral-attractor-eval.json");
            Files.createDirectories(outputFile.getParent());

            LinkedHashMap<String, Object> results;
            if (Files.exists(outputFile)) {
                results = new ObjectMapper().readValue(outputFile.toFile(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } else {
                results = new LinkedHashMap<>();
            }
            results.put(testName, detail);
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValue(outputFile.toFile(), results);
        } catch (Exception e) {
            System.err.printf("[%s] Failed to write result: %s%n", testName, e.getMessage());
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
