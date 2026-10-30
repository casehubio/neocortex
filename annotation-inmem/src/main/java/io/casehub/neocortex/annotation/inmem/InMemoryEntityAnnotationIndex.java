package io.casehub.neocortex.annotation.inmem;

import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryEntityAnnotationIndex implements EntityAnnotationIndex {

    private final Map<String, EntityAnnotation> store = new ConcurrentHashMap<>();

    public void clearAll() { store.clear(); }

    @Override
    public String addAnnotation(EntityAnnotation annotation) {
        store.put(annotation.annotationId(), annotation);
        return annotation.annotationId();
    }

    @Override
    public List<String> addAnnotations(List<EntityAnnotation> annotations) {
        List<String> ids = new ArrayList<>();
        for (EntityAnnotation a : annotations) { ids.add(addAnnotation(a)); }
        return ids;
    }

    @Override
    public void removeAnnotation(String annotationId, String tenantId) {
        store.values().removeIf(a ->
            a.annotationId().equals(annotationId) && a.tenantId().equals(tenantId));
    }

    @Override
    public void removeBySource(String sourceType, String sourceId, String tenantId) {
        store.values().removeIf(a -> a.tenantId().equals(tenantId)
            && a.sourceType().equals(sourceType) && a.sourceId().equals(sourceId));
    }

    @Override
    public void removeByOwnerSubject(String sourceType, String ownerSubjectId, String tenantId) {
        store.values().removeIf(a -> a.tenantId().equals(tenantId)
            && a.sourceType().equals(sourceType)
            && ownerSubjectId.equals(a.ownerSubjectId()));
    }

    @Override
    public int repointEntity(String fromEntityNodeId, String toEntityNodeId, String tenantId) {
        List<EntityAnnotation> toRepoint = store.values().stream()
            .filter(a -> a.tenantId().equals(tenantId) && a.entityNodeId().equals(fromEntityNodeId))
            .toList();
        int count = toRepoint.size();
        for (EntityAnnotation a : toRepoint) {
            store.remove(a.annotationId());
            boolean duplicate = store.values().stream().anyMatch(existing ->
                existing.tenantId().equals(tenantId)
                && existing.entityNodeId().equals(toEntityNodeId)
                && existing.sourceType().equals(a.sourceType())
                && existing.sourceId().equals(a.sourceId())
                && existing.startOffset() == a.startOffset()
                && existing.endOffset() == a.endOffset());
            if (!duplicate) {
                var repointed = new EntityAnnotation(a.annotationId(), a.tenantId(),
                    a.sourceType(), a.sourceId(), toEntityNodeId,
                    a.startOffset(), a.endOffset(), a.mentionText(),
                    a.confidence(), a.method(), a.ownerSubjectId(), a.createdAt());
                store.put(repointed.annotationId(), repointed);
            }
        }
        return count;
    }

    @Override
    public List<EntityAnnotation> findByEntity(String entityNodeId, String tenantId) {
        return store.values().stream()
            .filter(a -> a.tenantId().equals(tenantId) && a.entityNodeId().equals(entityNodeId))
            .toList();
    }

    @Override
    public List<EntityAnnotation> findByEntityPair(String entityNodeId1, String entityNodeId2,
                                                    String tenantId) {
        Map<String, Set<String>> sourceToEntities = new HashMap<>();
        for (EntityAnnotation a : store.values()) {
            if (!a.tenantId().equals(tenantId)) continue;
            if (a.entityNodeId().equals(entityNodeId1) || a.entityNodeId().equals(entityNodeId2)) {
                String sourceKey = a.sourceType() + ":" + a.sourceId();
                sourceToEntities.computeIfAbsent(sourceKey, k -> new HashSet<>())
                    .add(a.entityNodeId());
            }
        }
        Set<String> bothPresent = sourceToEntities.entrySet().stream()
            .filter(e -> e.getValue().contains(entityNodeId1) && e.getValue().contains(entityNodeId2))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
        return store.values().stream()
            .filter(a -> a.tenantId().equals(tenantId)
                && bothPresent.contains(a.sourceType() + ":" + a.sourceId())
                && (a.entityNodeId().equals(entityNodeId1) || a.entityNodeId().equals(entityNodeId2)))
            .toList();
    }

    @Override
    public List<EntityAnnotation> findByEntities(Set<String> entityNodeIds, String tenantId) {
        return store.values().stream()
            .filter(a -> a.tenantId().equals(tenantId) && entityNodeIds.contains(a.entityNodeId()))
            .toList();
    }

    @Override
    public List<EntityAnnotation> findBySource(String sourceType, String sourceId, String tenantId) {
        return store.values().stream()
            .filter(a -> a.tenantId().equals(tenantId)
                && a.sourceType().equals(sourceType) && a.sourceId().equals(sourceId))
            .toList();
    }

    @Override
    public Set<String> distinctEntitiesInSource(String sourceType, String sourceId, String tenantId) {
        return store.values().stream()
            .filter(a -> a.tenantId().equals(tenantId)
                && a.sourceType().equals(sourceType) && a.sourceId().equals(sourceId))
            .map(EntityAnnotation::entityNodeId)
            .collect(Collectors.toSet());
    }

    @Override
    public Map<String, Long> entityMentionCounts(String tenantId) {
        return store.values().stream()
            .filter(a -> a.tenantId().equals(tenantId))
            .collect(Collectors.groupingBy(EntityAnnotation::entityNodeId, Collectors.counting()));
    }
}
