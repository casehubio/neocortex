package io.casehub.neocortex.entity.token;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryEntityReferenceIndex implements EntityReferenceIndex {

    private final Set<Entry> entries = ConcurrentHashMap.newKeySet();

    @Override
    public void record(String entityId, String sourceType, String sourceId, String tenantId) {
        entries.add(new Entry(entityId, sourceType, sourceId, tenantId));
    }

    @Override
    public Set<String> findSourcesByEntity(String entityId, String tenantId) {
        return entries.stream()
            .filter(e -> e.tenantId.equals(tenantId) && e.entityId.equals(entityId))
            .map(e -> e.sourceId)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> findEntitiesBySource(String sourceType, String sourceId, String tenantId) {
        return entries.stream()
            .filter(e -> e.tenantId.equals(tenantId)
                && e.sourceType.equals(sourceType)
                && e.sourceId.equals(sourceId))
            .map(e -> e.entityId)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Map<String, Long> entityMentionCounts(String tenantId) {
        return entries.stream()
            .filter(e -> e.tenantId.equals(tenantId))
            .collect(Collectors.groupingBy(e -> e.entityId, Collectors.counting()));
    }

    @Override
    public void removeBySource(String sourceType, String sourceId, String tenantId) {
        entries.removeIf(e -> e.tenantId.equals(tenantId)
            && e.sourceType.equals(sourceType)
            && e.sourceId.equals(sourceId));
    }

    @Override
    public void removeByEntity(String entityId, String tenantId) {
        entries.removeIf(e -> e.tenantId.equals(tenantId) && e.entityId.equals(entityId));
    }

    @Override
    public void repointEntity(String fromEntityId, String toEntityId, String tenantId) {
        Set<Entry> toRepoint = entries.stream()
            .filter(e -> e.tenantId.equals(tenantId) && e.entityId.equals(fromEntityId))
            .collect(Collectors.toSet());
        entries.removeAll(toRepoint);
        for (Entry e : toRepoint) {
            entries.add(new Entry(toEntityId, e.sourceType, e.sourceId, e.tenantId));
        }
    }

    public void clearAll() {
        entries.clear();
    }

    private record Entry(String entityId, String sourceType, String sourceId, String tenantId) {
        Entry {
            Objects.requireNonNull(entityId);
            Objects.requireNonNull(sourceType);
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(tenantId);
        }
    }
}
