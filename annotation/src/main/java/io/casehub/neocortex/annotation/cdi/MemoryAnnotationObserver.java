package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.reflection.ReflectionRecorded;
import io.casehub.neocortex.memory.relationship.RelationshipRecorded;
import io.casehub.neocortex.mindmap.MindMapNode;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class MemoryAnnotationObserver {

    private static final Logger LOG = Logger.getLogger(MemoryAnnotationObserver.class.getName());

    private static final Set<String> ENTITY_SUBGRAPH_TYPES = Set.of(
        SubgraphTypes.PERSON, SubgraphTypes.PROJECT,
        SubgraphTypes.RESEARCH_AREA, SubgraphTypes.ORGANISATION,
        SubgraphTypes.CONCEPT, SubgraphTypes.ACTIVITY,
        SubgraphTypes.PLACE, SubgraphTypes.GENERAL);

    private final Instance<EntityAnnotationIndex> annotationIndex;
    private final Instance<MindMapStore> mindMapStore;

    @Inject
    public MemoryAnnotationObserver(Instance<EntityAnnotationIndex> annotationIndex,
                                      Instance<MindMapStore> mindMapStore) {
        this.annotationIndex = annotationIndex;
        this.mindMapStore = mindMapStore;
    }

    MemoryAnnotationObserver(EntityAnnotationIndex index, MindMapStore store) {
        this.annotationIndex = new SingletonInstance<>(index);
        this.mindMapStore = new SingletonInstance<>(store);
    }

    public void onExperienceRecorded(@Observes ExperienceRecorded event) {
        annotateMemory(event.memoryId(), event.event().description(),
            event.event().tenantId(), event.event().agentId());
    }

    public void onRelationshipRecorded(@Observes RelationshipRecorded event) {
        annotateMemory(event.memoryId(), event.event().description(),
            event.event().tenantId(), event.event().agentId());
    }

    public void onReflectionRecorded(@Observes ReflectionRecorded event) {
        annotateMemory(event.memoryId(), event.event().insight(),
            event.event().tenantId(), event.event().agentId());
    }

    private void annotateMemory(String memoryId, String text, String tenantId, String agentId) {
        if (annotationIndex.isUnsatisfied() || mindMapStore.isUnsatisfied()) return;
        if (text == null || text.isBlank()) return;

        try {
            var index = annotationIndex.get();
            var store = mindMapStore.get();

            List<EntityCandidate> candidates = buildCandidates(store, tenantId);
            List<MatchedSpan> matches = scanForMatches(text, candidates);
            if (matches.isEmpty()) return;

            Instant now = Instant.now();
            List<EntityAnnotation> annotations = matches.stream()
                .map(m -> new EntityAnnotation(
                    UUID.randomUUID().toString(), tenantId,
                    "memory", memoryId, m.nodeId(),
                    m.startOffset(), m.endOffset(), m.mentionText(),
                    1.0, "entity-scan", agentId, now))
                .toList();

            index.addAnnotations(annotations);
        } catch (Exception e) {
            LOG.log(Level.FINE, "Failed to annotate memory {0}: {1}",
                new Object[]{memoryId, e.getMessage()});
        }
    }

    private List<EntityCandidate> buildCandidates(MindMapStore store, String tenantId) {
        return store.listSubgraphs(tenantId).stream()
            .filter(sg -> ENTITY_SUBGRAPH_TYPES.contains(sg.type()))
            .flatMap(sg -> store.nodesIn(sg.id(), tenantId).stream())
            .map(node -> new EntityCandidate(node.id(), node.name()))
            .filter(c -> c.name() != null && c.name().length() >= 2)
            .sorted(Comparator.comparingInt((EntityCandidate c) -> c.name().length()).reversed())
            .toList();
    }

    static List<MatchedSpan> scanForMatches(String text, List<EntityCandidate> candidates) {
        List<MatchedSpan> allMatches = new ArrayList<>();
        for (EntityCandidate c : candidates) {
            int pos = 0;
            while ((pos = text.indexOf(c.name(), pos)) >= 0) {
                allMatches.add(new MatchedSpan(c.nodeId(), c.name(), pos, pos + c.name().length()));
                pos += c.name().length();
            }
        }
        allMatches.sort(Comparator.comparingInt(MatchedSpan::startOffset));
        List<MatchedSpan> filtered = new ArrayList<>();
        int lastEnd = -1;
        for (MatchedSpan m : allMatches) {
            if (m.startOffset() >= lastEnd) {
                filtered.add(m);
                lastEnd = m.endOffset();
            }
        }
        return filtered;
    }

    record EntityCandidate(String nodeId, String name) {}

    record MatchedSpan(String nodeId, String mentionText, int startOffset, int endOffset) {}

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
