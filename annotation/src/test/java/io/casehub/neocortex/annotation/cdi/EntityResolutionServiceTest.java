package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.mindmap.MindMapStore;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class EntityResolutionServiceTest {

    private InMemoryEntityAnnotationIndex annotationIndex;
    private InMemoryMindMapStore mindMapStore;
    private EntityResolutionService service;
    private String personSubgraphId;

    @BeforeEach
    void setUp() {
        annotationIndex = new InMemoryEntityAnnotationIndex();
        mindMapStore = new InMemoryMindMapStore();
        personSubgraphId = mindMapStore.createSubgraph(
            new SubgraphInput("People", SubgraphTypes.PERSON, null), "t1");

        service = new EntityResolutionService(annotationIndex, storeInstance(mindMapStore));
    }

    @SuppressWarnings("unchecked")
    private static Instance<MindMapStore> storeInstance(MindMapStore store) {
        return new Instance<>() {
            @Override public MindMapStore get() { return store; }
            @Override public boolean isUnsatisfied() { return store == null; }
            @Override public boolean isResolvable() { return store != null; }
            @Override public boolean isAmbiguous() { return false; }
            @Override public void destroy(MindMapStore instance) {}
            @Override public Instance<MindMapStore> select(Annotation... qualifiers) { return this; }
            @Override public <U extends MindMapStore> Instance<U> select(Class<U> subtype, Annotation... qualifiers) { return (Instance<U>) this; }
            @Override public <U extends MindMapStore> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { return (Instance<U>) this; }
            @Override public Handle<MindMapStore> getHandle() { return null; }
            @Override public Iterable<Handle<MindMapStore>> handles() { return null; }
            @Override public Stream<MindMapStore> stream() { return Stream.of(store); }
            @Override public Iterator<MindMapStore> iterator() { return Stream.of(store).iterator(); }
        };
    }

    private static Instance<MindMapStore> unsatisfiedInstance() {
        return storeInstance(null);
    }

    private EntityAnnotation ann(String sourceId, String entityNodeId,
                                  int start, int end, String mention) {
        return new EntityAnnotation(
            "a-" + sourceId, "t1", "memory", sourceId,
            entityNodeId, start, end, mention, 1.0, "test", null, Instant.now());
    }

    @Test
    void resolveTextsSubstitutesChangedNames() {
        String nodeId = mindMapStore.addNode(NodeInput.of("Alicia", personSubgraphId), "t1");
        annotationIndex.addAnnotation(ann("mem-1", nodeId, 0, 5, "Alice"));

        var result = service.resolveTexts("memory",
            Map.of("mem-1", "Alice went home."), "t1");

        assertThat(result.get("mem-1")).isEqualTo("Alicia went home.");
    }

    @Test
    void resolveTextsPassesThroughWhenNameUnchanged() {
        String nodeId = mindMapStore.addNode(NodeInput.of("Alice", personSubgraphId), "t1");
        annotationIndex.addAnnotation(ann("mem-1", nodeId, 0, 5, "Alice"));

        var result = service.resolveTexts("memory",
            Map.of("mem-1", "Alice went home."), "t1");

        assertThat(result.get("mem-1")).isEqualTo("Alice went home.");
    }

    @Test
    void resolveNodeNameReturnsCanonicalName() {
        String nodeId = mindMapStore.addNode(NodeInput.of("Alice", personSubgraphId), "t1");
        assertThat(service.resolveNodeName(nodeId, "t1")).isEqualTo("Alice");
    }

    @Test
    void resolveNodeNameReturnsFallbackForUnknownNode() {
        assertThat(service.resolveNodeName("unknown-id", "t1")).isEqualTo("unknown-id");
    }

    @Test
    void gracefulDegradationWhenMindMapStoreUnavailable() {
        var degradedService = new EntityResolutionService(annotationIndex, unsatisfiedInstance());

        var result = degradedService.resolveTexts("memory",
            Map.of("mem-1", "Alice went home."), "t1");
        assertThat(result.get("mem-1")).isEqualTo("Alice went home.");

        assertThat(degradedService.resolveNodeName("node-1", "t1")).isEqualTo("node-1");
    }
}
