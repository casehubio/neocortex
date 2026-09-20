package io.casehub.neocortex.memory.cdi;

import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.runtime.TemporalDecayCbrRecordStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(80)
public class TemporalDecayDecorator extends TemporalDecayCbrRecordStore {

    @Inject
    public TemporalDecayDecorator(@Delegate @Any CbrRecordStore delegate) {
        super(delegate);
    }
}
