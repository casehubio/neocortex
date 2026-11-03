package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.Observation;
import io.casehub.neocortex.memory.reflection.ReflectionEvent;
import io.casehub.neocortex.memory.reflection.ReflectionRecorded;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAnnotationObserverTest {

    private InMemoryEntityAnnotationIndex annotationIndex;
    private InMemoryMindMapStore store;
    private MemoryAnnotationObserver observer;
    private String aliceNodeId;
    private String bobNodeId;

    @BeforeEach
    void setUp() {
        annotationIndex = new InMemoryEntityAnnotationIndex();
        store = new InMemoryMindMapStore();
        observer = new MemoryAnnotationObserver(annotationIndex, store);

        String personSg = store.createSubgraph(
            new SubgraphInput("People", SubgraphTypes.PERSON, null), "t1");
        aliceNodeId = store.addNode(NodeInput.of("Alice", personSg), "t1");
        bobNodeId = store.addNode(NodeInput.of("Bob", personSg), "t1");
    }

    private static Observation obs(String description) {
        return new Observation("agent-1", "t1", null, null, null,
                               description, null, Map.of(), "test-subject");
    }


    @Test
    void createsAnnotationsForExperienceEvent() {
        observer.onExperienceRecorded(new ExperienceRecorded(
            obs("Alice walked to the park."), "mem-1"));

        var annotations = annotationIndex.findBySource("memory", "mem-1", "t1");
        assertThat(annotations).hasSize(1);
        assertThat(annotations.getFirst().entityNodeId()).isEqualTo(aliceNodeId);
        assertThat(annotations.getFirst().mentionText()).isEqualTo("Alice");
        assertThat(annotations.getFirst().startOffset()).isEqualTo(0);
        assertThat(annotations.getFirst().endOffset()).isEqualTo(5);
    }

    @Test
    void createsAnnotationsForMultipleEntities() {
        observer.onExperienceRecorded(new ExperienceRecorded(
            obs("Alice met Bob today."), "mem-2"));

        var annotations = annotationIndex.findBySource("memory", "mem-2", "t1");
        assertThat(annotations).hasSize(2);
        assertThat(annotations).extracting(EntityAnnotation::mentionText)
            .containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void handlesMultipleOccurrencesOfSameName() {
        observer.onExperienceRecorded(new ExperienceRecorded(
            obs("Alice saw Alice in the mirror."), "mem-3"));

        var annotations = annotationIndex.findBySource("memory", "mem-3", "t1");
        assertThat(annotations).hasSize(2);
        assertThat(annotations).allMatch(a -> a.entityNodeId().equals(aliceNodeId));
        assertThat(annotations).extracting(EntityAnnotation::startOffset)
            .containsExactly(0, 10);
    }

    @Test
    void longestMatchWins() {
        String conceptSg = store.createSubgraph(
            new SubgraphInput("Concepts", SubgraphTypes.CONCEPT, null), "t1");
        store.addNode(NodeInput.of("Alice Johnson", conceptSg), "t1");

        observer.onExperienceRecorded(new ExperienceRecorded(
            obs("Alice Johnson arrived."), "mem-4"));

        var annotations = annotationIndex.findBySource("memory", "mem-4", "t1");
        assertThat(annotations).hasSize(1);
        assertThat(annotations.getFirst().mentionText()).isEqualTo("Alice Johnson");
    }

    @Test
    void noAnnotationsWhenNoEntityMatches() {
        observer.onExperienceRecorded(new ExperienceRecorded(
            obs("The weather was nice."), "mem-5"));

        var annotations = annotationIndex.findBySource("memory", "mem-5", "t1");
        assertThat(annotations).isEmpty();
    }

    @Test
    void annotationOffsetsMatchText() {
        String text = "Alice told Bob about the project.";
        observer.onExperienceRecorded(new ExperienceRecorded(
            obs(text), "mem-6"));

        var annotations = annotationIndex.findBySource("memory", "mem-6", "t1");
        for (EntityAnnotation a : annotations) {
            assertThat(text.substring(a.startOffset(), a.endOffset()))
                .isEqualTo(a.mentionText());
        }
    }

    @Test
    void createsAnnotationsForReflectionEvent() {
        observer.onReflectionRecorded(new ReflectionRecorded(
            new ReflectionEvent("agent-1", "t1", null, Instant.now(),
                "Alice seems to prefer direct communication.", 1,
                List.of("src-1"), null, Map.of()),
            "mem-7"));

        var annotations = annotationIndex.findBySource("memory", "mem-7", "t1");
        assertThat(annotations).hasSize(1);
        assertThat(annotations.getFirst().mentionText()).isEqualTo("Alice");
    }

    @Test
    void noAnnotationsForTextWithoutEntities() {
        observer.onExperienceRecorded(new ExperienceRecorded(
            obs("Just a quiet day."), "mem-8"));
        var annotations = annotationIndex.findBySource("memory", "mem-8", "t1");
        assertThat(annotations).isEmpty();
    }

    @Test
    void ignoresNodesFromNonEntitySubgraphs() {
        String typeSg = store.createSubgraph(
            new SubgraphInput("Types", SubgraphTypes.TYPE_SYSTEM, null), "t1");
        store.addNode(NodeInput.of("Frida", typeSg), "t1");

        observer.onExperienceRecorded(new ExperienceRecorded(
            obs("Frida painted."), "mem-9"));

        var annotations = annotationIndex.findBySource("memory", "mem-9", "t1");
        assertThat(annotations).isEmpty();
    }
}
