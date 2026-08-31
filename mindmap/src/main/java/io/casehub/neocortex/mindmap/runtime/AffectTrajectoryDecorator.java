package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.mood.AffectEvents;
import io.casehub.neocortex.memory.mood.AffectRecorded;
import io.casehub.neocortex.mindmap.AbstractForwardingMindMapStore;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeUpdate;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Intercepts {@link MindMapStore#updateNode} to log PAD changes as
 * {@code domain="affect"} memory entries, creating a timestamped
 * affect trajectory per node.
 *
 * <p>This is a <strong>write-through decorator</strong> — it delegates
 * the update to the underlying store, then records the PAD change as
 * a memory entry if any PAD dimension changed. The stored memories
 * form a queryable trajectory via
 * {@code MemoryQuery.forEntity(nodeId, AffectEvents.DOMAIN, tenantId)}.
 *
 * <p>Uses {@link Instance} for graceful degradation — if no
 * {@link CaseMemoryStore} is on the classpath, PAD changes are not
 * logged (silently skipped).
 */
@Decorator
@Priority(65)
public class AffectTrajectoryDecorator extends AbstractForwardingMindMapStore {

    private final CaseMemoryStore memoryStore;
    private final Consumer<AffectRecorded> eventSink;

    @Inject
    public AffectTrajectoryDecorator(@Delegate @Any MindMapStore delegate,
                                     Instance<CaseMemoryStore> memoryStore,
                                     Event<AffectRecorded> event) {
        super(delegate);
        this.memoryStore = memoryStore.isResolvable() ? memoryStore.get() : null;
        this.eventSink = event::fire;
    }

    AffectTrajectoryDecorator(MindMapStore delegate, CaseMemoryStore memoryStore,
                              Consumer<AffectRecorded> eventSink) {
        super(delegate);
        this.memoryStore = memoryStore;
        this.eventSink = eventSink;
    }

    @Override
    public void updateNode(String nodeId, NodeUpdate update, String tenantId) {
        if (!hasPadUpdate(update) || memoryStore == null) {
            delegate().updateNode(nodeId, update, tenantId);
            return;
        }

        MindMapNode before = delegate().getNode(nodeId, tenantId);
        Double oldP = before.pleasure();
        Double oldA = before.arousal();
        Double oldD = before.dominance();

        delegate().updateNode(nodeId, update, tenantId);

        Double newP = update.pleasure() != null ? update.pleasure() : oldP;
        Double newA = update.arousal() != null ? update.arousal() : oldA;
        Double newD = update.dominance() != null ? update.dominance() : oldD;

        if (Objects.equals(oldP, newP) && Objects.equals(oldA, newA) && Objects.equals(oldD, newD)) return;

        double p = newP != null ? newP : 0.0;
        double a = newA != null ? newA : 0.0;
        double d = newD != null ? newD : 0.0;

        var input = AffectEvents.toMemoryInput(nodeId, tenantId, p, a, d);
        String memoryId = memoryStore.store(input);
        eventSink.accept(new AffectRecorded(nodeId, tenantId, memoryId));
    }

    private static boolean hasPadUpdate(NodeUpdate update) {
        return update.pleasure() != null || update.arousal() != null || update.dominance() != null;
    }
}
