package io.casehub.neocortex.cognition.salience;

import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.cognition.appraisal.PerceivedSituation;
import io.casehub.neocortex.cognition.appraisal.SalienceContext;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityAwareSalienceStrategyTest {

    private InMemoryEntityAnnotationIndex annotationIndex;
    private ConcernBasedSalienceStrategy delegate;

    @BeforeEach
    void setUp() {
        annotationIndex = new InMemoryEntityAnnotationIndex();
        delegate = new ConcernBasedSalienceStrategy();
    }

    private Memory concern(String memoryId, String text) {
        return new Memory(memoryId, Subject.of("agent", "a1"),
                          new MemoryDomain("experience"), "t1", null, text,
                          java.util.Map.of(), Instant.now(), null, null, null, null, null, null);
    }

    private void addAnnotation(String memoryId, String entityNodeId, String mentionText) {
        annotationIndex.addAnnotation(new EntityAnnotation(
            UUID.randomUUID().toString(), "t1", "memory", memoryId,
            entityNodeId, 0, mentionText.length(), mentionText,
            0.9, AnnotationMethod.LLM_EXTRACTION, null, Instant.now()));
    }

    @Test
    void boostsSalienceWhenEntityNameFoundInObservation() {
        Memory c1 = concern("mem-1", "discussion about photography");
        addAnnotation("mem-1", "node-alice", "Alice");

        var strategy = new EntityAwareSalienceStrategy(delegate, annotationIndex, "t1");
        var context = new SalienceContext("Alice walked into the room.", List.of(), null, List.of(c1), List.of());

        PerceivedSituation result = strategy.perceive(context);

        assertThat(result.salience()).containsKey("mem-1");
        assertThat(result.salience().get("mem-1")).isGreaterThan(0.0);
    }

    @Test
    void noBoostWhenNoEntityMatch() {
        Memory c1 = concern("mem-1", "discussion about photography");
        addAnnotation("mem-1", "node-alice", "Alice");

        var strategy = new EntityAwareSalienceStrategy(delegate, annotationIndex, "t1");
        var context = new SalienceContext("The weather was pleasant.", List.of(), null, List.of(c1), List.of());

        PerceivedSituation result = strategy.perceive(context);

        assertThat(result.salience().getOrDefault("mem-1", 0.0)).isEqualTo(0.0);
    }

    @Test
    void addsToDelegateSalience() {
        Memory c1 = concern("mem-1", "photography art");
        addAnnotation("mem-1", "node-frida", "Frida");

        var strategy = new EntityAwareSalienceStrategy(delegate, annotationIndex, "t1");
        var context = new SalienceContext("Frida exhibited photography art.", List.of(), null, List.of(c1), List.of());

        PerceivedSituation result = strategy.perceive(context);

        assertThat(result.salience().get("mem-1")).isGreaterThan(0.2);
    }

    @Test
    void caseInsensitiveMatch() {
        Memory c1 = concern("mem-1", "a walk in the garden");
        addAnnotation("mem-1", "node-james", "James");

        var strategy = new EntityAwareSalienceStrategy(delegate, annotationIndex, "t1");
        var context = new SalienceContext("james was in the garden.", List.of(), null, List.of(c1), List.of());

        PerceivedSituation result = strategy.perceive(context);

        assertThat(result.salience()).containsKey("mem-1");
    }

    @Test
    void noConcernAnnotationsNoBoost() {
        Memory c1 = concern("mem-1", "photography");

        var strategy = new EntityAwareSalienceStrategy(delegate, annotationIndex, "t1");
        var context = new SalienceContext("Alice walked in.", List.of(), null, List.of(c1), List.of());

        PerceivedSituation result = strategy.perceive(context);

        assertThat(result.salience().getOrDefault("mem-1", 0.0)).isEqualTo(0.0);
    }
}
