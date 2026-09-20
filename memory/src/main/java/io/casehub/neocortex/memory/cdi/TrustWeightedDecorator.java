package io.casehub.neocortex.memory.cdi;

import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.TrustWeightingFunction;
import io.casehub.neocortex.memory.cbr.runtime.TrustWeightedCbrRecordStore;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Decorator
@Priority(60)
@IfBuildProperty(name = "casehub.cbr.trust-weighting.enabled", stringValue = "true")
public class TrustWeightedDecorator extends TrustWeightedCbrRecordStore {

    @Inject
    TrustWeightedDecorator(@Delegate @Any CbrRecordStore delegate,
                            TrustWeightingFunction weightingFunction,
                            Instance<AgentTrustProvider> trustProviderInstance) {
        super(delegate, weightingFunction,
              trustProviderInstance.isResolvable() ? trustProviderInstance.get() : null);
    }
}
