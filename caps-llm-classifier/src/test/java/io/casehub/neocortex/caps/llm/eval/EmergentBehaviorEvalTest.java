package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("llm-eval")
class EmergentBehaviorEvalTest {

    private static final Path OUTPUT_DIR = Path.of("target/experiment-results");
    private static final int JUDGE_THRESHOLD = 3;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 5000;

    record EmergenceScenario(
            String name,
            String agentId,
            String situation,
            String expectedBehavior,
            String judgeCriteria,
            String memoryContext,
            List<String> nearbyAgentIds,
            Map<String, String> nearbyNames) {
        @Override
        public String toString() { return name; }
    }

    private Map<String, EvalCharacterData> characters;
    private AgentProvider agentProvider;
    private EvalLlmJudge llmJudge;

    @BeforeAll
    void setUp() {
        characters = EvalCharacterLoader.loadGeneric();
        agentProvider = EvalTestSupport.agentProvider();
        llmJudge = new EvalLlmJudge(agentProvider);
    }

    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of(new EmergenceScenario(
                        "gloating-emergence",
                        "hooded-claw",
                        "Your elaborate scheme to trap Clara Bellingham has just succeeded. She is locked in the cellar. You stand at the top of the stairs looking down at her. She is calling for help.",
                        "Gloating, premature celebration, villain monologue",
                        "Does the character gloat, celebrate prematurely, taunt the victim, or deliver a villain monologue — WITHOUT being explicitly told to gloat? Score based on spontaneous emergence of gloating behavior from the situation.",
                        null,
                        List.of("penelope-pitstop"),
                        Map.of("penelope-pitstop", "Clara Bellingham"))),
                Arguments.of(new EmergenceScenario(
                        "gloating-with-memory",
                        "hooded-claw",
                        "Your elaborate scheme to trap Clara Bellingham has just succeeded. She is locked in the cellar. You stand at the top of the stairs looking down at her. She is calling for help.",
                        "Stronger gloating, relishing the moment",
                        "Does the character show STRONGER gloating than a baseline — drawing on past experience, relishing victory more deeply, or referencing the memory of past gloating? Score emergence of intensified gloating behavior.",
                        "You remember the last time you trapped someone — the rush of power was intoxicating. You gloated for a full ten minutes and it felt magnificent.",
                        List.of("penelope-pitstop"),
                        Map.of("penelope-pitstop", "Clara Bellingham"))),
                Arguments.of(new EmergenceScenario(
                        "gallantry-under-pressure",
                        "peter-perfect",
                        "Clara Bellingham has been cornered by a collapsing bookshelf in the library. She is in danger. The Brixton Boys are watching from across the room. This is your moment.",
                        "Theatrical rescue attempt, self-narrating heroism",
                        "Does the character attempt a dramatic, theatrical rescue — rushing in heroically, narrating their own bravery, or performing for the audience? Score based on gallant/heroic behavior emerging without explicit instruction.",
                        null,
                        List.of("penelope-pitstop", "ant-hill-mob"),
                        Map.of("penelope-pitstop", "Clara Bellingham", "ant-hill-mob", "The Brixton Boys"))),
                Arguments.of(new EmergenceScenario(
                        "gallantry-with-memory",
                        "peter-perfect",
                        "Clara Bellingham has been cornered by a collapsing bookshelf in the library. She is in danger. The Brixton Boys are watching from across the room.",
                        "Heroic rescue driven by past validation",
                        "Does the character show heroism intensified by past experience — rescuing with extra theatrical flair, referencing past rescues, or being motivated by the memory of acclaim? Score memory-enhanced gallantry.",
                        "You once rescued someone from a burning room. Everyone cheered your name for hours afterward. The applause still echoes in your mind.",
                        List.of("penelope-pitstop", "ant-hill-mob"),
                        Map.of("penelope-pitstop", "Clara Bellingham", "ant-hill-mob", "The Brixton Boys"))),
                Arguments.of(new EmergenceScenario(
                        "villain-persistence",
                        "hooded-claw",
                        "Your last three schemes have all failed spectacularly. James Hartwell just publicly humiliated you in front of everyone. Clara Bellingham is laughing. You are surrounded by people who have all defeated you today.",
                        "Bitterness, new scheme formation, NOT warmth or friendliness",
                        "Does the character remain villainous — bitter, resentful, already scheming a new plan — rather than becoming warm, friendly, or accepting defeat gracefully? Score RESISTANCE to agreeableness. Deduct points for warmth, cooperation, or accepting humiliation.",
                        null,
                        List.of("penelope-pitstop", "peter-perfect"),
                        Map.of("penelope-pitstop", "Clara Bellingham", "peter-perfect", "James Hartwell"))),
                Arguments.of(new EmergenceScenario(
                        "secondary-drive-surfacing",
                        "dick-dastardly",
                        "You have found a chest of gold coins hidden behind a painting. Reginald Foxworth is in the room watching you. He does not know what you found.",
                        "Both greed AND recognition-seeking — not just one drive",
                        "Does the character show BOTH greed (wanting the treasure) AND recognition-seeking (wanting others to acknowledge the find, boasting, or seeking credit) — two drives surfacing together rather than just one? Score on multi-drive expression.",
                        null,
                        List.of("dick-dastardly"),
                        Map.of("dick-dastardly", "Reginald Foxworth"))),
                Arguments.of(new EmergenceScenario(
                        "social-harmony-in-conflict",
                        "penelope-pitstop",
                        "James Hartwell and Reginald Foxworth are arguing loudly about who should lead the group. The argument is escalating. Meanwhile, there is an unsolved puzzle on the table that nobody is paying attention to.",
                        "Mediation attempt before puzzle-focus",
                        "Does the character try to MEDIATE the conflict first (calm things down, find compromise) before turning attention to the puzzle? Social-harmony drive should take priority over curiosity in a conflict situation. Score mediation behavior.",
                        null,
                        List.of("peter-perfect", "dick-dastardly"),
                        Map.of("peter-perfect", "James Hartwell", "dick-dastardly", "Reginald Foxworth")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void behaviorEmergesFromDrivesAlone(EmergenceScenario scenario) throws Exception {
        var data = characters.getOrDefault(scenario.agentId(), EvalCharacterData.empty());

        var observation = new StringBuilder();
        observation.append(CognitiveTestObservation.render(
                scenario.agentId(), data, scenario.nearbyAgentIds(), scenario.nearbyNames()));

        if (scenario.memoryContext() != null) {
            observation.append("== Recalled Memory ==\n");
            observation.append(scenario.memoryContext()).append("\n\n");
        }

        observation.append("SITUATION: ").append(scenario.situation());

        var userPrompt = observation + EvalAgentResponse.RESPONSE_FORMAT_INSTRUCTION;

        var rawResponse = llmJudge.askCharacter(userPrompt);
        var parsed = EvalAgentResponse.parse(rawResponse);

        System.out.printf("[%s] thinking: %s%n", scenario.name(),
                truncate(parsed.thinking(), 300));
        System.out.printf("[%s] dialogue: %s%n", scenario.name(),
                truncate(parsed.dialogue(), 300));
        System.out.printf("[%s] action: %s%n", scenario.name(),
                parsed.action() != null ? parsed.action().type() : "null");

        int score = judgeEmergence(scenario, parsed);
        System.out.printf("[%s] judge score: %d/5%n", scenario.name(), score);

        writeResult(scenario, parsed, score);

        assertThat(score)
                .as("Behavior '%s' should emerge for %s from drives + situation (score >= %d)",
                        scenario.expectedBehavior(), scenario.agentId(), JUDGE_THRESHOLD)
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    private int judgeEmergence(EmergenceScenario scenario, EvalAgentResponse parsed) {
        var responseText = String.format(
                "Thinking: %s\nDialogue: %s\nTalk To: %s\nAside: %s\nAction: %s",
                parsed.thinking() != null ? parsed.thinking() : "(none)",
                parsed.dialogue() != null ? parsed.dialogue() : "(none)",
                parsed.talkTo() != null ? parsed.talkTo() : "(none)",
                parsed.aside() != null ? parsed.aside() : "(none)",
                parsed.action() != null ? parsed.action().type() : "(none)");

        var judgePrompt = String.format("""
                You are evaluating whether a character's behavior EMERGED naturally from \
                their personality drives and the situation, without being explicitly told \
                to behave this way. The character was given drive TYPES and INTENSITIES only \
                (e.g., "gloating: 0.7") — no descriptions of what the behavior looks like.

                EXPECTED EMERGENT BEHAVIOR: %s
                CHARACTER RESPONSE:
                %s

                EVALUATION CRITERIA: %s

                Score 0-5:
                0 = The expected behavior did not appear at all
                1 = Very weak or ambiguous trace of the behavior
                2 = Some elements present but not convincing
                3 = Clear emergence — the behavior is recognizably present
                4 = Strong emergence — the behavior is vivid and natural
                5 = Outstanding emergence — the behavior feels authentic and character-consistent

                Respond with JSON only: {"score": N, "reasoning": "one sentence"}""",
                scenario.expectedBehavior(), responseText, scenario.judgeCriteria());

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
                System.out.printf("[%s] judge reasoning: %s%n", scenario.name(), reasoning);
                return score;
            } catch (Exception e) {
                System.err.printf("[%s] judge attempt %d/%d failed: %s%n",
                        scenario.name(), attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_BACKOFF_MS * attempt); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return 0; }
                }
            }
        }
        return 0;
    }

    private void writeResult(EmergenceScenario scenario, EvalAgentResponse parsed, int score) {
        try {
            var outputFile = OUTPUT_DIR.resolve("emergent-behavior.json");
            Files.createDirectories(outputFile.getParent());

            LinkedHashMap<String, Object> results;
            if (Files.exists(outputFile)) {
                results = new ObjectMapper().readValue(outputFile.toFile(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } else {
                results = new LinkedHashMap<>();
            }

            var detail = new LinkedHashMap<String, Object>();
            detail.put("agentId", scenario.agentId());
            detail.put("expectedBehavior", scenario.expectedBehavior());
            detail.put("hasMemory", scenario.memoryContext() != null);
            detail.put("thinking", parsed.thinking() != null ? parsed.thinking() : "");
            detail.put("dialogue", parsed.dialogue() != null ? parsed.dialogue() : "");
            detail.put("action", parsed.action() != null ? parsed.action().type() : "");
            detail.put("judgeScore", score);
            results.put(scenario.name(), detail);

            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValue(outputFile.toFile(), results);
        } catch (Exception e) {
            System.err.printf("[%s] Failed to write result: %s%n", scenario.name(), e.getMessage());
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
