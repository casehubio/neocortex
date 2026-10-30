package io.casehub.neocortex.annotation.testing;

import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.EntityAnnotationIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class EntityAnnotationIndexContractTest {

    protected abstract EntityAnnotationIndex createIndex();

    private EntityAnnotationIndex index;
    private static final String TENANT = "t1";
    private static final String OTHER_TENANT = "t2";

    @BeforeEach
    void setUp() { index = createIndex(); }

    private EntityAnnotation annotation(String entityNodeId, String sourceType, String sourceId,
                                         int start, int end, String mention) {
        return new EntityAnnotation(UUID.randomUUID().toString(), TENANT, sourceType, sourceId,
            entityNodeId, start, end, mention, 0.9, AnnotationMethod.LLM_EXTRACTION, null, Instant.now());
    }

    private EntityAnnotation annotationWithOwner(String entityNodeId, String sourceId,
                                                  String ownerSubjectId) {
        return new EntityAnnotation(UUID.randomUUID().toString(), TENANT, "memory", sourceId,
            entityNodeId, 0, 5, "James", 1.0, AnnotationMethod.LLM_EXTRACTION, ownerSubjectId, Instant.now());
    }

    @Test
    void addAndFindByEntity() {
        EntityAnnotation a = annotation("node-1", "memory", "mem-1", 0, 5, "James");
        index.addAnnotation(a);
        List<EntityAnnotation> found = index.findByEntity("node-1", TENANT);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).mentionText()).isEqualTo("James");
    }

    @Test
    void addBatchAndFindByEntity() {
        var a1 = annotation("node-1", "memory", "mem-1", 0, 5, "James");
        var a2 = annotation("node-1", "memory", "mem-2", 0, 5, "He");
        index.addAnnotations(List.of(a1, a2));
        assertThat(index.findByEntity("node-1", TENANT)).hasSize(2);
    }

    @Test
    void removeAnnotation() {
        EntityAnnotation a = annotation("node-1", "memory", "mem-1", 0, 5, "James");
        String id = index.addAnnotation(a);
        index.removeAnnotation(id, TENANT);
        assertThat(index.findByEntity("node-1", TENANT)).isEmpty();
    }

    @Test
    void removeBySource() {
        index.addAnnotation(annotation("node-1", "memory", "mem-1", 0, 5, "James"));
        index.addAnnotation(annotation("node-2", "memory", "mem-1", 10, 15, "Clara"));
        index.addAnnotation(annotation("node-1", "memory", "mem-2", 0, 5, "James"));
        index.removeBySource("memory", "mem-1", TENANT);
        assertThat(index.findByEntity("node-1", TENANT)).hasSize(1);
        assertThat(index.findByEntity("node-2", TENANT)).isEmpty();
    }

    @Test
    void removeByOwnerSubject() {
        index.addAnnotation(annotationWithOwner("node-1", "mem-1", "alice"));
        index.addAnnotation(annotationWithOwner("node-2", "mem-2", "alice"));
        index.addAnnotation(annotationWithOwner("node-1", "mem-3", "bob"));
        index.removeByOwnerSubject("memory", "alice", TENANT);
        assertThat(index.findByEntity("node-1", TENANT)).hasSize(1);
        assertThat(index.findByEntity("node-2", TENANT)).isEmpty();
    }

    @Test
    void findByEntityPairSameDocument() {
        index.addAnnotation(annotation("node-1", "memory", "mem-1", 0, 5, "James"));
        index.addAnnotation(annotation("node-2", "memory", "mem-1", 10, 15, "Clara"));
        index.addAnnotation(annotation("node-1", "memory", "mem-2", 0, 5, "James"));
        List<EntityAnnotation> found = index.findByEntityPair("node-1", "node-2", TENANT);
        assertThat(found).hasSize(2);
        assertThat(found).allMatch(a -> a.sourceId().equals("mem-1"));
    }

    @Test
    void findByEntities() {
        index.addAnnotation(annotation("node-1", "memory", "mem-1", 0, 5, "James"));
        index.addAnnotation(annotation("node-2", "memory", "mem-2", 0, 5, "Clara"));
        index.addAnnotation(annotation("node-3", "memory", "mem-3", 0, 5, "Key"));
        List<EntityAnnotation> found = index.findByEntities(Set.of("node-1", "node-2"), TENANT);
        assertThat(found).hasSize(2);
    }

    @Test
    void findBySource() {
        index.addAnnotation(annotation("node-1", "memory", "mem-1", 0, 5, "James"));
        index.addAnnotation(annotation("node-2", "memory", "mem-1", 10, 15, "Clara"));
        List<EntityAnnotation> found = index.findBySource("memory", "mem-1", TENANT);
        assertThat(found).hasSize(2);
    }

    @Test
    void distinctEntitiesInSource() {
        index.addAnnotation(annotation("node-1", "memory", "mem-1", 0, 5, "James"));
        index.addAnnotation(annotation("node-1", "memory", "mem-1", 20, 22, "He"));
        index.addAnnotation(annotation("node-2", "memory", "mem-1", 10, 15, "Clara"));
        Set<String> entities = index.distinctEntitiesInSource("memory", "mem-1", TENANT);
        assertThat(entities).containsExactlyInAnyOrder("node-1", "node-2");
    }

    @Test
    void entityMentionCounts() {
        index.addAnnotation(annotation("node-1", "memory", "mem-1", 0, 5, "James"));
        index.addAnnotation(annotation("node-1", "memory", "mem-2", 0, 5, "He"));
        index.addAnnotation(annotation("node-2", "memory", "mem-1", 10, 15, "Clara"));
        Map<String, Long> counts = index.entityMentionCounts(TENANT);
        assertThat(counts).containsEntry("node-1", 2L);
        assertThat(counts).containsEntry("node-2", 1L);
    }

    @Test
    void repointEntity() {
        index.addAnnotation(annotation("old-node", "memory", "mem-1", 0, 5, "James"));
        index.addAnnotation(annotation("old-node", "memory", "mem-2", 0, 5, "He"));
        int repointed = index.repointEntity("old-node", "new-node", TENANT);
        assertThat(repointed).isEqualTo(2);
        assertThat(index.findByEntity("old-node", TENANT)).isEmpty();
        assertThat(index.findByEntity("new-node", TENANT)).hasSize(2);
    }

    @Test
    void repointEntityDeduplicates() {
        index.addAnnotation(annotation("node-a", "memory", "mem-1", 0, 5, "James"));
        index.addAnnotation(annotation("node-b", "memory", "mem-1", 0, 5, "James"));
        index.repointEntity("node-b", "node-a", TENANT);
        List<EntityAnnotation> found = index.findByEntity("node-a", TENANT);
        assertThat(found).hasSize(1);
    }

    @Test
    void repointNoMatchIsNoOp() {
        int repointed = index.repointEntity("nonexistent", "target", TENANT);
        assertThat(repointed).isEqualTo(0);
    }

    @Test
    void tenantIsolation() {
        index.addAnnotation(annotation("node-1", "memory", "mem-1", 0, 5, "James"));
        assertThat(index.findByEntity("node-1", OTHER_TENANT)).isEmpty();
    }

    @Test
    void nullableConfidence() {
        var a = new EntityAnnotation(UUID.randomUUID().toString(), TENANT, "memory", "mem-1",
            "node-1", 0, 5, "James", null, AnnotationMethod.MANUAL, null, Instant.now());
        index.addAnnotation(a);
        List<EntityAnnotation> found = index.findByEntity("node-1", TENANT);
        assertThat(found.get(0).confidence()).isNull();
    }

    @Test
    void nullableOwnerSubjectIdNotMatchedByRemoveByOwnerSubject() {
        var a = annotation("node-1", "memory", "mem-1", 0, 5, "James");
        index.addAnnotation(a);
        index.removeByOwnerSubject("memory", "nonexistent", TENANT);
        assertThat(index.findByEntity("node-1", TENANT)).hasSize(1);
    }
}
