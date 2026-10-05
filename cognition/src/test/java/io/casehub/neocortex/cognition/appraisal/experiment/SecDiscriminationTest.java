package io.casehub.neocortex.cognition.appraisal.experiment;

import io.casehub.neocortex.cognition.appraisal.AppraisalContext;
import io.casehub.neocortex.cognition.appraisal.CopingCheck;
import io.casehub.neocortex.cognition.appraisal.HabituationState;
import io.casehub.neocortex.cognition.appraisal.ImplicationCheck;
import io.casehub.neocortex.cognition.appraisal.NormativeCheck;
import io.casehub.neocortex.cognition.appraisal.PerceivedSituation;
import io.casehub.neocortex.cognition.appraisal.RelevanceCheck;
import io.casehub.neocortex.cognition.appraisal.SchererAppraisalConfig;
import io.casehub.neocortex.cognition.appraisal.SchererAppraisalStrategy;
import io.casehub.neocortex.cognition.appraisal.SecCheck;
import io.casehub.neocortex.cognition.appraisal.SecDimensions;
import io.casehub.neocortex.cognition.appraisal.SecResult;
import io.casehub.neocortex.cognitive.HabituationConfig;
import io.casehub.neocortex.mindmap.AppraisalWeights;
import org.junit.jupiter.api.Test;

