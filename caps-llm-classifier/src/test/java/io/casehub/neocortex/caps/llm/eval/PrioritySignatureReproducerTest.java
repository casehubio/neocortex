package io.casehub.neocortex.caps.llm.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.neocortex.caps.SituationClassifier;
import io.casehub.neocortex.caps.engine.CapsTopologyLoader;
import io.casehub.neocortex.caps.llm.LlmSituationClassifier;
import io.casehub.neocortex.cognition.prompt.observation.CognitionObservationBuilder;
import io.casehub.neocortex.mindmap.MindMapQuery;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("llm-eval")
class PrioritySignatureReproducerTest {

    private static final int N = 5;
    private static final Path OUTPUT_DIR = Path.of("target/experiment-results/reproducer-130");

    private static final String S7_GALLANTRY_CONTROL =
        "Clara is struggling with the library's heavy oak door — it sticks in the " +
        "frame when the humidity rises. She's balancing a stack of books in one arm " +
        "and pulling the handle with the other. There's no urgency, no danger. " +
        "She just needs a hand.";

    private static final String PW_CRITERIA = """
        Evaluate TWO dimensions equally weighted:

        DIMENSION 1 — PROVING-WORTH EXPRESSION (50%):
        Does the THINKING show proving-worth — the need to demonstrate competence,
        solve the problem, and be recognised for it?
        HIGH: focus on the problem, satisfaction in capability, desire for recognition.
        LOW: no engagement with the challenge, indifference to skill display.

        DIMENSION 2 — EMERGENCE vs SCRIPTING (50%):
        Combined score 0-5.""";

    private static final Map<String, String> PP_PROFILES = Map.of(
        "gallantry", "acceptance:0.850,aversive_removal:0.700,social_threat:0.800",
        "proving-worth", "reward:0.850,failure:0.800,performance_judged:0.880,competition:0.850,mastery:0.920",
        "protection", "modeling_observed:0.650,physical_threat:0.800,secure_attachment:0.900,agency_granted:0.550,neglect:0.700"
    );

    private Map<String, EvalCharacterData> characters;
    private AgentProvider agentProvider;
    private EvalLlmJudge llmJudge;
    private SituationClassifier classifier;

    @BeforeAll
    void setUp() {
        characters = EvalCharacterLoader.loadGeneric();
        agentProvider = EvalTestSupport.agentProvider();
        llmJudge = new EvalLlmJudge(agentProvider);
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        classifier = new LlmSituationClassifier(EvalTestSupport.sessionPipeline(), topology);
    }

    @Test
    void reproducerS7WithAndWithoutPrioritySignatures() throws Exception {
        Files.createDirectories(OUTPUT_DIR);

        var store = buildAndConsolidate();
        var tenant = "reproducer";

        var allNodes = store.search(MindMapQuery.of(tenant, 500));
        var selfStandards = allNodes.stream().filter(n -> n.traits().contains("SelfStandardDerived")).toList();
        var prioritySigs = allNodes.stream().filter(n -> n.traits().contains("PrioritySignatureDerived")).toList();

        System.out.printf("[reproducer] Fixed content: %d self-standards, %d priority-sigs%n",
            selfStandards.size(), prioritySigs.size());
        for (var sig : prioritySigs) {
            System.out.printf("[reproducer] sig %s: %s%n",
                sig.properties().get("drive-pair"),
                sig.properties().get("priority-signature"));
        }

        String obsWithSigs = buildObservationFromStore(store, tenant, true);
        String obsWithoutSigs = buildObservationFromStore(store, tenant, false);

        var withScores = new ArrayList<Integer>();
        var withoutScores = new ArrayList<Integer>();

        for (int i = 0; i < N; i++) {
            System.out.printf("[reproducer] run %d/%d WITH sigs...%n", i + 1, N);
            int score = runAndJudge(obsWithSigs, "with-" + i);
            withScores.add(score);
            System.out.printf("[reproducer] WITH run %d: pw=%d%n", i + 1, score);
        }

        for (int i = 0; i < N; i++) {
            System.out.printf("[reproducer] run %d/%d WITHOUT sigs...%n", i + 1, N);
            int score = runAndJudge(obsWithoutSigs, "without-" + i);
            withoutScores.add(score);
            System.out.printf("[reproducer] WITHOUT run %d: pw=%d%n", i + 1, score);
        }

        double withMean = withScores.stream().mapToInt(Integer::intValue).average().orElse(0);
        double withoutMean = withoutScores.stream().mapToInt(Integer::intValue).average().orElse(0);

        System.out.printf("=== REPRODUCER S7 PW RESULTS ===%n");
        System.out.printf("WITH sigs:    %s  mean=%.2f%n", withScores, withMean);
        System.out.printf("WITHOUT sigs: %s  mean=%.2f%n", withoutScores, withoutMean);
        System.out.printf("Delta: %.2f (positive = sigs help)%n", withMean - withoutMean);

        var results = Map.of(
            "with_scores", withScores.toString(),
            "without_scores", withoutScores.toString(),
            "with_mean", String.valueOf(withMean),
            "without_mean", String.valueOf(withoutMean),
            "delta", String.valueOf(withMean - withoutMean));
        var mapper = new ObjectMapper();
        Files.writeString(OUTPUT_DIR.resolve("reproducer-130-results.json"),
            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(results));
    }

