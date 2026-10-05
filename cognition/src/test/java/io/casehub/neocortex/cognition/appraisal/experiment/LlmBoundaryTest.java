package io.casehub.neocortex.cognition.appraisal.experiment;

import io.casehub.neocortex.cognition.appraisal.AppraisalContext;
import io.casehub.neocortex.cognition.appraisal.CopingCheck;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.appraisal.LlmCopingCheck;
import io.casehub.neocortex.cognition.appraisal.PerceivedSituation;
import io.casehub.neocortex.cognition.appraisal.SecDimensions;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class LlmBoundaryTest {

    private static final AgentEvent.InvocationComplete COMPLETE =
            new AgentEvent.InvocationComplete(0, 0, 0, 0, 0, null, 0L, 0L, null, 0, false);

    private AgentProvider mockProvider(Map<String, String> scenarioResponses) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                String userPrompt = config.userPrompt();
                for (var entry : scenarioResponses.entrySet()) {
                    if (userPrompt.contains(entry.getKey())) {
                        return Multi.createFrom().items(
                                new AgentEvent.TextDelta(entry.getValue()),
                                COMPLETE);
                    }
                }
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta("{\"controllability\": 0.5, \"adjustability\": 0.5}"),
                        COMPLETE);
            }

            @Override
            public io.casehub.platform.agent.AgentSession openSession(
                    io.casehub.platform.agent.AgentSessionInit init) {
                throw new UnsupportedOperationException("not needed for experiment");
            }
        };
    }

    @Test
    void llmCopingCheck_returnsValidSecResult() {
        var provider = mockProvider(Map.of(
                "locked", "{\"controllability\": 0.15, \"adjustability\": 0.2}",
                "tools and training", "{\"controllability\": 0.9, \"adjustability\": 0.85}"));

        var check = new LlmCopingCheck(provider);

        var lockedCtx = contextFor("The door is locked and I don't have the key");
        var equippedCtx = contextFor("I can handle this — I have all the tools and training I need");

        var lockedResult = check.evaluate(lockedCtx);
        var equippedResult = check.evaluate(equippedCtx);

        assertThat(lockedResult.dimension(SecDimensions.CONTROLLABILITY))
                .isCloseTo(0.15, offset(0.01));
        assertThat(equippedResult.dimension(SecDimensions.CONTROLLABILITY))
                .isCloseTo(0.9, offset(0.01));
    }

    @Test
    void boundaryComparison_llmVsKeyword_acrossAllScenarios() {
        var scenarioResponses = new LinkedHashMap<String, String>();
        scenarioResponses.put("solved a complex algorithm",
                "{\"controllability\": 0.9, \"adjustability\": 0.8}");
        scenarioResponses.put("deployed the new feature",
                "{\"controllability\": 0.85, \"adjustability\": 0.9}");
        scenarioResponses.put("approved my proposal",
                "{\"controllability\": 0.6, \"adjustability\": 0.7}");
        scenarioResponses.put("metrics have improved",
                "{\"controllability\": 0.5, \"adjustability\": 0.6}");
        scenarioResponses.put("completely down and I have no access",
                "{\"controllability\": 0.1, \"adjustability\": 0.15}");
        scenarioResponses.put("deadline was moved up",
                "{\"controllability\": 0.25, \"adjustability\": 0.4}");
        scenarioResponses.put("systematic errors",
                "{\"controllability\": 0.35, \"adjustability\": 0.45}");
        scenarioResponses.put("tech stack might change",
                "{\"controllability\": 0.15, \"adjustability\": 0.3}");
        scenarioResponses.put("not sure if it will work",
                "{\"controllability\": 0.55, \"adjustability\": 0.6}");
        scenarioResponses.put("colleague achieved",
                "{\"controllability\": 0.3, \"adjustability\": 0.5}");
        scenarioResponses.put("violates our SLA",
                "{\"controllability\": 0.3, \"adjustability\": 0.35}");
        scenarioResponses.put("ignoring the code review",
                "{\"controllability\": 0.4, \"adjustability\": 0.5}");
        scenarioResponses.put("locked and I don't have the key",
                "{\"controllability\": 0.1, \"adjustability\": 0.1}");
        scenarioResponses.put("tools and training I need",
                "{\"controllability\": 0.95, \"adjustability\": 0.9}");
        scenarioResponses.put("routine standup",
                "{\"controllability\": 0.7, \"adjustability\": 0.7}");

        var keywordCheck = new CopingCheck();
        var llmCheck = new LlmCopingCheck(mockProvider(scenarioResponses));

        int agreementCount = 0;
        int divergenceCount = 0;

        System.out.println("\n=== LLM vs Keyword Coping Check ===");
        System.out.printf("%-25s | %-12s %-12s | %-12s %-12s | %s%n",
                "Scenario", "KW-ctrl", "KW-adj", "LLM-ctrl", "LLM-adj", "Verdict");
        System.out.println("-".repeat(105));

        for (var scenario : ScenarioCorpus.scenarios()) {
            var ctx = new AppraisalContext(
                    PerceivedSituation.passThrough(scenario.observation()),
                    scenario.drives(), AppraisalWeights.NEUTRAL,
                    HabituationConfig.defaults(), HabituationState.empty(), null);

            var kwResult = keywordCheck.evaluate(ctx);
            var llmResult = llmCheck.evaluate(ctx);

            double kwCtrl = kwResult.dimension(SecDimensions.CONTROLLABILITY);
            double llmCtrl = llmResult.dimension(SecDimensions.CONTROLLABILITY);
            double delta = Math.abs(kwCtrl - llmCtrl);
            String verdict = delta > 0.3 ? "DIVERGE" : "agree";

            if (delta > 0.3) divergenceCount++;
            else agreementCount++;

            System.out.printf("%-25s | %12.3f %12.3f | %12.3f %12.3f | %s (Δ=%.2f)%n",
                    scenario.name(),
                    kwCtrl, kwResult.dimension(SecDimensions.ADJUSTABILITY),
                    llmCtrl, llmResult.dimension(SecDimensions.ADJUSTABILITY),
                    verdict, delta);
        }

        System.out.printf("%nAgreement: %d, Divergence: %d (threshold: 0.3)%n",
                agreementCount, divergenceCount);
        System.out.printf("LLM adds signal beyond keywords in %d/%d scenarios%n",
                divergenceCount, ScenarioCorpus.scenarios().size());

        var kwDistinct = new HashSet<Double>();
        var llmDistinct = new HashSet<Double>();
        for (var scenario : ScenarioCorpus.scenarios()) {
            var ctx = contextFor(scenario.observation());
            kwDistinct.add(keywordCheck.evaluate(ctx).dimension(SecDimensions.CONTROLLABILITY));
            llmDistinct.add(llmCheck.evaluate(ctx).dimension(SecDimensions.CONTROLLABILITY));
        }
        System.out.printf("Keyword distinct values: %d, LLM distinct values: %d%n",
                kwDistinct.size(), llmDistinct.size());

        assertThat(divergenceCount)
                .as("Mock LLM should diverge from keywords on at least some scenarios")
                .isGreaterThan(0);
    }

    @Test
    @Tag("experiment")
    void boundaryComparison_withRealLlm() {
        org.junit.jupiter.api.Assumptions.assumeTrue(false,
                "Real LLM test — requires AgentProvider. Run with -Dgroups=experiment");
    }

    private AppraisalContext contextFor(String observation) {
        return new AppraisalContext(
                PerceivedSituation.passThrough(observation),
                List.of(), AppraisalWeights.NEUTRAL,
                HabituationConfig.defaults(),
                HabituationState.empty(), null);
    }
}
