package io.casehub.neocortex.memory.cdi;

import io.casehub.memory.runtime.CaseEnrichmentPipeline;
import io.casehub.neocortex.memory.CaseEnrichmentStep;
import io.casehub.neocortex.memory.CaseMemoryStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Comparator;

@Decorator
@Priority(jakarta.interceptor.Interceptor.Priority.APPLICATION)
public class CaseEnrichmentDecorator extends CaseEnrichmentPipeline {

    @Inject
    CaseEnrichmentDecorator(@Delegate @Any CaseMemoryStore delegate,
                            Instance<CaseEnrichmentStep> steps) {
        super(delegate, steps.stream()
            .sorted(Comparator.comparingInt(CaseEnrichmentStep::priority))
            .toList());
    }
}
