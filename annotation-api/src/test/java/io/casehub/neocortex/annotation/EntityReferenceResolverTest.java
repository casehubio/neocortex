package io.casehub.neocortex.annotation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityReferenceResolverTest {

    private StubAnnotationIndex index;

    @BeforeEach
    void setUp() {
        index = new StubAnnotationIndex();
    }

    static class StubAnnotationIndex implements EntityAnnotationIndex {
        private final Map<String, EntityAnnotation> store = new HashMap<>();

        @Override public String addAnnotation(EntityAnnotation a) {
            store.put(a.annotationId(), a);
            return a.annotationId();
        }

        @Override public List<String> addAnnotations(List<EntityAnnotation> annotations) {
            return annotations.stream().map(this::addAnnotation).toList();
        }

        @Override public void removeAnnotation(String id, String tenantId) { store.remove(id); }
        @Override public void removeBySource(String st, String si, String t) {}
        @Override public void removeByOwnerSubject(String st, String osi, String t) {}
        @Override public int repointEntity(String f, String to, String t) { return 0; }
        @Override public List<EntityAnnotation> findByEntity(String eni, String t) { return List.of(); }
        @Override public List<EntityAnnotation> findByEntityPair(String e1, String e2, String t) { return List.of(); }
        @Override public List<EntityAnnotation> findByEntities(Set<String> enis, String t) { return List.of(); }
        @Override public Set<String> distinctEntitiesInSource(String st, String si, String t) { return Set.of(); }
        @Override public Map<String, Long> entityMentionCounts(String t) { return Map.of(); }

        @Override
        public List<EntityAnnotation> findBySource(String sourceType, String sourceId, String tenantId) {
            return store.values().stream()
                .filter(a -> a.sourceType().equals(sourceType) && a.sourceId().equals(sourceId) && a.tenantId().equals(tenantId))
                .toList();
        }
    }

    private EntityAnnotation ann(String sourceId, String entityNodeId,
                                  int start, int end, String mention) {
        return new EntityAnnotation(
            "a-" + sourceId + "-" + start, "t1", "memory", sourceId,
            entityNodeId, start, end, mention, 1.0, "test", null, Instant.now());
    }

    @Test
    void substitutesChangedName() {
        index.addAnnotation(ann("mem-1", "node-1", 0, 5, "Alice"));

        var result = EntityReferenceResolver.resolve(index, "memory",
            Map.of("mem-1", "Alice went home."), "t1",
            id -> "node-1".equals(id) ? "Alicia" : id);

        assertThat(result.get("mem-1")).isEqualTo("Alicia went home.");
    }

    @Test
    void returnsOriginalTextWhenNoAnnotations() {
        var result = EntityReferenceResolver.resolve(index, "memory",
            Map.of("mem-1", "No entities here."), "t1", id -> id);

        assertThat(result.get("mem-1")).isEqualTo("No entities here.");
    }

    @Test
    void returnsOriginalTextWhenNameUnchanged() {
        index.addAnnotation(ann("mem-1", "node-1", 0, 5, "Alice"));

        var result = EntityReferenceResolver.resolve(index, "memory",
            Map.of("mem-1", "Alice went home."), "t1",
            id -> "Alice");

        assertThat(result.get("mem-1")).isEqualTo("Alice went home.");
    }

    @Test
    void handlesMultipleAnnotationsInSameText() {
        index.addAnnotations(List.of(
            ann("mem-1", "node-1", 0, 5, "Alice"),
            ann("mem-1", "node-2", 10, 13, "Bob")));

        var result = EntityReferenceResolver.resolve(index, "memory",
            Map.of("mem-1", "Alice met Bob today."), "t1",
            id -> switch (id) {
                case "node-1" -> "Alicia";
                case "node-2" -> "Roberto";
                default -> id;
            });

        assertThat(result.get("mem-1")).isEqualTo("Alicia met Roberto today.");
    }

    @Test
    void handlesBatchAcrossMultipleSources() {
        index.addAnnotations(List.of(
            ann("mem-1", "node-1", 0, 5, "Alice"),
            ann("mem-2", "node-1", 6, 11, "Alice")));

        var result = EntityReferenceResolver.resolve(index, "memory",
            Map.of("mem-1", "Alice went home.", "mem-2", "Today Alice returned."),
            "t1", id -> "Alicia");

        assertThat(result.get("mem-1")).isEqualTo("Alicia went home.");
        assertThat(result.get("mem-2")).isEqualTo("Today Alicia returned.");
    }

    @Test
    void handlesEmptySourceTexts() {
        var result = EntityReferenceResolver.resolve(index, "memory",
            Map.of(), "t1", id -> id);
        assertThat(result).isEmpty();
    }

    @Test
    void skipsAnnotationWhenNodeIdResolvesToNull() {
        index.addAnnotation(ann("mem-1", "node-1", 0, 5, "Alice"));

        var result = EntityReferenceResolver.resolve(index, "memory",
            Map.of("mem-1", "Alice went home."), "t1",
            id -> null);

        assertThat(result.get("mem-1")).isEqualTo("Alice went home.");
    }

    @Test
    void handlesNameLengthChange() {
        index.addAnnotations(List.of(
            ann("mem-1", "node-1", 0, 2, "Jo"),
            ann("mem-1", "node-2", 7, 11, "Jane")));

        var result = EntityReferenceResolver.resolve(index, "memory",
            Map.of("mem-1", "Jo met Jane today."), "t1",
            id -> switch (id) {
                case "node-1" -> "Jonathan";
                case "node-2" -> "J";
                default -> id;
            });

        assertThat(result.get("mem-1")).isEqualTo("Jonathan met J today.");
    }
}
