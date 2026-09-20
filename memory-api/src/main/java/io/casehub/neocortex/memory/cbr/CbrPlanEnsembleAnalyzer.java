package io.casehub.neocortex.memory.cbr;

import java.util.List;
import java.util.Map;

public interface CbrPlanEnsembleAnalyzer {
    EnsemblePlan analyze(String caseType,
                         List<CbrMatch<CbrPlanRecord>> scoredCases,
                         List<AdaptedPlan> adaptedPlans,
                         Map<String, FeatureValue> currentFeatures);
}