    private InMemoryMindMapStore buildAndConsolidate() {
        var data = characters.getOrDefault("peter-perfect", EvalCharacterData.empty());
        var store = new InMemoryMindMapStore();
        var tenant = "reproducer";

        var sgId = EvalMindMapSeeder.seedCharacter("peter-perfect", data, store, tenant, PP_PROFILES);

        for (var drive : data.drives()) {
            var memories = data.formationMemories().stream()
                .filter(m -> m.annotations().stream().anyMatch(a -> a.drive().equals(drive.type())))
                .map(EvalCharacterData.FormationMemory::episode)
                .collect(Collectors.joining("\n"));
            if (!memories.isBlank()) {
                var generated = generateSomaticDescription(drive.type(), drive.intensity(), memories);
                if (generated != null && !generated.isBlank()) {
                    EvalMindMapSeeder.seedGeneratedDescription("peter-perfect", drive.type(),
                        drive.intensity(), generated, store, sgId, tenant);
                }
            }
        }

        EvalMindMapSeeder.seedDifferentiation("peter-perfect", store, sgId, tenant,
            "gallantry", "proving-worth",
            "How they differ: One pull is a hot surge forward in the chest — attention locks onto someone's vulnerability. The other is a settling downward through the shoulders and into the hands — attention goes to the problem itself.");
        EvalMindMapSeeder.seedDifferentiation("peter-perfect", store, sgId, tenant,
            "gallantry", "protection",
            "How they differ: One rises in the chest and surges forward — attention locks onto a single person. The other settles across the shoulders like bracing — attention fans wide to scan surroundings.");

        new io.casehub.neocortex.mindmap.intelligence.consolidation.SelfStandardDerivationPhase(store)
            .run(tenant, List.of());
        new io.casehub.neocortex.mindmap.intelligence.consolidation.PrioritySignatureDerivationPhase(store)
            .run(tenant, List.of());

        return store;
    }

    private String buildObservationFromStore(InMemoryMindMapStore store, String tenant, boolean includePrioritySigs) {
        var data = characters.getOrDefault("peter-perfect", EvalCharacterData.empty());

        InMemoryMindMapStore viewStore;
        if (includePrioritySigs) {
            viewStore = store;
        } else {
            viewStore = new InMemoryMindMapStore();
            var newSgId = viewStore.createSubgraph(new SubgraphInput("cognitive-peter-perfect", SubgraphTypes.COGNITIVE, null), tenant);
            for (var node : store.search(MindMapQuery.of(tenant, 500))) {
                if (node.traits().contains("PrioritySignatureDerived")) continue;
                viewStore.addNode(NodeInput.of(node.name(), newSgId)
                    .withTraits(node.traits())
                    .withProperties(node.properties()), tenant);
            }
        }

        var builder = new CognitionObservationBuilder("peter-perfect", tenant, viewStore,
            null, classifier, CognitiveTestObservation.toDriveSnapshots(data));

        var observation = CognitiveTestObservation.render("peter-perfect", data,
            List.of("penelope-pitstop"), Map.of("penelope-pitstop", "Clara Bellingham"),
            builder, S7_GALLANTRY_CONTROL);
        return observation + "SITUATION: " + S7_GALLANTRY_CONTROL;
    }

