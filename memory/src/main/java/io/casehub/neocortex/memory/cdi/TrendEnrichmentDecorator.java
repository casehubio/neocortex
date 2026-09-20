package io.casehub.neocortex.memory.cdi;

import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.runtime.TrendEnrichmentCbrRecordStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(90)
public class TrendEnrichmentDecorator extends TrendEnrichmentCbrRecordStore {

    @Inject
    TrendEnrichmentDecorator(@Delegate @Any CbrRecordStore delegate) {
        super(delegate);
    }
}
