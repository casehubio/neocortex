package io.casehub.neocortex.mindmap.cdi;

import io.casehub.neocortex.memory.MemoryEntityErased;
import io.casehub.neocortex.memory.cbr.CbrCasesErased;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.runtime.NodeRefCleanupProcessor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class MindMapBeans {

    @Produces
    @ApplicationScoped
    public NodeRefCleanupProcessor nodeRefCleanupProcessor(MindMapStore store) {
        return new NodeRefCleanupProcessor(store);
    }

    void onMemoryEntityErased(@Observes MemoryEntityErased.ByEntity event,
                               NodeRefCleanupProcessor processor) {
        processor.removeRefs("memory", event.subject().id(), event.tenantId());
    }

    void onCbrCasesErased(@Observes CbrCasesErased.ByEntity event,
                           NodeRefCleanupProcessor processor) {
        processor.removeRefs("cbr", event.subject().id(), event.tenantId());
    }
}