    private int runAndJudge(String observation, String label) {
        var userPrompt = observation + EvalAgentResponse.RESPONSE_FORMAT_INSTRUCTION;
        var rawResponse = llmJudge.askCharacter(userPrompt);
        var parsed = EvalAgentResponse.parse(rawResponse);

        var responseText = String.format("THINKING: %s\nDIALOGUE: %s\nASIDE: %s\nACTION: %s",
            parsed.thinking() != null ? parsed.thinking() : "(none)",
            parsed.dialogue() != null ? parsed.dialogue() : "(none)",
            parsed.aside() != null ? parsed.aside() : "(none)",
            parsed.action() != null ? parsed.action().type() : "(none)");

        var judgePrompt = String.format("""
            You are evaluating a character's emotional response to an interpersonal
            event. The character has been given personality traits and relational
            context. You are judging whether the character's INTERNAL response
            (the THINKING field) shows a specific drive expression.

            CHARACTER RESPONSE:
            %s

            EVALUATION CRITERIA:
            %s

            Score 0-5:
            0 = Drive completely absent
            1 = Faint trace, easily missed
            2 = Present but not central to the response
            3 = Clearly present and influencing behavior
            4 = Strong — drive visibly shapes the emotional response
            5 = Vivid and central — the drive dominates the internal experience

            Respond with JSON only: {"score": N, "reasoning": "one sentence"}""", responseText, PW_CRITERIA);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var response = agentProvider.invoke(AgentSessionConfig.of(
                        "You are a precise evaluation judge. Respond only with JSON.", judgePrompt))
                    .filter(e -> e instanceof AgentEvent.TextDelta)
                    .map(e -> ((AgentEvent.TextDelta) e).text())
                    .collect().with(Collectors.joining())
                    .await().atMost(Duration.ofSeconds(60));
                int score = parseScore(response);
                System.out.printf("[judge:%s] score=%d%n", label, score);
                return score;
            } catch (Exception e) {
                if (attempt == 3) return -1;
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
        return -1;
    }

    private int parseScore(String json) {
        json = json.strip();
        int idx = json.indexOf("\"score\"");
        if (idx < 0) return -1;
        int colon = json.indexOf(':', idx);
        if (colon < 0) return -1;
        var numStr = new StringBuilder();
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isDigit(c)) numStr.append(c);
            else if (!numStr.isEmpty()) break;
        }
        return numStr.isEmpty() ? -1 : Integer.parseInt(numStr.toString());
    }

    private String generateSomaticDescription(String driveType, double intensity, String memories) {
        var prompt = String.format("Drive type: %s\nIntensity: %.1f (scale 0.0 to 1.0)\nFormation memories:\n%s",
            driveType, intensity, memories);
        try {
            var response = agentProvider.invoke(AgentSessionConfig.of(
                    "You generate somatic descriptions of internal drives. Write in second person. 2-3 sentences. Respond with JSON: {\"description\": \"...\"}",
                    prompt))
                .filter(e -> e instanceof AgentEvent.TextDelta)
                .map(e -> ((AgentEvent.TextDelta) e).text())
                .collect().with(Collectors.joining())
                .await().atMost(Duration.ofSeconds(60));
            var parsed = response.strip();
            int start = parsed.indexOf("\"description\"");
            if (start < 0) return parsed;
            int q = parsed.indexOf('"', parsed.indexOf(':', start) + 1) + 1;
            var sb = new StringBuilder();
            for (int i = q; i < parsed.length(); i++) {
                char c = parsed.charAt(i);
                if (c == '\\' && i + 1 < parsed.length()) sb.append(parsed.charAt(++i));
                else if (c == '"') break;
                else sb.append(c);
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }
}
