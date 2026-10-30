package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.cognitive.observability.GraphMutation;
import io.casehub.neocortex.cognitive.observability.GraphMutationRecorded;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class MergeRepointObserver {

    private final EntityAnnotationIndex index;

    @Inject
    public MergeRepointObserver(EntityAnnotationIndex index) {
        this.index = index;
    }

    MergeRepointObserver() {
        this.index = null;
    }

    void onMutation(@Observes GraphMutationRecorded event) {
        if (event.mutation() instanceof GraphMutation.NodesMerged merged) {
            index.repointEntity(merged.absorbedId(), merged.survivorId(), event.tenantId());
        }
    }
}
