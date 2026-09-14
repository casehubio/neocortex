package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@Decorator
@Priority(20)
public class MutationTrackingCdiDecorator extends MutationTrackingDecorator {

    @Inject
    public MutationTrackingCdiDecorator(@Delegate @Any MindMapStore delegate,
                                         Instance<SnapshotStore> snapshotStore,
                                         Event<GraphMutationRecorded> event) {
        super(delegate,
              snapshotStore.isResolvable() ? snapshotStore.get() : null,
              event::fire);
    }
}
