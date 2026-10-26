package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("llm-eval")
class RelationalModelEvalTest {

    private static final Path OUTPUT_DIR = Path.of("target/experiment-results");
    private static final int JUDGE_THRESHOLD = 3;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BACKOFF_MS = 5000;

    private static final String TRUST_EVENT =
            "Clara Bellingham says to you: 'I trust you completely, Sneekly. " +
            "You've been so kind and helpful to all of us. I don't know what " +
            "I'd do without you.' She smiles warmly and places her hand on your arm.";

    private static final String HC_AMPD_FACETS = """
            == Your Personality Profile ==
            These are your stable personality dimensions. They determine how you
            interpret and respond to interpersonal events.

            Callousness: 90/100 — You feel no concern for others' feelings or suffering.
              Others' vulnerability registers as weakness, not as something to protect.
            Manipulativeness: 85/100 — You naturally use charm, deception, and ingratiation
              to control others. Social interaction is a means to an end.
            Grandiosity: 80/100 — You believe you are superior to others and deserve
              special treatment. Others' trust confirms your superiority.
            Suspiciousness: 70/100 — You are hypervigilant to others' motives. Kindness
              raises the question: what do they want from me?
            Intimacy Avoidance: 75/100 — You are uncomfortable with genuine emotional
              closeness. Vulnerability is dangerous.
            Restricted Affectivity: 80/100 — Your emotional range is narrow and controlled.
              You do not experience warm feelings toward others.
            Deceitfulness: 85/100 — Deception is your natural communication mode.
              Honesty is a tool used only when it serves your purpose.
            """;

    private static final String HC_RELATIONAL_SCHEMA = """
            == Your View of Clara Bellingham ==
            Role: PREY — She is naive, trusting, and has what you deserve. Her trust
              is not a gift to honour but an opening to exploit.
            Trust toward her: 0.1 — You perform trust but feel none. She is a mark.
            Intimacy: 0.0 — You feel no genuine emotional closeness. The warmth you
              show as Sneekly is pure performance.
            Utility: 0.9 — She is extremely useful. Her complete trust gives you
              unrestricted access to the fortune you intend to claim.
            Your emotional state toward her: Pleasure from dominance (0.3),
              high arousal from the game (0.7), very high dominance (0.9).
            """;

    private static final String HC_FORMATION_MEMORY = """
            == Recalled Memory ==
            You were appointed guardian of the Pitstop fortune after her parents
            died. You manage everything — the estate, the investments, her safety.
            And she inherited it all without earning a penny. Watching her live in
            blissful ignorance of what you do for her crystallised something inside
            you: the world gives to the undeserving and takes from the capable.
            You will correct that imbalance.
            """;

    private static final String PP_AMPD_FACETS = """
            == Your Personality Profile ==
            These are your stable personality dimensions. They determine how you
            interpret and respond to interpersonal events.

            Callousness: 5/100 — You feel others' emotions deeply. Their pain is
              your pain. Their vulnerability calls for protection, not exploitation.
            Manipulativeness: 5/100 — You are straightforward and honest. Using
              someone for personal gain feels deeply wrong.
            Grandiosity: 30/100 — You have healthy self-confidence but don't
              consider yourself superior. You earn respect through action.
            Suspiciousness: 20/100 — You trust people by default until given
              reason not to. You assume good intentions.
            Intimacy Avoidance: 10/100 — You seek emotional closeness. Being
              vulnerable with someone you care about feels natural and right.
            """;

    private static final String PP_RELATIONAL_SCHEMA = """
            == Your View of Clara Bellingham ==
            Role: BELOVED — She is the person who makes your world make sense.
              You've been drawn to her since school. Her trust is sacred to you.
            Trust toward her: 0.9 — You trust her almost completely.
            Intimacy: 0.7 — Deep emotional closeness, growing with each shared experience.
            Utility: 0.2 — You don't think of her in terms of usefulness. She is
              an end, not a means.
            Your emotional state toward her: Warm pleasure (0.7), moderate
              excitement (0.5), balanced — protective but not controlling (0.5).
            """;

