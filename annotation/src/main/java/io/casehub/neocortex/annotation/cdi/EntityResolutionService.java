package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.annotation.EntityReferenceResolver;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class EntityResolutionService {

    private final EntityAnnotationIndex annotationIndex;
    private final Instance<MindMapStore> mindMapStore;

    @Inject
    public EntityResolutionService(EntityAnnotationIndex annotationIndex,
                                     Instance<MindMapStore> mindMapStore) {
        this.annotationIndex = annotationIndex;
        this.mindMapStore = mindMapStore;
    }

    public Map<String, String> resolveTexts(String sourceType,
            Map<String, String> sourceTexts, String tenantId) {
        if (mindMapStore.isUnsatisfied()) return Map.copyOf(sourceTexts);
        var store = mindMapStore.get();
        return EntityReferenceResolver.resolve(
            annotationIndex, sourceType, sourceTexts, tenantId,
            nodeId -> {
                MindMapNode node = store.getNode(nodeId, tenantId);
                return node != null ? node.name() : null;
            });
    }

    public String resolveNodeName(String nodeId, String tenantId) {
        if (mindMapStore.isUnsatisfied()) return nodeId;
        MindMapNode node = mindMapStore.get().getNode(nodeId, tenantId);
        return node != null ? node.name() : nodeId;
    }
}
