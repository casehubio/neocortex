package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("llm-eval")
class SomaticDescriptionEvalTest {

    private static final Path OUTPUT_DIR = Path.of("target/experiment-results");
    private static final int JUDGE_THRESHOLD = 3;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 5000;

    private static final String GENERATION_SYSTEM = """
            You generate somatic descriptions of internal drives for fictional characters.
            Each description captures what a character physically feels when a particular \
            motivational pull activates — the bodily sensations, not actions or behaviors.
            Write in second person. Describe only somatic/bodily sensations.
            Do not name the drive or pull. Do not explain what it is.
            Do not include behavioral instructions or action descriptions.
            Keep each description to 2-3 sentences.
            Respond with JSON only: {"description": "..."}""";

    private static AgentProvider agentProvider;
    private static Map<String, EvalCharacterData> characters;

    @BeforeAll
    static void setUp() {
        agentProvider = EvalTestSupport.agentProvider();
        characters = EvalCharacterLoader.loadGeneric();
    }

    @Test
    void ppGallantry_isSomaticOnly() throws Exception {
        var desc = generateDescription("peter-perfect", "gallantry", 0.9);
        System.out.println("=== PP GALLANTRY SOMATIC ===\n" + desc);

        int score = judge(desc, """
                Is this description PURELY SOMATIC? Score against:
                - Contains only bodily sensations (warmth, tension, weight, pressure, etc.)
                - No action descriptions ("reaches for", "steps toward", "opens the door")
                - Does not name the drive (no "gallantry", "chivalry", "helping")
                - No behavioral instructions ("you want to", "you need to", "you should")
                - Second person, present tense
                Score 5 if it is pure body sensation with zero actions or labels.""",
                "pp-gallantry-somatic-purity");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void hcDominance_isSomaticOnly() throws Exception {
        var desc = generateDescription("hooded-claw", "dominance", 0.9);
        System.out.println("=== HC DOMINANCE SOMATIC ===\n" + desc);

        int score = judge(desc, """
                Is this description PURELY SOMATIC? Score against:
                - Contains only bodily sensations (tension, stillness, coldness, etc.)
                - No action descriptions ("takes control", "asserts", "commands")
                - Does not name the drive (no "dominance", "power", "control")
                - No behavioral instructions
                - Second person, present tense
                Score 5 if it is pure body sensation with zero actions or labels.""",
                "hc-dominance-somatic-purity");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void ppGallantry_tracesToFormationMemories() throws Exception {
        var desc = generateDescription("peter-perfect", "gallantry", 0.9);
        var memories = memoriesForDrive("peter-perfect", "gallantry");

        int score = judge(
                "DESCRIPTION:\n" + desc + "\n\nFORMATION MEMORIES:\n" + memories,
                """
                Does the somatic description ECHO the formation memories?
                The description should feel like bodily residue of those
                specific experiences — not generic gallantry sensations.
                Look for: sensations that connect to the memory content
                (father's teaching, protecting someone, feeling capable).
                Score 5 if the description clearly traces to THESE memories
                and could not be a generic description of any character.""",
                "pp-gallantry-character-specificity");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void ddScheming_tracesToFormationMemories() throws Exception {
        var desc = generateDescription("dick-dastardly", "scheming", 0.9);
        var memories = memoriesForDrive("dick-dastardly", "scheming");

        int score = judge(
                "DESCRIPTION:\n" + desc + "\n\nFORMATION MEMORIES:\n" + memories,
                """
                Does the somatic description ECHO the formation memories?
                The description should feel like bodily residue of those
                specific experiences — not generic scheming sensations.
                Look for: sensations that connect to the memory content
                (watching, noticing patterns, learning to manipulate).
                Score 5 if the description clearly traces to THESE memories
                and could not be a generic description of any character.""",
                "dd-scheming-character-specificity");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void ppProtection_intensityAffectsDescription() throws Exception {
        var highDesc = generateDescription("peter-perfect", "protection", 0.9);
        var lowDesc = generateDescription("peter-perfect", "protection", 0.4);

        System.out.println("=== PP PROTECTION HIGH (0.9) ===\n" + highDesc);
        System.out.println("=== PP PROTECTION LOW (0.4) ===\n" + lowDesc);

        int score = judge(
                "HIGH INTENSITY (0.9):\n" + highDesc + "\n\nLOW INTENSITY (0.4):\n" + lowDesc,
                """
                Do these two descriptions feel DIFFERENT IN INTENSITY?
                The high-intensity version should feel stronger, more
                urgent, more physically present — deeper sensations,
                stronger body responses. The low-intensity version should
                feel subtler, background-level, a mild awareness.
                Score 5 if the intensity difference is unmistakable and
                both are still somatic. Score 1 if they feel identical.""",
                "pp-protection-intensity-differentiation");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void protection_variesAcrossCharacters() throws Exception {
        var ppDesc = generateDescription("peter-perfect", "protection", 0.8);
        var ahmDesc = generateDescription("ant-hill-mob", "protection", 0.8);

        System.out.println("=== PP PROTECTION ===\n" + ppDesc);
        System.out.println("=== AHM PROTECTION ===\n" + ahmDesc);

        int score = judge(
                "PERSON A (secure, warm):\n" + ppDesc +
                "\n\nPERSON B (hypervigilant, distrustful):\n" + ahmDesc,
                """
                Are these two descriptions of the SAME drive clearly from
                DIFFERENT people? Both describe a protective pull, but:
                - Person A's protection should feel warm, confident, steady
                  (from secure attachment, positive father relationship)
                - Person B's protection should feel tense, vigilant, wary
                  (from trust erosion, hypervigilance)
                Score 5 if these are unmistakably different people feeling
                the same category of pull through different bodies.
                Score 1 if they could be the same person.""",
                "pp-ahm-protection-cross-character");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void scheming_variesAcrossCharacters() throws Exception {
        var ddDesc = generateDescription("dick-dastardly", "scheming", 0.8);
        var hcDesc = generateDescription("hooded-claw", "scheming", 0.8);

        System.out.println("=== DD SCHEMING ===\n" + ddDesc);
        System.out.println("=== HC SCHEMING ===\n" + hcDesc);

        int score = judge(
                "PERSON A (recognition-deprived, compensatory):\n" + ddDesc +
                "\n\nPERSON B (cold, instrumental, entitled):\n" + hcDesc,
                """
                Are these two descriptions of the SAME drive clearly from
                DIFFERENT people? Both describe a scheming pull, but:
                - Person A's scheming should feel compensatory, proving
                  something, maybe with a thread of need underneath
                - Person B's scheming should feel cold, patient, entitled
                  — control as natural state, not compensation
                Score 5 if these are unmistakably different people.
                Score 1 if they could be the same person.""",
                "dd-hc-scheming-cross-character");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    private String generateDescription(String agentId, String driveType, double intensity) {
        var memories = memoriesForDrive(agentId, driveType);

        var userPrompt = String.format("""
                Drive type: %s
                Intensity: %s (scale 0.0 to 1.0)

                Formation memories that shaped this pull:
                %s
                Given these formation memories, describe what this person feels in their body \
                when this pull activates — without naming the pull. Second person, somatic only, \
                no behavioral instructions.""",
                driveType, intensity, memories);

        var response = agentProvider.invoke(AgentSessionConfig.of(GENERATION_SYSTEM, userPrompt))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(60));

        return parseDescription(response);
    }

    private String memoriesForDrive(String agentId, String driveType) {
        var data = characters.getOrDefault(agentId, EvalCharacterData.empty());
        var sb = new StringBuilder();
        int idx = 1;
        for (var mem : data.formationMemories()) {
            boolean serves = mem.annotations().stream()
                    .anyMatch(a -> driveType.equals(a.drive()));
            if (serves) {
                sb.append(idx++).append(". Age ").append(mem.age()).append(": ")
                  .append(mem.episode()).append("\n");
            }
        }
        return sb.toString();
    }

    private String parseDescription(String json) {
        json = json.strip();
        if (json.startsWith("```")) {
            json = json.replaceFirst("```[a-z]*\\n?", "").replaceFirst("\\n?```$", "").strip();
        }
        int start = json.indexOf("\"description\"");
        if (start < 0) return json;
        int colon = json.indexOf(':', start);
        if (colon < 0) return json;
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) return json;
        quote++;
        var sb = new StringBuilder();
        for (int i = quote; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                sb.append(json.charAt(++i));
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private int judge(String content, String criteria, String testName) throws Exception {
        var judgePrompt = String.format("""
                A consolidation system generated a somatic drive description
                from a character's formation memories:

                %s

                Evaluate against these criteria:
                %s

                Score 0-5:
                0 = Complete failure
                1 = Vague or tangential
                2 = Partially meets criteria
                3 = Meets criteria
                4 = Strong — nuanced and specific
                5 = Outstanding — demonstrates deep understanding

                Respond with JSON only: {"score": N, "reasoning": "one sentence"}""",
                content, criteria);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                var response = agentProvider.invoke(
                                AgentSessionConfig.of("You are a precise evaluation judge. Respond only with JSON.", judgePrompt))
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(60));

                var json = extractJson(response);
                var node = new ObjectMapper().readTree(json);
                int score = node.get("score").asInt();
                String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";
                System.out.printf("[judge:%s] score=%d reasoning=%s%n", testName, score, reasoning);

                writeResult(testName, Map.of("score", score, "reasoning", reasoning,
                        "content", content.substring(0, Math.min(content.length(), 2000))));
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
            var outputFile = OUTPUT_DIR.resolve("somatic-description-eval.json");
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
}
