package io.casehub.neocortex.cognitive.index;

import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityOverlapModulationFactorTest {

    private InMemoryEntityAnnotationIndex annotationIndex;

    @BeforeEach
    void setUp() {
        annotationIndex = new InMemoryEntityAnnotationIndex();
    }

    private Memory memory(String memoryId) {
        return new Memory(memoryId, Subject.of("agent", "a1"),
                          new MemoryDomain("experience"), "t1", null, "some text",
                          java.util.Map.of(), Instant.now(), null, null, null, null, null, null);
    }

    private void addAnnotation(String memoryId, String entityNodeId) {
        annotationIndex.addAnnotation(new EntityAnnotation(
            UUID.randomUUID().toString(), "t1", "memory", memoryId,
            entityNodeId, 0, 5, "text", 0.9, AnnotationMethod.LLM_EXTRACTION,
            null, Instant.now()));
    }

    @Test
    void boostsWhenEntitiesOverlap() {
        addAnnotation("mem-1", "node-alice");
        addAnnotation("mem-1", "node-bob");

        var factor = new EntityOverlapModulationFactor(
            annotationIndex, "t1", Set.of("node-alice", "node-charlie"));

        double result = factor.apply(memory("mem-1"), null);

        assertThat(result).isEqualTo(1.15);
    }

    @Test
    void boostsProportionalToOverlapCount() {
        addAnnotation("mem-1", "node-alice");
        addAnnotation("mem-1", "node-bob");

        var factor = new EntityOverlapModulationFactor(
            annotationIndex, "t1", Set.of("node-alice", "node-bob"));

        double result = factor.apply(memory("mem-1"), null);

        assertThat(result).isEqualTo(1.3);
    }

    @Test
    void noBoostWhenNoOverlap() {
        addAnnotation("mem-1", "node-alice");

        var factor = new EntityOverlapModulationFactor(
            annotationIndex, "t1", Set.of("node-charlie"));

        double result = factor.apply(memory("mem-1"), null);

        assertThat(result).isEqualTo(1.0);
    }

    @Test
    void noBoostWhenNoAnnotations() {
        var factor = new EntityOverlapModulationFactor(
            annotationIndex, "t1", Set.of("node-alice"));

        double result = factor.apply(memory("mem-1"), null);

        assertThat(result).isEqualTo(1.0);
    }

    @Test
    void noBoostWhenContextEmpty() {
        addAnnotation("mem-1", "node-alice");

        var factor = new EntityOverlapModulationFactor(
            annotationIndex, "t1", Set.of());

        double result = factor.apply(memory("mem-1"), null);

        assertThat(result).isEqualTo(1.0);
    }
}
