package io.casehub.neocortex.memory.cbr;

import java.util.List;
import java.util.Map;

public interface PlanEnsembleAnalyzer {
    EnsemblePlan analyze(String caseType,
                         List<ScoredCbrCase<ResolvedCase>> scoredCases,
                         List<AdaptedPlan> adaptedPlans,
                         Map<String, FeatureValue> currentFeatures);
}
