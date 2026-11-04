package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.cognitive.observability.GraphMutation;
import io.casehub.neocortex.cognitive.observability.GraphMutationRecorded;
import io.casehub.neocortex.entity.token.EntityReferenceIndex;
import io.casehub.neocortex.entity.token.TokenFormat;
import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EntityMergeTokenHandler {

    private static final Logger LOG = Logger.getLogger(EntityMergeTokenHandler.class.getName());

    private final Instance<EntityReferenceIndex> referenceIndex;
    private final Instance<CaseMemoryStore> memoryStore;

    @Inject
    public EntityMergeTokenHandler(Instance<EntityReferenceIndex> referenceIndex,
                                    Instance<CaseMemoryStore> memoryStore) {
        this.referenceIndex = referenceIndex;
        this.memoryStore = memoryStore;
    }

    public void onGraphMutation(@Observes GraphMutationRecorded event) {
        if (!(event.mutation() instanceof GraphMutation.NodesMerged merged)) return;
        if (referenceIndex.isUnsatisfied()) return;

        try {
            var index = referenceIndex.get();
            index.repointEntity(merged.absorbedId(), merged.survivorId(), event.tenantId());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to repoint entity references for merge {0} -> {1}: {2}",
                new Object[]{merged.absorbedId(), merged.survivorId(), e.getMessage()});
        }
    }
}
