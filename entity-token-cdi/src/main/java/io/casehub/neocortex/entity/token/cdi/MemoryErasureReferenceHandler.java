package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.EntityReferenceIndex;
import io.casehub.neocortex.memory.MemoryEntityErased;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MemoryErasureReferenceHandler {

    private static final Logger LOG = Logger.getLogger(MemoryErasureReferenceHandler.class.getName());

    private final Instance<EntityReferenceIndex> referenceIndex;

    @Inject
    public MemoryErasureReferenceHandler(Instance<EntityReferenceIndex> referenceIndex) {
        this.referenceIndex = referenceIndex;
    }

    public void onMemoryErased(@Observes MemoryEntityErased event) {
        if (referenceIndex.isUnsatisfied()) return;

        try {
            var index = referenceIndex.get();
            switch (event) {
                case MemoryEntityErased.ByRequest e ->
                    index.removeBySource("memory", e.subject().id(), e.tenantId());
                case MemoryEntityErased.ByEntity e ->
                    index.removeByEntity(e.subject().id(), e.tenantId());
                case MemoryEntityErased.CrossTenant e -> {
                    for (String tenantId : e.tenantIds()) {
                        index.removeByEntity(e.subject().id(), tenantId);
                    }
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to remove entity references on memory erasure: {0}",
                e.getMessage());
        }
    }
}
