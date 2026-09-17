package io.casehub.neocortex.mindmap.cdi;

import io.casehub.neocortex.cognitive.index.DeclarativeRuleRegistry;
import io.casehub.neocortex.mindmap.DerivedEdgeRule;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.runtime.DerivedEdgeDecorator;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Decorator
@Priority(80)
public class DerivedEdgeCdiDecorator extends DerivedEdgeDecorator {

    @Inject
    public DerivedEdgeCdiDecorator(@Delegate @Any MindMapStore delegate,
                                   Instance<DerivedEdgeRule> rules,
                                   Instance<DeclarativeRuleRegistry> registry) {
        super(delegate,
              rules.stream().toList(),
              3,
              registry.isResolvable() ? registry.get() : null);
    }
}
