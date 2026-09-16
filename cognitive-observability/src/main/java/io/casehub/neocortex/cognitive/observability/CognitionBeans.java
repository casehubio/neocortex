package io.casehub.neocortex.cognitive.observability;

import io.casehub.neocortex.cognitive.index.CognitiveProfile;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class CognitionBeans {

    @Produces
    @ApplicationScoped
    public CognitionService cognitionService(MindMapStore store,
                                              Instance<CognitiveProfile> cognitiveProfile,
                                              Instance<SnapshotStore> snapshotStore) {
        return new CognitionService(
                store,
                cognitiveProfile.isResolvable() ? cognitiveProfile.get() : null,
                snapshotStore.isResolvable() ? snapshotStore.get() : null);
    }
}
