package io.casehub.neocortex.mindmap.cdi;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.mood.AffectRecorded;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.runtime.AffectTrajectoryDecorator;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Decorator
@Priority(65)
public class AffectTrajectoryCdiDecorator extends AffectTrajectoryDecorator {

    @Inject
    public AffectTrajectoryCdiDecorator(@Delegate @Any MindMapStore delegate,
                                        Instance<CaseMemoryStore> memoryStore,
                                        Event<AffectRecorded> event) {
        super(delegate,
              memoryStore.isResolvable() ? memoryStore.get() : null,
              event::fire);
    }
}
