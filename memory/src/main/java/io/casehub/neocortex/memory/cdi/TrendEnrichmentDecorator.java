package io.casehub.neocortex.memory.cdi;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.runtime.TrendEnrichmentCbrCaseMemoryStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(90)
public class TrendEnrichmentDecorator extends TrendEnrichmentCbrCaseMemoryStore {

    @Inject
    TrendEnrichmentDecorator(@Delegate @Any CbrCaseMemoryStore delegate) {
        super(delegate);
    }
}
