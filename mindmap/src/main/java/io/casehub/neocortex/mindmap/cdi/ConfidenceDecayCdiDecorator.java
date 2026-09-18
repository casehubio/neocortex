package io.casehub.neocortex.mindmap.cdi;

import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.runtime.ConfidenceDecayDecorator;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Decorator
@Priority(55)
public class ConfidenceDecayCdiDecorator extends ConfidenceDecayDecorator {

    @Inject
    public ConfidenceDecayCdiDecorator(@Delegate @Any MindMapStore delegate,
                                       @ConfigProperty(name = "casehub.mindmap.confidence.half-life-days",
                                                        defaultValue = "30.0") double halfLifeDays) {
        super(delegate, halfLifeDays);
    }
}
