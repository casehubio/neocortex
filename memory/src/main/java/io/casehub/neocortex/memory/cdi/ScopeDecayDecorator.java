package io.casehub.neocortex.memory.cdi;

import io.casehub.neocortex.memory.cbr.CbrCaseMemoryStore;
import io.casehub.neocortex.memory.cbr.runtime.ScopeDecayCbrCaseMemoryStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(85)
public class ScopeDecayDecorator extends ScopeDecayCbrCaseMemoryStore {

    @Inject
    public ScopeDecayDecorator(@Delegate @Any CbrCaseMemoryStore delegate) {
        super(delegate);
    }
}
