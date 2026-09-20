package io.casehub.neocortex.memory.cdi;

import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.runtime.ScopeDecayCbrRecordStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(85)
public class ScopeDecayDecorator extends ScopeDecayCbrRecordStore {

    @Inject
    public ScopeDecayDecorator(@Delegate @Any CbrRecordStore delegate) {
        super(delegate);
    }
}
