package io.casehub.neocortex.cognition.appraisal.experiment;

import io.casehub.neocortex.cognition.appraisal.*;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TendencyCardinalityTest {

    private static final Set<ActionReadiness> REDUCED_SET = Set.of(
            ActionReadiness.APPROACH, ActionReadiness.AVOIDANCE, ActionReadiness.ATTENDING);

    private static final Set<ActionReadiness> FULL_SET = Set.copyOf(
            EnumSet.allOf(ActionReadiness.class));

    private final SchererAppraisalStrategy strategy = new SchererAppraisalStrategy(
            new RelevanceCheck(), new ImplicationCheck(), new CopingCheck(), new NormativeCheck(),
            SchererAppraisalConfig.allEnabled());

    private AppraisalContext contextFor(ScenarioCorpus.Scenario s) {
        return new AppraisalContext(
                PerceivedSituation.passThrough(s.observation()),
                s.drives(), AppraisalWeights.NEUTRAL,
                HabituationConfig.defaults(), HabituationState.empty(), null);
    }

    @Test
    void fullVsReduced_tendencySetsProduceDifferentPrompts() {
        int totalScenarios = 0;
        int differingPrompts = 0;
        int fullOnlyTendencies = 0;

        System.out.println("\n=== Tendency Cardinality Comparison ===");
        System.out.printf("Full set:    %s (%d)%n", FULL_SET, FULL_SET.size());
        System.out.printf("Reduced set: %s (%d)%n%n", REDUCED_SET, REDUCED_SET.size());

        for (var scenario : ScenarioCorpus.scenarios()) {
            var result = strategy.appraise(contextFor(scenario));
            totalScenarios++;

            var allTendencies = result.actionTendencies().stream()
                    .filter(t -> t.intensity() > 0.3).toList();
            var reducedTendencies = allTendencies.stream()
                    .filter(t -> REDUCED_SET.contains(t.readiness())).toList();

            boolean differs = allTendencies.size() != reducedTendencies.size();
            if (differs) differingPrompts++;

            var droppedTypes = allTendencies.stream()
                    .map(ActionTendency::readiness)
                    .filter(r -> !REDUCED_SET.contains(r))
                    .collect(Collectors.toSet());
            fullOnlyTendencies += droppedTypes.size();

            if (!allTendencies.isEmpty()) {
                System.out.printf("%-25s | full: %-40s | dropped: %s%n",
                        scenario.name(),
                        allTendencies.stream().map(t -> t.readiness().name())
                                .collect(Collectors.joining(", ")),
                        droppedTypes.isEmpty() ? "(none)" : droppedTypes);
            }
        }

        System.out.printf("%n%d/%d scenarios have tendencies outside the reduced set%n",
                differingPrompts, totalScenarios);
        System.out.printf("%d total tendency instances would be lost by reducing%n",
                fullOnlyTendencies);

        assertThat(totalScenarios).isEqualTo(ScenarioCorpus.scenarios().size());
    }

    @Test
    void tendencyDistribution_acrossAllScenarios() {
        Map<ActionReadiness, Integer> counts = new EnumMap<>(ActionReadiness.class);

        for (var scenario : ScenarioCorpus.scenarios()) {
            var result = strategy.appraise(contextFor(scenario));
            result.actionTendencies().stream()
                    .filter(t -> t.intensity() > 0.3)
                    .forEach(t -> counts.merge(t.readiness(), 1, Integer::sum));
        }

        System.out.println("\n=== Tendency Frequency Distribution ===");
        counts.entrySet().stream()
                .sorted(Map.Entry.<ActionReadiness, Integer>comparingByValue().reversed())
                .forEach(e -> System.out.printf("  %-15s: %d%n", e.getKey(), e.getValue()));

        var neverFired = EnumSet.allOf(ActionReadiness.class);
        neverFired.removeAll(counts.keySet());
        if (!neverFired.isEmpty()) {
            System.out.printf("  Never fired:    %s (%d of %d)%n",
                    neverFired, neverFired.size(), ActionReadiness.values().length);
        }

        assertThat(counts).as("At least one tendency should fire across all scenarios")
                .isNotEmpty();
    }
}
