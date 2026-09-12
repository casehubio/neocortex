package io.casehub.neocortex.mindmap.cdi;

import io.casehub.neocortex.cognitive.index.DeclarativeRuleRegistry;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.TraitRule;
import io.casehub.neocortex.mindmap.runtime.TraitApplicationDecorator;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Decorator
@Priority(70)
public class TraitApplicationCdiDecorator extends TraitApplicationDecorator {

    @Inject
    public TraitApplicationCdiDecorator(@Delegate @Any MindMapStore delegate,
                                        Instance<TraitRule> rules,
                                        Instance<DeclarativeRuleRegistry> registry) {
        super(delegate,
              rules.stream().toList(),
              registry.isResolvable() ? registry.get() : null);
    }
}
