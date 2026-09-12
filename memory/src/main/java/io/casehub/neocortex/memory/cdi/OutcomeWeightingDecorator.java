package io.casehub.neocortex.memory.cdi;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.OutcomeWeightingFunction;
import io.casehub.neocortex.memory.cbr.runtime.OutcomeWeightingCbrCaseMemoryStore;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(65)
@IfBuildProperty(name = "casehub.cbr.outcome-weighting.enabled", stringValue = "true")
public class OutcomeWeightingDecorator extends OutcomeWeightingCbrCaseMemoryStore {

    @Inject
    OutcomeWeightingDecorator(@Delegate @Any CbrCaseMemoryStore delegate,
                               OutcomeWeightingFunction weightingFunction) {
        super(delegate, weightingFunction);
    }
}
