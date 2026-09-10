package io.casehub.neocortex.memory.cbr;

import java.util.Map;

public interface PlanAdapter {
    AdaptedPlan adapt(String caseType, ScoredCbrCase<ResolvedCase> retrieved,
                      Map<String, FeatureValue> currentFeatures);
}
