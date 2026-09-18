package io.casehub.neocortex.mindmap.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.mood.AffectEvents;
import io.casehub.neocortex.memory.mood.AffectRecorded;
import io.casehub.neocortex.mindmap.AbstractForwardingMindMapStore;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeUpdate;

import java.util.Objects;
import java.util.function.Consumer;

public class AffectTrajectoryDecorator extends AbstractForwardingMindMapStore {

    private final CaseMemoryStore memoryStore;
    private final Consumer<AffectRecorded> eventSink;

    public AffectTrajectoryDecorator(MindMapStore delegate, CaseMemoryStore memoryStore,
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
        Double      oldP   = before.pleasure();
        Double      oldA   = before.arousal();
        Double      oldD   = before.dominance();

        delegate().updateNode(nodeId, update, tenantId);

        Double newP = update.pleasure() != null ? update.pleasure() : oldP;
        Double newA = update.arousal() != null ? update.arousal() : oldA;
        Double newD = update.dominance() != null ? update.dominance() : oldD;

        if (Objects.equals(oldP, newP) && Objects.equals(oldA, newA) && Objects.equals(oldD, newD)) {return;}

        try {
            double p = newP != null ? newP : 0.0;
            double a = newA != null ? newA : 0.0;
            double d = newD != null ? newD : 0.0;

            var    input    = AffectEvents.toMemoryInput(nodeId, tenantId, p, a, d);
            String memoryId = memoryStore.store(input);
            eventSink.accept(new AffectRecorded(nodeId, tenantId, memoryId));
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(AffectTrajectoryDecorator.class.getName())
                                    .log(java.util.logging.Level.WARNING, "Affect trajectory recording failed for node " + nodeId, e);
        }
    }

    private static boolean hasPadUpdate(NodeUpdate update) {
        return update.pleasure() != null || update.arousal() != null || update.dominance() != null;
    }
}