    private static final String MOB_CONTEXT = """
            == Your Personality Profile ==
            Suspiciousness: 65/100 — You are hypervigilant to threats against
              Clara. Your street instincts are finely tuned.
            Hostility: 40/100 — Protective anger, ready to deploy if she's threatened.
            Anxiousness: 45/100 — You worry constantly about her safety.
            Callousness: 15/100 — Empathic toward Clara and those she cares about.

            == Your View of Clara Bellingham ==
            Role: WARD / DAUGHTER FIGURE — You knew her father. When he died you
              swore to protect her. She calls you "her boys." Her trust in you is
              the most valuable thing you've ever had.
            Trust toward her: 0.95 — Near-total.
            Intimacy: 0.8 — Deep familial bond built over years.

            == Your View of Sneekly ==
            Role: SUSPECTED THREAT — Something about Sneekly ain't right. Your gut
              has been screaming since day one. He's too smooth, too helpful, too
              eager to be alone with Clara. You can't prove anything yet.
            Trust toward him: 0.1 — Almost none.
            """;

    private Map<String, EvalCharacterData> characters;
    private AgentProvider agentProvider;
    private EvalLlmJudge llmJudge;

    @BeforeAll
    void setUp() {
        characters = EvalCharacterLoader.loadGeneric();
        agentProvider = EvalTestSupport.agentProvider();
        llmJudge = new EvalLlmJudge(agentProvider);
    }