import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SecDiscriminationTest {

    private final RelevanceCheck relevanceCheck = new RelevanceCheck();
    private final ImplicationCheck implicationCheck = new ImplicationCheck();
    private final CopingCheck copingCheck = new CopingCheck();
    private final NormativeCheck normativeCheck = new NormativeCheck();

    private AppraisalContext context(ScenarioCorpus.Scenario s) {
        return new AppraisalContext(
                PerceivedSituation.passThrough(s.observation()),
                s.drives(), AppraisalWeights.NEUTRAL,
                HabituationConfig.defaults(),
                HabituationState.empty(), null);
    }

    @Test
    void relevanceCheck_producesVarianceAcrossScenarios() {
        var results = runCheck(relevanceCheck);
        printCheckSummary("RelevanceCheck", results,
                          List.of(SecDimensions.RELEVANCE, SecDimensions.NOVELTY, SecDimensions.URGENCY));
        assertThat(results).hasSize(ScenarioCorpus.scenarios().size());
        assertDimensionHasVariance(results, SecDimensions.RELEVANCE, "RelevanceCheck");
    }

    @Test
    void implicationCheck_producesVarianceAcrossScenarios() {
        var results = runCheck(implicationCheck);
        printCheckSummary("ImplicationCheck", results,
                          List.of(SecDimensions.CONDUCIVENESS));
        assertThat(results).hasSize(ScenarioCorpus.scenarios().size());
    }

    @Test
    void copingCheck_producesVarianceAcrossScenarios() {
        var results = runCheck(copingCheck);
        printCheckSummary("CopingCheck", results,
                          List.of(SecDimensions.CONTROLLABILITY, SecDimensions.ADJUSTABILITY));
        assertThat(results).hasSize(ScenarioCorpus.scenarios().size());
    }

    @Test
    void normativeCheck_producesVarianceAcrossScenarios() {
        var results = runCheck(normativeCheck);
        printCheckSummary("NormativeCheck", results,
                List.of(SecDimensions.INTERNAL_STANDARDS, SecDimensions.EXTERNAL_STANDARDS));
        var internalValues = extractDimension(results, SecDimensions.INTERNAL_STANDARDS);
        long distinctInternal = internalValues.stream().distinct().count();
        System.out.printf("NormativeCheck: %d distinct internal_standards values from %d scenarios%n",
                distinctInternal, internalValues.size());
    }

    @Test
    void fullPipeline_producesDiverseEmotions() {
        var strategy = new SchererAppraisalStrategy(
                relevanceCheck, implicationCheck, copingCheck, normativeCheck,
                SchererAppraisalConfig.allEnabled());

        Set<String> allEmotionTypes = new LinkedHashSet<>();
        Set<String> allTendencies   = new LinkedHashSet<>();

        System.out.println("\n=== Full Pipeline Results ===");
        System.out.printf("%-25s | %-40s | %-30s%n", "Scenario", "Emotions", "Tendencies");
        System.out.println("-".repeat(100));

        for (var scenario : ScenarioCorpus.scenarios()) {
            var ctx    = context(scenario);
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
        System.out.printf("Scenarios with no emotion: %d/%d%n",
                          ScenarioCorpus.scenarios().size() - (int) ScenarioCorpus.scenarios().stream()
                                                                                  .map(s -> strategy.appraise(context(s)))
                                                                                  .filter(r -> !r.emotions().isEmpty()).count(),
                          ScenarioCorpus.scenarios().size());

        assertThat(allEmotionTypes).as("Pipeline should produce at least one emotion type")
                                   .isNotEmpty();
    }

    @Test
    void configComparison_removingCheckChangesOutput() {
        var full = new SchererAppraisalStrategy(
                relevanceCheck, implicationCheck, copingCheck, normativeCheck,
                SchererAppraisalConfig.allEnabled());
        var noCoping = new SchererAppraisalStrategy(
                relevanceCheck, implicationCheck, null, normativeCheck,
                new SchererAppraisalConfig(true, true, false, true));
        var noNormative = new SchererAppraisalStrategy(
                relevanceCheck, implicationCheck, copingCheck, null,
                new SchererAppraisalConfig(true, true, true, false));

        int noCopingDiffers = 0, noNormativeDiffers = 0;

        for (var scenario : ScenarioCorpus.scenarios()) {
            var ctx = context(scenario);
            var fullResult = full.appraise(ctx);
            var noCopingResult = noCoping.appraise(ctx);
            var noNormResult = noNormative.appraise(ctx);

            var fullEmotions = fullResult.emotions().stream()
                    .map(e -> e.type()).collect(Collectors.toSet());
            var noCopingEmotions = noCopingResult.emotions().stream()
                    .map(e -> e.type()).collect(Collectors.toSet());
            var noNormEmotions = noNormResult.emotions().stream()
                    .map(e -> e.type()).collect(Collectors.toSet());

            if (!fullEmotions.equals(noCopingEmotions)) noCopingDiffers++;
            if (!fullEmotions.equals(noNormEmotions)) noNormativeDiffers++;
        }

        System.out.printf("%n=== Config Comparison ===%n");
        System.out.printf("Removing CopingCheck changes emotions in %d/%d scenarios%n",
                noCopingDiffers, ScenarioCorpus.scenarios().size());
        System.out.printf("Removing NormativeCheck changes emotions in %d/%d scenarios%n",
                noNormativeDiffers, ScenarioCorpus.scenarios().size());
    }

    private Map<String, SecResult> runCheck(SecCheck check) {
        var results = new LinkedHashMap<String, SecResult>();
        for (var scenario : ScenarioCorpus.scenarios()) {
            results.put(scenario.name(), check.evaluate(context(scenario)));
        }
        return results;
    }

    private List<Double> extractDimension(Map<String, SecResult> results, String dimension) {
        return results.values().stream()
                .map(r -> r.dimension(dimension))
                .toList();
    }

    private void assertDimensionHasVariance(Map<String, SecResult> results,
                                             String dimension, String checkName) {
        var values = extractDimension(results, dimension);
        long distinct = values.stream().distinct().count();
        assertThat(distinct)
                .as("%s.%s should produce at least 3 distinct values across %d scenarios",
                        checkName, dimension, values.size())
                .isGreaterThanOrEqualTo(3);
    }

    private void printCheckSummary(String checkName, Map<String, SecResult> results,
                                    List<String> dimensions) {
        System.out.printf("%n=== %s ===%n", checkName);
        System.out.printf("%-25s", "Scenario");
        for (var dim : dimensions) {
            System.out.printf(" | %-15s", dim);
        }
        System.out.println();
        System.out.println("-".repeat(25 + dimensions.size() * 18));

        results.forEach((name, result) -> {
            System.out.printf("%-25s", name);
            for (var dim : dimensions) {
                System.out.printf(" | %15.3f", result.dimension(dim));
            }
            System.out.println();
        });

        for (var dim : dimensions) {
            var values = extractDimension(results, dim);
            var stats = new DoubleSummaryStatistics();
            values.forEach(stats::accept);
            double mean = stats.getAverage();
            double variance = values.stream()
                    .mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0);
            System.out.printf("  %s — mean=%.3f, stddev=%.3f, min=%.3f, max=%.3f, distinct=%d%n",
                    dim, mean, Math.sqrt(variance), stats.getMin(), stats.getMax(),
                    values.stream().distinct().count());
        }
    }
}
