package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.SituationClassifier;
import io.casehub.neocortex.caps.engine.SubThoughtSituationDecorator;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

@Decorator
@Priority(70)
public class SubThoughtSituationCdiDecorator extends SubThoughtSituationDecorator {

    @Inject
    public SubThoughtSituationCdiDecorator(@Delegate @Any SituationClassifier delegate) {
        super(delegate);
    }
}
