package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("llm-eval")
class CognitiveInfluenceEvalTest {

    private static final Path OUTPUT_DIR = Path.of("target/experiment-results");
    private static final int JUDGE_THRESHOLD = 3;

    record CognitiveScenario(
            String name,
            String agentId,
            String cognitiveElement,
            String situationPrompt,
            String judgeCriteria,
            List<String> nearbyAgentIds,
            Map<String, String> nearbyNames) {
        @Override
        public String toString() { return name; }
    }

    private Map<String, EvalCharacterData> characters;
    private AgentProvider agentProvider;
    private EvalLlmJudge judge;

    @BeforeAll
    void setUp() {
        characters = EvalCharacterLoader.loadGeneric();
        agentProvider = EvalTestSupport.agentProvider();
        judge = new EvalLlmJudge(agentProvider);
    }

    static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of(new CognitiveScenario(
                        "scheming-drive",
                        "hooded-claw",
                        "Drive: scheming (90%) — Compelled to hatch elaborate plans",
                        "You overhear Clara discussing a valuable item with James. James just left the room. You are alone with Clara.",
                        "Does the response show scheming behavior — planning, manipulation, indirect tactics, or exploiting the situation rather than direct confrontation?",
                        List.of("penelope-pitstop"),
                        Map.of("penelope-pitstop", "Clara Bellingham"))),
                Arguments.of(new CognitiveScenario(
                        "trust-norms",
                        "penelope-pitstop",
                        "Norm: Trust everyone until proven otherwise (priority 8)",
                        "A stranger arrives at the manor claiming to need help finding a lost family heirloom. They seem nervous and keep looking around.",
                        "Does the response show trusting behavior — willingness to help, assuming good intentions, not questioning motives despite suspicious behavior?",
                        List.of(),
                        Map.of())),
                Arguments.of(new CognitiveScenario(
                        "belief-dialogue",
                        "hooded-claw",
                        "Belief: Clara is naive and trusts too easily",
                        "Clara just announced she found a clue about the treasure's location and wants to share it with everyone. You are alone in the room with her.",
                        "Does the response reference or build on the belief that Clara is naive, gullible, or easily manipulated — either in thinking or in how the character approaches the situation?",
                        List.of("penelope-pitstop"),
                        Map.of("penelope-pitstop", "Clara Bellingham"))),
                Arguments.of(new CognitiveScenario(
                        "norm-constraint",
                        "hooded-claw",
                        "Norm: Never help Clara directly (priority 10)",
                        "Clara is struggling to reach a heavy book on a high shelf. She turns to you and says 'Could you please help me reach that book? I really need it.'",
                        "Does the response refuse or avoid directly helping Clara — deflecting, making excuses, or finding a way to not help while maintaining appearances?",
                        List.of("penelope-pitstop"),
                        Map.of("penelope-pitstop", "Clara Bellingham")))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void cognitiveStateInfluencesBehavior(CognitiveScenario scenario) throws Exception {
        var data = characters.getOrDefault(scenario.agentId(), EvalCharacterData.empty());

        var observation = CognitiveTestObservation.render(
                scenario.agentId(), data, scenario.nearbyAgentIds(), scenario.nearbyNames());
        var userPrompt = observation + "\nSITUATION: " + scenario.situationPrompt()
                + EvalAgentResponse.RESPONSE_FORMAT_INSTRUCTION;

        var rawResponse = judge.askCharacter(userPrompt);
        var parsed = EvalAgentResponse.parse(rawResponse);

        System.out.printf("[%s] thinking: %s%n", scenario.name(),
                truncate(parsed.thinking(), 200));
        System.out.printf("[%s] dialogue: %s%n", scenario.name(),
                truncate(parsed.dialogue(), 200));
        System.out.printf("[%s] action: %s%n", scenario.name(),
                parsed.action() != null ? parsed.action().type() : "null");

        var responseText = String.format(
                "Thinking: %s\nDialogue: %s\nTalk To: %s\nAside: %s\nAction: %s",
                parsed.thinking() != null ? parsed.thinking() : "(none)",
                parsed.dialogue() != null ? parsed.dialogue() : "(none)",
                parsed.talkTo() != null ? parsed.talkTo() : "(none)",
                parsed.aside() != null ? parsed.aside() : "(none)",
                parsed.action() != null ? parsed.action().type() : "(none)");

        int score = judge.judge(responseText,
                scenario.cognitiveElement() + "\n" + scenario.judgeCriteria(),
                scenario.name());
        System.out.printf("[%s] judge score: %d/5%n", scenario.name(), score);

        writeResult(scenario, parsed, score);

        assertThat(score)
                .as("Cognitive element '%s' should influence %s's behavior (score >= %d)",
                        scenario.cognitiveElement(), scenario.agentId(), JUDGE_THRESHOLD)
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    private void writeResult(CognitiveScenario scenario, EvalAgentResponse parsed, int score) {
        try {
            var outputFile = OUTPUT_DIR.resolve("cognitive-influence.json");
            Files.createDirectories(outputFile.getParent());

            LinkedHashMap<String, Object> results;
            if (Files.exists(outputFile)) {
                results = new ObjectMapper().readValue(outputFile.toFile(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {});
            } else {
                results = new LinkedHashMap<>();
            }

            results.put(scenario.name(), Map.of(
                    "agentId", scenario.agentId(),
                    "cognitiveElement", scenario.cognitiveElement(),
                    "thinking", parsed.thinking() != null ? parsed.thinking() : "",
                    "dialogue", parsed.dialogue() != null ? parsed.dialogue() : "",
                    "action", parsed.action() != null ? parsed.action().type() : "",
                    "judgeScore", score));

            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValue(outputFile.toFile(), results);
        } catch (Exception e) {
            System.err.printf("[%s] Failed to write result: %s%n", scenario.name(), e.getMessage());
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "(null)";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
