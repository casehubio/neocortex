package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.AdaptationAction;
import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanAdapter;
import io.casehub.neocortex.memory.cbr.ResolvedCase;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@DefaultBean
@ApplicationScoped
public class NoOpPlanAdapter implements PlanAdapter {
    @Override
    public AdaptedPlan adapt(String caseType, ScoredCbrCase<ResolvedCase> retrieved,
                             Map<String, FeatureValue> currentFeatures) {
        return new AdaptedPlan(
                retrieved.cbrCase().resolutionStep().stream()
                         .map(t -> new AdaptedStep(
                                 t.bindingName(), t.capabilityName(), t.workerName(),
                                 t.stepOutcome(), t.priority(), t.parameters(),
                                 AdaptationAction.RETAINED, null))
                         .toList()
        );
    }
}
