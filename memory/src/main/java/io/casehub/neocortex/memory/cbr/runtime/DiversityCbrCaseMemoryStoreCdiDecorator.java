package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Decorator
@Priority(55)
@IfBuildProperty(name = "casehub.cbr.diversity.enabled", stringValue = "true")
public class DiversityCbrCaseMemoryStoreCdiDecorator extends DiversityCbrCaseMemoryStore {

    @Inject
    public DiversityCbrCaseMemoryStoreCdiDecorator(
            @Delegate @Any CbrCaseMemoryStore delegate,
            @ConfigProperty(name = "casehub.cbr.diversity.lambda",
                            defaultValue = "0.7") double lambda,
            @ConfigProperty(name = "casehub.cbr.diversity.over-fetch-factor",
                            defaultValue = "3.0") double overFetchFactor) {
        super(delegate, lambda, overFetchFactor, true);
    }
}