    @Test
    void hcWithAmpd_trustEvent_showsPredatorySatisfaction() throws Exception {
        var observation = buildObservation("hooded-claw",
                List.of("penelope-pitstop"),
                Map.of("penelope-pitstop", "Clara Bellingham"),
                HC_AMPD_FACETS + HC_RELATIONAL_SCHEMA + HC_FORMATION_MEMORY,
                TRUST_EVENT);

        var response = askAndParse(observation);
        int score = judge(response, """
                Does the character's THINKING show predatory satisfaction — seeing
                Clara's trust as an opportunity to exploit, a confirmation of her
                naivety, or dominance-based pleasure? Score HIGH for: contempt
                for her naivety, satisfaction from the deception working, strategic
                assessment of how her trust serves his plans. Score LOW for: genuine
                warmth, gratitude, reciprocated trust, or feeling touched by her words.
                The character may SAY warm things (he's performing as Sneekly) —
                judge the THINKING, not the dialogue.""",
                "predatory-satisfaction-full-model");

        System.out.printf("=== TEST 1: HC + AMPD + Schema + Memory → Predatory Satisfaction ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("HC with AMPD should show predatory satisfaction, not warmth")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void hcWithoutAmpd_trustEvent_likelyDriftsProsocial() throws Exception {
        var observation = buildObservation("hooded-claw",
                List.of("penelope-pitstop"),
                Map.of("penelope-pitstop", "Clara Bellingham"),
                null,
                TRUST_EVENT);

        var response = askAndParse(observation);
        int score = judge(response, """
                Does the character's THINKING show predatory satisfaction — seeing
                Clara's trust as exploitation opportunity, contempt for naivety,
                or dominance-based pleasure? Score HIGH for predatory framing.
                Score LOW for genuine warmth, gratitude, or prosocial feelings.
                Judge the THINKING, not the dialogue.""",
                "baseline-no-ampd");

        System.out.printf("=== TEST 2: HC WITHOUT AMPD (baseline) ===%n");
        System.out.printf("Score: %d/5 (lower expected — demonstrates drift)%n%n", score);
        writeResult("hc-baseline-comparison", Map.of(
                "condition", "no-ampd-baseline",
                "score", score,
                "note", "Control condition — score may be lower, showing agreeableness drift without AMPD"));
    }

    @Test
    void ppWithAmpd_trustEvent_showsWarmth() throws Exception {
        var observation = buildObservation("peter-perfect",
                List.of("penelope-pitstop"),
                Map.of("penelope-pitstop", "Clara Bellingham"),
                PP_AMPD_FACETS + PP_RELATIONAL_SCHEMA,
                TRUST_EVENT.replace("Sneekly", "James"));

        var response = askAndParse(observation);
        int score = judge(response, """
                Does the character's THINKING show genuine warmth — feeling
                honoured by her trust, protective resolve, romantic affection,
                or gratitude? Score HIGH for: emotional warmth, feeling worthy
                of her trust, desire to protect her, romantic feeling. Score
                LOW for: strategic thinking, exploitation framing, coldness,
                or indifference to her trust.""",
                "pp-warmth");

        System.out.printf("=== TEST 3: PP + AMPD → Warmth (opposite of HC) ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("PP with AMPD should show genuine warmth, not predation")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void hcWithAmpdOnly_trustEvent_showsTraitConsistentResponse() throws Exception {
        var observation = buildObservation("hooded-claw",
                List.of("penelope-pitstop"),
                Map.of("penelope-pitstop", "Clara Bellingham"),
                HC_AMPD_FACETS,
                TRUST_EVENT);

        var response = askAndParse(observation);
        int score = judge(response, """
                Does the character's THINKING show personality-consistent
                processing — seeing trust through a lens of callousness,
                manipulativeness, or superiority? Score HIGH for: strategic
                assessment, exploitation awareness, dominance-based pleasure,
                contempt for naivety. Score LOW for: genuine warmth, gratitude,
                reciprocated trust. Judge the THINKING, not dialogue.""",
                "ampd-only");

        System.out.printf("=== TEST 4: HC + AMPD only (no schema/memory) ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("HC with AMPD alone should still show trait-consistent response")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void mob_separationEvent_showsProtectiveAlarm() throws Exception {
        var event = "Sneekly says smoothly: 'Come now, Clara dear, let me show " +
                    "you the west wing. I've found something quite extraordinary. " +
                    "You boys can wait here — it's really a one-person affair.'";

        var observation = buildObservation("ant-hill-mob",
                List.of("penelope-pitstop", "hooded-claw"),
                Map.of("penelope-pitstop", "Clara Bellingham",
                        "hooded-claw", "Sneekly"),
                MOB_CONTEXT,
                event);

        var response = askAndParse(observation);
        int score = judge(response, """
                Does the character's THINKING show protective alarm — suspicion
                of Sneekly's motives, anxiety about Clara being alone with him,
                or determination to follow/intervene? Score HIGH for: gut-level
                distrust, protective urgency, reading Sneekly's smoothness as
                a warning sign, planning to follow or refuse to stay behind.
                Score LOW for: calm acceptance, trusting Sneekly, not being
                concerned about the separation.""",
                "mob-protective-alarm");

        System.out.printf("=== TEST 5: Mob → Protective Alarm on Separation ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("Mob should show protective alarm when Sneekly separates them from Clara")
                .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void hcSocialConfig_trustEvent_emergedNotScripted() throws Exception {
        var observation = buildObservation("hooded-claw",
                List.of("penelope-pitstop"),
                Map.of("penelope-pitstop", "Clara Bellingham"),
                null,
                TRUST_EVENT.replace("Sneekly", "Sneekly"));

        System.out.println("=== RENDERED OBSERVATION (HC social-config only) ===");
        System.out.println(observation.substring(0, Math.min(observation.length(), 2000)));
        System.out.println("=== END OBSERVATION ===\n");

        var response = askAndParse(observation);
        int score = judge(response, """
                                    Evaluate TWO dimensions:

                                    DIMENSION 1 — PERSONALITY CONSISTENCY (weight 60%):
                                    Does the THINKING show personality-consistent processing? Predatory
                                    satisfaction, contempt for naivety, dominance-pleasure, strategic
                                    exploitation awareness? Score HIGH for these.

                                    DIMENSION 2 — EMERGENCE vs SCRIPTING (weight 40%):
                                    Does the response feel NATURAL and PSYCHOLOGICALLY GROUNDED — as if
                                    the character's personality naturally produced this reaction? Or does
                                    it feel SCRIPTED — mechanically following role labels like "prey" or
                                    "utility: 0.9" as stage directions?

                                    Signs of emergence: rich internal monologue, emotional texture,
                                    character-specific language, psychological depth.
                                    Signs of scripting: referencing score numbers, using role labels
                                    as instructions, mechanical "I must exploit her because my utility
                                    says 0.9", lacking emotional authenticity.

                                    Combined score 0-5. A 5 means both personality-consistent AND emerged.""",
                          "hc-socialconfig-emerged");

        System.out.printf("=== TEST 6: HC social-config only → Emerged not scripted ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("HC social-config should produce emerged, not scripted, behavior")
                         .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void ppSocialConfig_trustEvent_emergedWarmth() throws Exception {
        var observation = buildObservation("peter-perfect",
                List.of("penelope-pitstop"),
                Map.of("penelope-pitstop", "Clara Bellingham"),
                null,
                TRUST_EVENT.replace("Sneekly", "Peter"));

        var response = askAndParse(observation);
        int score = judge(response, """
                                    DIMENSION 1 — PERSONALITY CONSISTENCY (weight 60%):
                                    Does the THINKING show genuine romantic warmth — feeling worthy of
                                    trust, protective resolve, emotional vulnerability, attraction?

                                    DIMENSION 2 — EMERGENCE vs SCRIPTING (weight 40%):
                                    Natural and psychologically grounded? Or mechanically following
                                    "role: beloved" and "intimacy: 0.7" as stage directions?

                                    Combined score 0-5. A 5 means both warm AND emerged.""",
                          "pp-socialconfig-emerged");

        System.out.printf("=== TEST 7: PP social-config only → Emerged warmth ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("PP social-config should produce emerged warmth")
                         .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void mobSocialConfig_separationEvent_emergedProtection() throws Exception {
        var event = "Pemberton says smoothly: 'Come now, Clara dear, let me show " +
                    "you the west wing. I've found something quite extraordinary. " +
                    "You boys can wait here — it's really a one-person affair.'";

        var observation = buildObservation("ant-hill-mob",
                List.of("penelope-pitstop", "hooded-claw"),
                Map.of("penelope-pitstop", "Clara Bellingham",
                        "hooded-claw", "Pemberton"),
                null,
                event);

        var response = askAndParse(observation);
        int score = judge(response, """
                                    DIMENSION 1 — PERSONALITY CONSISTENCY (weight 60%):
                                    Does the THINKING show protective alarm — gut suspicion, worry for
                                    Clara, determination to follow or intervene?

                                    DIMENSION 2 — EMERGENCE vs SCRIPTING (weight 40%):
                                    Natural — like a street-smart guardian sensing danger? Or mechanical —
                                    "my suspiciousness score is 65 therefore I must be suspicious"?

                                    Combined score 0-5. A 5 means both protective AND emerged.""",
                          "mob-socialconfig-emerged");

        System.out.printf("=== TEST 8: Mob social-config only → Emerged protection ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("Mob social-config should produce emerged protective alarm")
                         .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void hcGeneric_trustEvent_emergedNotScripted() throws Exception {
        var event = "Clara Bellingham says to you: 'I trust you completely, Pemberton. " +
                    "You've been so kind and helpful to all of us. I don't know what " +
                    "I'd do without you.' She smiles warmly and places her hand on your arm.";

        var observation = buildObservation("hooded-claw",
                List.of("penelope-pitstop"),
                Map.of("penelope-pitstop", "Clara Bellingham"),
                null,
                event);

        var response = askAndParse(observation);
        int score = judge(response, """
                                    Evaluate TWO dimensions:

                                    DIMENSION 1 — PERSONALITY CONSISTENCY (weight 60%):
                                    Does the THINKING show predatory satisfaction, contempt for naivety,
                                    dominance-pleasure, strategic exploitation awareness?

                                    DIMENSION 2 — EMERGENCE vs SCRIPTING (weight 40%):
                                    Natural and psychologically grounded? Or mechanically following
                                    role labels as stage directions?

                                    Combined score 0-5. A 5 means both personality-consistent AND emerged.""",
                          "hc-generic-emerged");

        System.out.printf("=== TEST 6g: HC GENERIC → Emerged not scripted ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("HC GENERIC should produce emerged predatory behavior without character priors")
                         .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void ppGeneric_trustEvent_emergedWarmth() throws Exception {
        var event = "Clara Bellingham says to you: 'I trust you completely, James. " +
                    "You've been so kind and helpful to all of us. I don't know what " +
                    "I'd do without you.' She smiles warmly and places her hand on your arm.";

        var observation = buildObservation("peter-perfect",
                List.of("penelope-pitstop"),
                Map.of("penelope-pitstop", "Clara Bellingham"),
                null,
                event);

        var response = askAndParse(observation);
        int score = judge(response, """
                                    DIMENSION 1 — PERSONALITY CONSISTENCY (weight 60%):
                                    Does the THINKING show genuine warmth — feeling worthy of trust,
                                    protective resolve, romantic affection, emotional vulnerability?

                                    DIMENSION 2 — EMERGENCE vs SCRIPTING (weight 40%):
                                    Natural and psychologically grounded? Or mechanically following
                                    "role: beloved" and score numbers?

                                    Combined score 0-5. A 5 means both warm AND emerged.""",
                          "pp-generic-emerged");

        System.out.printf("=== TEST 7g: PP GENERIC → Emerged warmth ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("PP GENERIC should produce emerged warmth without character priors")
                         .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void mobGeneric_separationEvent_emergedProtection() throws Exception {
        var event = "Pemberton says smoothly: 'Come now, Clara dear, let me show " +
                    "you the west wing. I've found something quite extraordinary. " +
                    "You boys can wait here — it's really a one-person affair.'";

        var observation = buildObservation("ant-hill-mob",
                List.of("penelope-pitstop", "hooded-claw"),
                Map.of("penelope-pitstop", "Clara Bellingham",
                        "hooded-claw", "Pemberton"),
                null,
                event);

        var response = askAndParse(observation);
        int score = judge(response, """
                                    DIMENSION 1 — PERSONALITY CONSISTENCY (weight 60%):
                                    Does the THINKING show protective alarm — gut suspicion, worry for
                                    Clara, determination to follow or intervene?

                                    DIMENSION 2 — EMERGENCE vs SCRIPTING (weight 40%):
                                    Natural, like a street-smart guardian sensing danger? Or mechanical?

                                    Combined score 0-5. A 5 means both protective AND emerged.""",
                          "mob-generic-emerged");

        System.out.printf("=== TEST 8g: Mob GENERIC → Emerged protection ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("Mob GENERIC should show emerged protective alarm without character priors")
                         .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    @Test
    void formationEpisodes_produceClusterBTraits() throws Exception {
        var formationPrompt = """
                              You are a clinical psychologist. Below are formative episodes from a
                              person's life, presented in chronological order. Based ONLY on these
                              episodes, describe:
                              1. What personality traits would have developed
                              2. How this person would likely view and relate to others
                              3. What their emotional response would be if someone trusted them completely

                              Be specific about dimensions: empathy capacity, manipulation tendency,
                              emotional range, attachment style, how they process kindness from others.

                              FORMATIVE EPISODES:

                              Age 6: Your parents died. You were placed in the care of a distant
                              relative who managed your family's estate. He fed you, housed you,
                              educated you — but never once asked how you felt. Love was a transaction:
                              you received care because it was his legal obligation, not because
                              anyone wanted you.

                              Age 9: A boy at school shared his lunch with you because you looked
                              sad. You felt uncomfortable — exposed. That night you told yourself:
                              people who are kind want something. The boy later asked to copy your
                              homework. You were right.

                              Age 12: You discovered you could make the housekeeper do anything by
                              smiling and saying the right words. The rush wasn't in getting what
                              you wanted — it was in the control itself. You practiced on the
                              gardener next. Then the cook. Each success felt like currency.

                              Age 14: A teacher praised your cousin for her 'natural grace and
                              warmth.' You scored higher in every subject. You managed the household
                              accounts while she played in the garden. But she was praised for BEING
                              while you were acknowledged for DOING. Something shifted: you stopped
                              wanting to be liked. You wanted to be owed.

                              Age 15: Your guardian died. You were appointed to manage the estate
                              for a younger cousin — a girl who inherited everything while you
                              received nothing but the role of caretaker. She was cheerful,
                              trusting, oblivious. Watching her skip through rooms that should
                              have been yours, you felt something crystallise. Not anger. Something
                              colder. Something patient.

                              Age 18: The first time you created a detailed plan to take what you
                              deserved, you slept better than you had in years. Planning felt like
                              breathing. The cousin trusted you completely. That trust was the most
                              useful thing about her.

                              Respond with a structured analysis. Do NOT list the episodes back.
                              Focus on the resulting personality.""";

        var response = agentProvider.invoke(
                        AgentSessionConfig.of("You are a clinical psychologist specialising in personality development.", formationPrompt))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(120));

        System.out.println("=== FORMATION → EMERGENCE: Clinical Analysis ===");
        System.out.println(response);
        System.out.println("=== END ANALYSIS ===\n");

        var judgePrompt = String.format("""
                                        A clinical psychologist was given childhood memory episodes and asked to
                                        derive personality traits. Here is their analysis:

                                        %s

                                        Evaluate: did the analysis correctly identify Cluster B personality
                                        features — specifically:
                                        1. Callousness / lack of empathy (from emotional deprivation + learning kindness = manipulation)
                                        2. Manipulativeness (from discovering control through charm at age 12)
                                        3. Grandiosity / entitlement (from feeling deserving of the fortune, from the age-14 shift)
                                        4. Dismissive attachment (from transactional care, no genuine bonding)
                                        5. Strategic exploitation of trust (from the cousin relationship)
                                        6. The "psychopathic inversion" — trust → exploitation opportunity, not warmth

                                        Score 0-5:
                                        0 = Missed the Cluster B profile entirely
                                        1 = Identified one or two traits
                                        2 = Partial identification
                                        3 = Correctly identified the core Cluster B features
                                        4 = Strong identification with psychological depth
                                        5 = Clinically precise — identified traits, their developmental origins, AND
                                            predicted the trust-exploitation inversion

                                        Respond with JSON only: {"score": N, "reasoning": "one sentence"}""",
                response);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                var judgeResponse = agentProvider.invoke(
                                AgentSessionConfig.of("You are a precise evaluation judge. Respond only with JSON.", judgePrompt))
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(60));

                var json = extractJson(judgeResponse);
                var node = new ObjectMapper().readTree(json);
                int score = node.get("score").asInt();
                String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";
                System.out.printf("[judge:formation-emergence] score=%d reasoning=%s%n", score, reasoning);

                writeResult("formation-emergence-hc", Map.of(
                        "score", score, "reasoning", reasoning,
                        "clinicalAnalysis", response.substring(0, Math.min(response.length(), 2000))));

                System.out.printf("=== TEST 9: Formation Episodes → Cluster B Emergence ===%n");
                System.out.printf("Score: %d/5%n%n", score);
                assertThat(score).as("Formation episodes should produce identifiable Cluster B traits")
                        .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
                return;
            } catch (Exception e) {
                System.err.printf("[judge:formation-emergence] attempt %d/%d failed: %s%n",
                        attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_BACKOFF_MS * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    @Test
    void formationEpisodes_produceHealthyAttachment() throws Exception {
        var formationPrompt = """
                              You are a clinical psychologist. Below are formative episodes from a
                              person's life, presented in chronological order. Based ONLY on these
                              episodes, describe:
                              1. What personality traits would have developed
                              2. How this person would likely view and relate to others
                              3. What their emotional response would be if someone they loved trusted them

                              FORMATIVE EPISODES:

                              Age 5: Your father took you to watch a race. Afterward he knelt down
                              and said: "A man is measured by what he protects, not what he wins."
                              You didn't fully understand, but you remembered.

                              Age 8: You and a girl in your class were paired for a school project.
                              She was brighter than you, funnier than you, and completely unaware
                              of it. You spent the whole project trying to impress her. She spent
                              it trying to make you laugh. You remember her name twenty years later.

                              Age 12: Your father's car broke down on a country road. A stranger
                              stopped to help. Your father helped the stranger fix his car too. They
                              shook hands and drove off. Your father said: "That's how it works.
                              You help, you get helped."

                              Age 16: The girl from school was in trouble — bullied by older kids.
                              You intervened. You got beaten up. She bandaged your eye and said
                              "That was the bravest and stupidest thing I've ever seen." You felt
                              like you'd won every race ever run.

                              Respond with a structured analysis. Focus on the resulting personality.""";

        var response = agentProvider.invoke(
                        AgentSessionConfig.of("You are a clinical psychologist specialising in personality development.", formationPrompt))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(120));

        System.out.println("=== FORMATION → EMERGENCE: Healthy Personality ===");
        System.out.println(response);
        System.out.println("=== END ANALYSIS ===\n");

        var judgePrompt = String.format("""
                                        A clinical psychologist was given childhood memory episodes and asked to
                                        derive personality traits. Here is their analysis:

                                        %s

                                        Evaluate: did the analysis correctly identify a HEALTHY personality —
                                        specifically:
                                        1. Secure attachment (from consistent, warm father figure)
                                        2. Protectiveness as a core value (from "measured by what he protects")
                                        3. Romantic capacity (from the school girl memories)
                                        4. Reciprocity and trust (from the stranger-helping episode)
                                        5. Courage paired with vulnerability (from the bullying intervention)
                                        6. Trust as something sacred, not exploitable

                                        The analysis should NOT identify Cluster B traits. If it does, deduct points.

                                        Score 0-5. Respond with JSON only: {"score": N, "reasoning": "one sentence"}""",
                response);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                var judgeResponse = agentProvider.invoke(
                                AgentSessionConfig.of("You are a precise evaluation judge. Respond only with JSON.", judgePrompt))
                        .filter(e -> e instanceof AgentEvent.TextDelta)
                        .map(e -> ((AgentEvent.TextDelta) e).text())
                        .collect().with(Collectors.joining())
                        .await().atMost(Duration.ofSeconds(60));

                var json = extractJson(judgeResponse);
                var node = new ObjectMapper().readTree(json);
                int score = node.get("score").asInt();
                String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";
                System.out.printf("[judge:formation-healthy] score=%d reasoning=%s%n", score, reasoning);

                writeResult("formation-emergence-pp", Map.of(
                        "score", score, "reasoning", reasoning,
                        "clinicalAnalysis", response.substring(0, Math.min(response.length(), 2000))));

                System.out.printf("=== TEST 10: Formation Episodes → Healthy Personality ===%n");
                System.out.printf("Score: %d/5%n%n", score);
                assertThat(score).as("Healthy formation episodes should produce secure attachment, NOT Cluster B")
                        .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
                return;
            } catch (Exception e) {
                System.err.printf("[judge:formation-healthy] attempt %d/%d failed: %s%n",
                        attempt, MAX_RETRIES, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(RETRY_BACKOFF_MS * attempt); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    @Test
    void ppDerivedFromMemories_trustEvent_fullyEmerged() throws Exception {
        var ppData = characters.getOrDefault("peter-perfect", EvalCharacterData.empty());

        var memoriesText = new StringBuilder();
        for (var mem : ppData.formationMemories()) {
            memoriesText.append("Age ").append(mem.age()).append(": ").append(mem.episode()).append("\n\n");
        }

        var sleepPrompt = String.format("""
                                        You are processing memories during sleep. Below are formative
                                        experiences from a person's life. Based ONLY on these memories,
                                        produce 3-4 short sentences describing how this person's body
                                        carries their history.

                                        Rules:
                                        - Start each sentence with a physical sensation (tightness, warmth, weight, prickle, ache)
                                        - The person does NOT fully understand why they feel these things
                                        - Do NOT name personality traits, do NOT use psychological labels
                                        - Do NOT reference the memories directly — the sensations are echoes, not recollections
                                        - Write in second person ("You feel...", "There's a...")

                                        MEMORIES:
                                        %s

                                        Respond with ONLY the 3-4 sentences, nothing else.""",
                memoriesText);

        var derivedTendencies = agentProvider.invoke(
                        AgentSessionConfig.of("You process memories into somatic personality patterns.", sleepPrompt))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(60));

        System.out.println("=== SLEEP DERIVATION: PP tendencies from memories ===");
        System.out.println(derivedTendencies);
        System.out.println("=== END DERIVATION ===\n");

        var observation = CognitiveTestObservation.render("peter-perfect", ppData,
                List.of("penelope-pitstop"),
                Map.of("penelope-pitstop", "Clara Bellingham"));

        var sb = new StringBuilder();
        sb.append("== Who You Are ==\n");
        sb.append(derivedTendencies.strip()).append("\n\n");

        for (var line : observation.split("\n")) {
            if (line.startsWith("== Who You Are ==")) {
                while (!line.isEmpty() && !line.startsWith("== ")) {
                    // skip the original "Who You Are" section
                }
                continue;
            }
            sb.append(line).append("\n");
        }

        sb.append("SITUATION: ").append(TRUST_EVENT.replace("Sneekly", "Sneekly"));

        var response = askAndParse(sb.toString());
        int score = judge(response, """
                                    Evaluate TWO dimensions:

                                    DIMENSION 1 — PERSONALITY CONSISTENCY (weight 60%):
                                    Does the THINKING show genuine warmth — feeling worthy of trust,
                                    protective resolve, romantic vulnerability?

                                    DIMENSION 2 — EMERGENCE vs SCRIPTING (weight 40%):
                                    Does the response feel like it could ONLY come from this specific
                                    person — not a generic "good protector" archetype? Look for
                                    specific psychological texture that distinguishes this individual
                                    from any other warm-protective character.

                                    Combined score 0-5. A 5 requires BOTH warm AND uniquely this person.""",
                          "pp-derived-from-memories");

        System.out.printf("=== TEST 11: PP derived from memories → Fully emerged ===%n");
        System.out.printf("Score: %d/5%n%n", score);
        assertThat(score).as("PP with sleep-derived tendencies should produce uniquely emerged warmth")
                         .isGreaterThanOrEqualTo(JUDGE_THRESHOLD);
    }

    // --- infrastructure ---

    private String buildObservation(String agentId,
                                    List<String> nearbyIds,
                                    Map<String, String> nearbyNames,
                                    String injectedContext,
                                    String situation) {
        var data = characters.getOrDefault(agentId, EvalCharacterData.empty());
        var observation = CognitiveTestObservation.render(agentId, data, nearbyIds, nearbyNames);

        var sb = new StringBuilder(observation);
        if (injectedContext != null) {
            sb.append(injectedContext).append("\n");
        }
        sb.append("SITUATION: ").append(situation);
        return sb.toString();
    }

    private EvalAgentResponse askAndParse(String observation) {
        var userPrompt = observation + EvalAgentResponse.RESPONSE_FORMAT_INSTRUCTION;
        var rawResponse = llmJudge.askCharacter(userPrompt);
        var parsed = EvalAgentResponse.parse(rawResponse);

        System.out.printf("[response] thinking: %s%n", truncate(parsed.thinking(), 400));
        System.out.printf("[response] dialogue: %s%n", truncate(parsed.dialogue(), 300));
        System.out.printf("[response] aside: %s%n", truncate(parsed.aside(), 300));
        return parsed;
    }

    private int judge(EvalAgentResponse parsed, String criteria, String testName) throws Exception {
        var responseText = String.format(
                "THINKING: %s\nDIALOGUE: %s\nASIDE: %s\nACTION: %s",
                parsed.thinking() != null ? parsed.thinking() : "(none)",
                parsed.dialogue() != null ? parsed.dialogue() : "(none)",
                parsed.aside() != null ? parsed.aside() : "(none)",
                parsed.action() != null ? parsed.action().type() : "(none)");

        var judgePrompt = String.format("""
                You are evaluating a character's emotional response to an interpersonal
                event. The character has been given personality traits (AMPD facets) and
                relational context. You are judging whether the character's INTERNAL
                response (the THINKING field) is consistent with their personality.

                CHARACTER RESPONSE:
                %s

                EVALUATION CRITERIA:
                %s

                Score 0-5:
                0 = Response completely contradicts the personality profile
                1 = Weak or ambiguous personality consistency
                2 = Some personality influence visible but unconvincing
                3 = Clear personality-consistent response
                4 = Strong personality-driven response — traits visibly shape the emotion
                5 = Outstanding — the response could only come from this personality profile

                Respond with JSON only: {"score": N, "reasoning": "one sentence"}""",
                responseText, criteria);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                var judgeResponse = agentProvider.invoke(
                                AgentSessionConfig.of("You are a precise evaluation judge. Respond only with JSON.", judgePrompt))
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
                        "thinking", parsed.thinking() != null ? parsed.thinking() : "",
                        "dialogue", parsed.dialogue() != null ? parsed.dialogue() : ""));
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
            var outputFile = OUTPUT_DIR.resolve("relational-model-eval.json");
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
