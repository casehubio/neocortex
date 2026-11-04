package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.EntityReferenceIndex;
import io.casehub.neocortex.entity.token.SurfaceFormTrie;
import io.casehub.neocortex.entity.token.TrieMatch;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.reflection.ReflectionRecorded;
import io.casehub.neocortex.memory.relationship.RelationshipRecorded;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class EntityReferenceRecordingObserver {

    private static final Logger LOG = Logger.getLogger(EntityReferenceRecordingObserver.class.getName());

    private final Instance<EntityReferenceIndex> referenceIndex;
    private final Instance<SurfaceFormTrieManager> trieManager;

    @Inject
    public EntityReferenceRecordingObserver(Instance<EntityReferenceIndex> referenceIndex,
                                            Instance<SurfaceFormTrieManager> trieManager) {
        this.referenceIndex = referenceIndex;
        this.trieManager = trieManager;
    }

    EntityReferenceRecordingObserver(EntityReferenceIndex index, SurfaceFormTrieManager manager) {
        this.referenceIndex = new SingletonInstance<>(index);
        this.trieManager = new SingletonInstance<>(manager);
    }

    public void onExperienceRecorded(@Observes ExperienceRecorded event) {
        recordReferences(event.memoryId(), event.event().description(), event.event().tenantId());
    }

    public void onRelationshipRecorded(@Observes RelationshipRecorded event) {
        recordReferences(event.memoryId(), event.event().description(), event.event().tenantId());
    }

    public void onReflectionRecorded(@Observes ReflectionRecorded event) {
        recordReferences(event.memoryId(), event.event().insight(), event.event().tenantId());
    }

    private void recordReferences(String memoryId, String text, String tenantId) {
        if (referenceIndex.isUnsatisfied() || trieManager.isUnsatisfied()) return;
        if (text == null || text.isBlank()) return;

        try {
            SurfaceFormTrie trie = trieManager.get().getTrieForTenant(tenantId);
            List<TrieMatch> matches = trie.scan(text);
            if (matches.isEmpty()) return;

            Set<String> entityIds = matches.stream()
                .map(m -> m.form().entityId())
                .collect(Collectors.toSet());

            referenceIndex.get().recordAll(entityIds, "memory", memoryId, tenantId);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to record entity references for memory {0}: {1}",
                new Object[]{memoryId, e.getMessage()});
        }
    }

    @SuppressWarnings("unchecked")
    private static class SingletonInstance<T> implements Instance<T> {
        private final T value;
        SingletonInstance(T value) { this.value = value; }
        @Override public T get() { return value; }
        @Override public boolean isUnsatisfied() { return value == null; }
        @Override public boolean isResolvable() { return value != null; }
        @Override public boolean isAmbiguous() { return false; }
        @Override public void destroy(T instance) {}
        @Override public Instance<T> select(java.lang.annotation.Annotation... qualifiers) { return this; }
        @Override public <U extends T> Instance<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { return (Instance<U>) this; }
        @Override public <U extends T> Instance<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { return (Instance<U>) this; }
        @Override public Handle<T> getHandle() { return null; }
        @Override public Iterable<Handle<T>> handles() { return null; }
        @Override public java.util.stream.Stream<T> stream() { return java.util.stream.Stream.of(value); }
        @Override public java.util.Iterator<T> iterator() { return java.util.stream.Stream.of(value).iterator(); }
    }
}
