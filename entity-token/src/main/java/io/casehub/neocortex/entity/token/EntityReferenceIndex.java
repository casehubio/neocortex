package io.casehub.neocortex.entity.token;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface EntityReferenceIndex {

    void record(String entityId, String sourceType, String sourceId, String tenantId);

    default void recordAll(Collection<String> entityIds, String sourceType,
                           String sourceId, String tenantId) {
        for (String entityId : entityIds) {
            record(entityId, sourceType, sourceId, tenantId);
        }
    }

    Set<String> findSourcesByEntity(String entityId, String tenantId);

    Set<String> findEntitiesBySource(String sourceType, String sourceId, String tenantId);

    Map<String, Long> entityMentionCounts(String tenantId);

    void removeBySource(String sourceType, String sourceId, String tenantId);

    void removeByEntity(String entityId, String tenantId);

    void repointEntity(String fromEntityId, String toEntityId, String tenantId);
}
