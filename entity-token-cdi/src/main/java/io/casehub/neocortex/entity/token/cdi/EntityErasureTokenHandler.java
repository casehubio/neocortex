package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.cognitive.observability.GraphMutation;
import io.casehub.neocortex.cognitive.observability.GraphMutationRecorded;
import io.casehub.neocortex.entity.token.EntityReferenceIndex;
import io.casehub.neocortex.entity.token.TokenFormat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class EntityErasureTokenHandler {

    private static final Logger LOG = Logger.getLogger(EntityErasureTokenHandler.class.getName());

    private final Instance<EntityReferenceIndex> referenceIndex;

    @Inject
    public EntityErasureTokenHandler(Instance<EntityReferenceIndex> referenceIndex) {
        this.referenceIndex = referenceIndex;
    }

    public void onGraphMutation(@Observes GraphMutationRecorded event) {
        if (!(event.mutation() instanceof GraphMutation.NodeErased erased)) return;
        if (referenceIndex.isUnsatisfied()) return;

        try {
            referenceIndex.get().removeByEntity(erased.nodeId(), event.tenantId());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to remove entity references for erased node {0}: {1}",
                new Object[]{erased.nodeId(), e.getMessage()});
        }
    }
}
