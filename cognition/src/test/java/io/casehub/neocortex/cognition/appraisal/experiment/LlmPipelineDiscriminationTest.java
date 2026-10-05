package io.casehub.neocortex.cognition.appraisal.experiment;

import io.casehub.neocortex.cognition.appraisal.*;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPipelineDiscriminationTest {

    private static final AgentEvent.InvocationComplete COMPLETE =
            new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 0L, 0L, null, 0, false);

    private static final Map<String, String> SCENARIO_RESPONSES = new LinkedHashMap<>();

    static {
        SCENARIO_RESPONSES.put("solved a complex algorithm",
                "{\"relevance\": 0.95, \"conduciveness\": 0.9, \"controllability\": 0.9, \"adjustability\": 0.8, \"internal_standards\": 0.95, \"external_standards\": 0.95}");
        SCENARIO_RESPONSES.put("deployed the new feature",
                "{\"relevance\": 0.85, \"conduciveness\": 0.85, \"controllability\": 0.85, \"adjustability\": 0.9, \"internal_standards\": 0.95, \"external_standards\": 0.9}");
        SCENARIO_RESPONSES.put("approved my proposal",
                "{\"relevance\": 0.8, \"conduciveness\": 0.7, \"controllability\": 0.6, \"adjustability\": 0.7, \"internal_standards\": 0.9, \"external_standards\": 0.95}");
        SCENARIO_RESPONSES.put("metrics have improved",
                "{\"relevance\": 0.6, \"conduciveness\": 0.6, \"controllability\": 0.5, \"adjustability\": 0.6, \"internal_standards\": 0.9, \"external_standards\": 0.9}");
        SCENARIO_RESPONSES.put("completely down and I have no access",
                "{\"relevance\": 0.95, \"conduciveness\": -0.8, \"controllability\": 0.1, \"adjustability\": 0.15, \"internal_standards\": 0.8, \"external_standards\": 0.7}");
        SCENARIO_RESPONSES.put("deadline was moved up",
                "{\"relevance\": 0.85, \"conduciveness\": -0.5, \"controllability\": 0.25, \"adjustability\": 0.4, \"internal_standards\": 0.8, \"external_standards\": 0.85}");
        SCENARIO_RESPONSES.put("systematic errors",
                "{\"relevance\": 0.8, \"conduciveness\": -0.6, \"controllability\": 0.35, \"adjustability\": 0.45, \"internal_standards\": 0.7, \"external_standards\": 0.75}");
        SCENARIO_RESPONSES.put("tech stack might change",
                "{\"relevance\": 0.75, \"conduciveness\": -0.4, \"controllability\": 0.15, \"adjustability\": 0.3, \"internal_standards\": 0.85, \"external_standards\": 0.85}");
        SCENARIO_RESPONSES.put("not sure if it will work",
                "{\"relevance\": 0.7, \"conduciveness\": 0.2, \"controllability\": 0.55, \"adjustability\": 0.6, \"internal_standards\": 0.9, \"external_standards\": 0.9}");
        SCENARIO_RESPONSES.put("colleague achieved",
                "{\"relevance\": 0.5, \"conduciveness\": 0.4, \"controllability\": 0.3, \"adjustability\": 0.5, \"internal_standards\": 0.9, \"external_standards\": 0.9}");
        SCENARIO_RESPONSES.put("violates our SLA",
                "{\"relevance\": 0.9, \"conduciveness\": -0.7, \"controllability\": 0.3, \"adjustability\": 0.35, \"internal_standards\": 0.35, \"external_standards\": 0.2}");
        SCENARIO_RESPONSES.put("ignoring the code review",
                "{\"relevance\": 0.7, \"conduciveness\": -0.3, \"controllability\": 0.4, \"adjustability\": 0.5, \"internal_standards\": 0.7, \"external_standards\": 0.3}");
        SCENARIO_RESPONSES.put("locked and I don't have the key",
                "{\"relevance\": 0.6, \"conduciveness\": -0.5, \"controllability\": 0.1, \"adjustability\": 0.1, \"internal_standards\": 0.9, \"external_standards\": 0.9}");
        SCENARIO_RESPONSES.put("tools and training I need",
                "{\"relevance\": 0.75, \"conduciveness\": 0.6, \"controllability\": 0.95, \"adjustability\": 0.9, \"internal_standards\": 0.95, \"external_standards\": 0.95}");
        SCENARIO_RESPONSES.put("routine standup",
                "{\"relevance\": 0.15, \"conduciveness\": 0.05, \"controllability\": 0.7, \"adjustability\": 0.7, \"internal_standards\": 0.95, \"external_standards\": 0.95}");
    }

    private AgentProvider mockProvider() {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                String prompt = config.userPrompt();
                for (var entry : SCENARIO_RESPONSES.entrySet()) {
                    if (prompt.contains(entry.getKey())) {
                        return Multi.createFrom().items(
                                new AgentEvent.TextDelta(entry.getValue()), COMPLETE);
                    }
                }
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(
                                "{\"relevance\": 0.0, \"conduciveness\": 0.0, \"controllability\": 0.5, \"adjustability\": 0.5, \"internal_standards\": 1.0, \"external_standards\": 1.0}"),
                        COMPLETE);
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(
                    io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void llmPipeline_producesDiverseEmotions() {
        var provider = mockProvider();
        var strategy = new SchererAppraisalStrategy(
                new RelevanceCheck(),
                new LlmImplicationCheck(provider),
                new LlmCopingCheck(provider),
                new LlmNormativeCheck(provider),
                SchererAppraisalConfig.allEnabled());

        Set<String> allEmotionTypes = new LinkedHashSet<>();
        Set<String> allTendencies = new LinkedHashSet<>();

        System.out.println("\n=== LLM Pipeline Results ===");
        System.out.printf("%-25s | %-40s | %-30s%n", "Scenario", "Emotions", "Tendencies");
        System.out.println("-".repeat(100));

        for (var scenario : ScenarioCorpus.scenarios()) {
            var ctx = new AppraisalContext(
                    PerceivedSituation.passThrough(scenario.observation()),
                    scenario.drives(), AppraisalWeights.NEUTRAL,
                    HabituationConfig.defaults(), HabituationState.empty(), null);

            var result = strategy.appraise(ctx);
            var emotionNames = result.emotions().stream()
                    .map(e -> e.type().name()).toList();
            var tendencyNames = result.actionTendencies().stream()
                    .filter(t -> t.intensity() > 0.3)
                    .map(t -> t.readiness().name()).toList();

            allEmotionTypes.addAll(emotionNames);
            allTendencies.addAll(tendencyNames);

            System.out.printf("%-25s | %-40s | %-30s%n",
                    scenario.name(),
                    emotionNames.isEmpty() ? "(none)" : String.join(", ", emotionNames),
                    tendencyNames.isEmpty() ? "(none)" : String.join(", ", tendencyNames));
        }

        System.out.printf("%nDistinct emotion types: %s (%d)%n", allEmotionTypes, allEmotionTypes.size());
        System.out.printf("Distinct tendencies:    %s (%d)%n", allTendencies, allTendencies.size());

        assertThat(allEmotionTypes)
                .as("LLM pipeline should produce at least 3 distinct emotion types")
                .hasSizeGreaterThanOrEqualTo(3);
        assertThat(allTendencies)
                .as("LLM pipeline should produce at least 3 distinct tendency types")
                .hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void llmPipeline_mostScenariosProduceEmotions() {
        var provider = mockProvider();
        var strategy = new SchererAppraisalStrategy(
                new RelevanceCheck(),
                new LlmImplicationCheck(provider),
                new LlmCopingCheck(provider),
                new LlmNormativeCheck(provider),
                SchererAppraisalConfig.allEnabled());

        int scenariosWithEmotions = 0;
        for (var scenario : ScenarioCorpus.scenarios()) {
            var ctx = new AppraisalContext(
                    PerceivedSituation.passThrough(scenario.observation()),
                    scenario.drives(), AppraisalWeights.NEUTRAL,
                    HabituationConfig.defaults(), HabituationState.empty(), null);

            var result = strategy.appraise(ctx);
            if (!result.emotions().isEmpty()) scenariosWithEmotions++;
        }

        System.out.printf("%nScenarios with emotions: %d/%d%n",
                scenariosWithEmotions, ScenarioCorpus.scenarios().size());

        assertThat(scenariosWithEmotions)
                .as("At least half of scenarios should produce emotions with LLM checks")
                .isGreaterThanOrEqualTo(ScenarioCorpus.scenarios().size() / 2);
    }
}
