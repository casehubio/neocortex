package io.casehub.neocortex.annotation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface EntityAnnotationIndex {

    String addAnnotation(EntityAnnotation annotation);

    List<String> addAnnotations(List<EntityAnnotation> annotations);

    void removeAnnotation(String annotationId, String tenantId);

    void removeBySource(String sourceType, String sourceId, String tenantId);

    void removeByOwnerSubject(String sourceType, String ownerSubjectId, String tenantId);

    int repointEntity(String fromEntityNodeId, String toEntityNodeId, String tenantId);

    List<EntityAnnotation> findByEntity(String entityNodeId, String tenantId);

    List<EntityAnnotation> findByEntityPair(String entityNodeId1, String entityNodeId2, String tenantId);

    List<EntityAnnotation> findByEntities(Set<String> entityNodeIds, String tenantId);

    List<EntityAnnotation> findBySource(String sourceType, String sourceId, String tenantId);

    default List<EntityAnnotation> findBySource(String sourceType, Collection<String> sourceIds, String tenantId) {
        if (sourceIds.isEmpty()) {return List.of();}
        return sourceIds.stream()
                        .flatMap(id -> findBySource(sourceType, id, tenantId).stream())
                        .toList();
    }


    Set<String> distinctEntitiesInSource(String sourceType, String sourceId, String tenantId);

    Map<String, Long> entityMentionCounts(String tenantId);
}
