package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.cognitive.observability.GraphMutation;
import io.casehub.neocortex.cognitive.observability.GraphMutationRecorded;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MergeRepointObserverTest {

    private InMemoryEntityAnnotationIndex index;
    private MergeRepointObserver observer;

    @BeforeEach
    void setUp() {
        index = new InMemoryEntityAnnotationIndex();
        observer = new MergeRepointObserver(index);
    }

    private EntityAnnotation annotation(String entityNodeId, String sourceId, String tenantId) {
        return new EntityAnnotation(UUID.randomUUID().toString(), tenantId, "memory", sourceId,
            entityNodeId, 0, 5, "James", 0.9, AnnotationMethod.LLM_EXTRACTION,
            null, Instant.now());
    }

    @Test
    void repointsAnnotationsOnNodesMerged() {
        index.addAnnotation(annotation("absorbed-node", "mem-1", "t1"));
        index.addAnnotation(annotation("absorbed-node", "mem-2", "t1"));

        var mutation = new GraphMutation.NodesMerged(
            "survivor-node", "absorbed-node", List.of(), Instant.now(), "merge");
        observer.onMutation(new GraphMutationRecorded("t1", mutation));

        assertThat(index.findByEntity("absorbed-node", "t1")).isEmpty();
        assertThat(index.findByEntity("survivor-node", "t1")).hasSize(2);
    }

    @Test
    void deduplicatesOnMerge() {
        index.addAnnotation(annotation("node-a", "mem-1", "t1"));
        index.addAnnotation(annotation("node-b", "mem-1", "t1"));

        var mutation = new GraphMutation.NodesMerged(
            "node-a", "node-b", List.of(), Instant.now(), "merge");
        observer.onMutation(new GraphMutationRecorded("t1", mutation));

        assertThat(index.findByEntity("node-b", "t1")).isEmpty();
        assertThat(index.findByEntity("node-a", "t1")).hasSize(1);
    }

    @Test
    void ignoresNonMergeMutations() {
        index.addAnnotation(annotation("node-1", "mem-1", "t1"));

        var mutation = new GraphMutation.NodeAdded(
            "new-node", "Alice", "sg-1", null, Instant.now(), "test");
        observer.onMutation(new GraphMutationRecorded("t1", mutation));

        assertThat(index.findByEntity("node-1", "t1")).hasSize(1);
    }

    @Test
    void noOpWhenNoMatchingAnnotations() {
        var mutation = new GraphMutation.NodesMerged(
            "survivor", "nonexistent", List.of(), Instant.now(), "merge");
        observer.onMutation(new GraphMutationRecorded("t1", mutation));
        // no exception
    }
}
