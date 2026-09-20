package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.AdaptationAction;
import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrPlanAdapter;
import io.casehub.neocortex.memory.cbr.CbrPlanRecord;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@DefaultBean
@ApplicationScoped
public class NoOpCbrPlanAdapter implements CbrPlanAdapter {
    @Override
    public AdaptedPlan adapt(String caseType, CbrMatch<CbrPlanRecord> retrieved,
                             Map<String, FeatureValue> currentFeatures) {
        return new AdaptedPlan(
                retrieved.cbrRecord().cbrPlanStep().stream()
                         .map(t -> new AdaptedStep(
                                 t.bindingName(), t.capabilityName(), t.workerName(),
                                 t.stepOutcome(), t.priority(), t.parameters(),
                                 AdaptationAction.RETAINED, null))
                         .toList()
        );
    }
}
