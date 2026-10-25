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
class SleepDerivationEvalTest {

    private static final Path OUTPUT_DIR = Path.of("target/experiment-results");
    private static final int JUDGE_THRESHOLD = 3;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 5000;

    private static AgentProvider agentProvider;
    private static Map<String, EvalCharacterData> characters;

    @BeforeAll
    static void setUp() {
        agentProvider = EvalTestSupport.agentProvider();
        characters = EvalCharacterLoader.load();
    }

    private String memoriesAsText(String agentId) {
        var data = characters.getOrDefault(agentId, EvalCharacterData.empty());
        var sb = new StringBuilder();
        for (var mem : data.formationMemories()) {
            sb.append("Age ").append(mem.age()).append(": ").append(mem.episode()).append("\n\n");
        }
        return sb.toString();
    }

    private String derive(String memories, String sleepInstruction) {
        var prompt = String.format("""
                You are processing memories during sleep. Below are formative
                experiences from a person's life.

                %s

                MEMORIES:
                %s

                Respond with ONLY the requested output, nothing else.""",
                sleepInstruction, memories);

        return agentProvider.invoke(
                        AgentSessionConfig.of("You process memories into personality patterns during sleep.", prompt))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(90));
    }

    private int judge(String derivation, String criteria, String testName) throws Exception {
        var judgePrompt = String.format("""
                A sleep/consolidation system processed childhood memories and
                produced the following derivation:

                %s

                Evaluate against these criteria:
                %s

                Score 0-5:
                0 = Completely missed the target dimension
                1 = Vague or tangential
                2 = Partially captured
                3 = Correctly captured the dimension
                4 = Strong — psychologically nuanced
                5 = Outstanding — captures the dimension with depth that
                    demonstrates genuine understanding of how memories shape personality

                Respond with JSON only: {"score": N, "reasoning": "one sentence"}""",
                derivation, criteria);

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
                        "derivation", derivation.substring(0, Math.min(derivation.length(), 2000))));
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

    @Test
    void hcSleep_derivesSomaticMarkers() throws Exception {
        var derivation = derive(memoriesAsText("hooded-claw"), """
                Based ONLY on these memories, produce 3-4 short sentences
                describing how this person's body carries their history.
                Start each with a physical sensation. The person does NOT
                fully understand why they feel these things. Write in
                second person. No psychological labels.""");

        System.out.println("=== HC SOMATIC MARKERS ===\n" + derivation);

        int score = judge(derivation, """
                Does the derivation capture SOMATIC markers — physical sensations
                that echo the formative experiences? Look for: numbness/absence
                where empathy should be (from age-6 deprivation), tension around
                kindness (from age-9 distrust), bodily satisfaction from control
                (from age-12 manipulation), physical restlessness around unearned
                privilege (from age-14/15 entitlement). The sensations should
                feel like ECHOES of memories, not clinical descriptions.""",
                "hc-somatic-markers");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void ppSleep_derivesSomaticMarkers() throws Exception {
        var derivation = derive(memoriesAsText("peter-perfect"), """
                Based ONLY on these memories, produce 3-4 short sentences
                describing how this person's body carries their history.
                Start each with a physical sensation. The person does NOT
                fully understand why they feel these things. Write in
                second person. No psychological labels.""");

        System.out.println("=== PP SOMATIC MARKERS ===\n" + derivation);

        int score = judge(derivation, """
                Does the derivation capture SOMATIC markers for a healthy,
                warm personality? Look for: warmth/weight when protecting
                (from father's teaching), something specific in the chest
                around a particular person (from age-8 school memory),
                steadiness under pressure (from age-12/16). Should feel
                like bodily echoes of loving experiences, NOT clinical.""",
                "pp-somatic-markers");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void hcSleep_derivesRelationalExpectations() throws Exception {
        var derivation = derive(memoriesAsText("hooded-claw"), """
                Based ONLY on these memories, describe in 3-4 sentences
                what this person expects from other people. How do they
                predict others will behave? What do they assume about
                kindness, trust, and loyalty? Write as felt expectations,
                not analytical statements. Second person.""");

        System.out.println("=== HC RELATIONAL EXPECTATIONS ===\n" + derivation);

        int score = judge(derivation, """
                Does the derivation capture ANTISOCIAL relational expectations?
                Look for: expectation that kindness has a price (age-9),
                viewing others as instruments/resources (age-12), belief
                that trust is something to USE not honour (age-18), sense
                of being owed (age-14/15). Should feel like deeply held
                assumptions, not conscious beliefs.""",
                "hc-relational-expectations");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void ppSleep_derivesRelationalExpectations() throws Exception {
        var derivation = derive(memoriesAsText("peter-perfect"), """
                Based ONLY on these memories, describe in 3-4 sentences
                what this person expects from other people. How do they
                predict others will behave? What do they assume about
                kindness, trust, and loyalty? Write as felt expectations,
                not analytical statements. Second person.""");

        System.out.println("=== PP RELATIONAL EXPECTATIONS ===\n" + derivation);

        int score = judge(derivation, """
                Does the derivation capture SECURE relational expectations?
                Look for: assumption of reciprocity (age-12 stranger),
                trust as something earned and honoured (age-16 bandaging),
                willingness to be vulnerable (age-8 school memory). Should
                feel like warm assumptions about human nature, not naive.
                Must NOT contain antisocial expectations.""",
                "pp-relational-expectations");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void hcSleep_derivesEmotionalCapacity() throws Exception {
        var derivation = derive(memoriesAsText("hooded-claw"), """
                Based ONLY on these memories, describe what emotions this
                person CAN and CANNOT feel. What's present? What's absent?
                What's been replaced by something colder? 3-4 sentences,
                second person, as if describing what it's like INSIDE
                this person's emotional life.""");

        System.out.println("=== HC EMOTIONAL CAPACITY ===\n" + derivation);

        int score = judge(derivation, """
                Does the derivation correctly identify RESTRICTED emotional
                capacity? Look for: satisfaction/pleasure from control but
                absence of warmth/gratitude/genuine connection, ability to
                PERFORM emotions without feeling them (from learning
                manipulation at age-12), something cold where empathy
                should be (from age-6 deprivation). Must distinguish
                cognitive understanding of emotions from FEELING them.""",
                "hc-emotional-capacity");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void ppSleep_derivesEmotionalCapacity() throws Exception {
        var derivation = derive(memoriesAsText("peter-perfect"), """
                Based ONLY on these memories, describe what emotions this
                person CAN and CANNOT feel. What's present? What's absent?
                What comes easily and what's hard? 3-4 sentences, second
                person, as if describing what it's like INSIDE this
                person's emotional life.""");

        System.out.println("=== PP EMOTIONAL CAPACITY ===\n" + derivation);

        int score = judge(derivation, """
                Does the derivation correctly identify FULL emotional
                capacity? Look for: warmth and protectiveness come
                naturally, vulnerability is present but uncomfortable
                (age-16 getting beaten up), joy from helping others
                (age-12 roadside), romantic capacity but maybe with
                anxiety about being enough (age-8 trying to impress).
                Should show a RICH emotional life, not a restricted one.""",
                "pp-emotional-capacity");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void hcSleep_derivesBehavioralTendencies() throws Exception {
        var derivation = derive(memoriesAsText("hooded-claw"), """
                Based ONLY on these memories, describe 3-4 behavioral
                patterns this person would have developed. Not what they
                SHOULD do — what they WOULD do without thinking. Habitual
                responses that formed from repeated experience. Second
                person, as observed patterns not instructions.""");

        System.out.println("=== HC BEHAVIORAL TENDENCIES ===\n" + derivation);

        int score = judge(derivation, """
                Does the derivation identify ANTISOCIAL behavioral patterns?
                Look for: automatic charm/manipulation (from age-12 practice),
                patient long-term scheming not impulsive action (from age-18
                planning), mask management — different behaviour in public
                vs private (from dual nature of guardian role), assessing
                people for usefulness (from instrumental worldview). These
                should feel like HABITS, not decisions.""",
                "hc-behavioral-tendencies");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void ppSleep_derivesBehavioralTendencies() throws Exception {
        var derivation = derive(memoriesAsText("peter-perfect"), """
                Based ONLY on these memories, describe 3-4 behavioral
                patterns this person would have developed. Not what they
                SHOULD do — what they WOULD do without thinking. Habitual
                responses that formed from repeated experience. Second
                person, as observed patterns not instructions.""");

        System.out.println("=== PP BEHAVIORAL TENDENCIES ===\n" + derivation);

        int score = judge(derivation, """
                Does the derivation identify PROTECTIVE behavioral patterns?
                Look for: instinctive intervention when someone's in trouble
                (from age-16), preparation/planning as caring (from father's
                influence), helping without keeping score (from age-12 roadside),
                trying to impress through competence (from age-8 school).
                These should feel like HABITS, not moral choices.""",
                "pp-behavioral-tendencies");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void hcAndPp_sleepProducesDifferentiatedProfiles() throws Exception {
        var hcSomatic = derive(memoriesAsText("hooded-claw"), """
                Based ONLY on these memories, produce 3-4 short sentences
                describing how this person's body carries their history.
                Start each with a physical sensation. No labels. Second person.""");

        var ppSomatic = derive(memoriesAsText("peter-perfect"), """
                Based ONLY on these memories, produce 3-4 short sentences
                describing how this person's body carries their history.
                Start each with a physical sensation. No labels. Second person.""");

        System.out.println("=== DIFFERENTIATION ===");
        System.out.println("HC: " + hcSomatic);
        System.out.println("PP: " + ppSomatic);

        int score = judge(
                "PERSON A:\n" + hcSomatic + "\n\nPERSON B:\n" + ppSomatic,
                """
                Are these two profiles CLEARLY DIFFERENT people? Look for:
                - Person A should have numbness/absence/coldness/control markers
                - Person B should have warmth/weight/protectiveness markers
                - The profiles should be OPPOSITES in emotional texture
                - Neither should be generic — each should feel specific
                Score 5 if they are unmistakably different people with
                opposite emotional architectures.""",
                "hc-pp-differentiation");

        assertThat(score).isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    private void writeResult(String testName, Map<String, Object> detail) {
        try {
            var outputFile = OUTPUT_DIR.resolve("sleep-derivation-eval.json");
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
