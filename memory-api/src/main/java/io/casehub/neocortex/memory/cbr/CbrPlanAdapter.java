package io.casehub.neocortex.memory.cbr;

import java.util.Map;

public interface CbrPlanAdapter {
    AdaptedPlan adapt(String caseType, CbrMatch<CbrPlanRecord> retrieved,
                      Map<String, FeatureValue> currentFeatures);
}
