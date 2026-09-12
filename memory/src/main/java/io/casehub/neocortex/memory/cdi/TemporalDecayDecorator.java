package io.casehub.neocortex.memory.cdi;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.runtime.TemporalDecayCbrCaseMemoryStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(80)
public class TemporalDecayDecorator extends TemporalDecayCbrCaseMemoryStore {

    @Inject
    public TemporalDecayDecorator(@Delegate @Any CbrCaseMemoryStore delegate) {
        super(delegate);
    }
}
