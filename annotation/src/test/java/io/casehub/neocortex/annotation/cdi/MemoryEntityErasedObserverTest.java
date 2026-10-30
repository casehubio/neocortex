package io.casehub.neocortex.annotation.cdi;

import io.casehub.neocortex.annotation.AnnotationMethod;
import io.casehub.neocortex.annotation.EntityAnnotation;
import io.casehub.neocortex.annotation.inmem.InMemoryEntityAnnotationIndex;
import io.casehub.neocortex.memory.MemoryEntityErased;
import io.casehub.neocortex.memory.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryEntityErasedObserverTest {

    private InMemoryEntityAnnotationIndex index;
    private MemoryEntityErasedObserver observer;

    @BeforeEach
    void setUp() {
        index = new InMemoryEntityAnnotationIndex();
        observer = new MemoryEntityErasedObserver(index);
    }

    private EntityAnnotation annotation(String entityNodeId, String sourceId,
                                         String ownerSubjectId, String tenantId) {
        return new EntityAnnotation(UUID.randomUUID().toString(), tenantId, "memory", sourceId,
            entityNodeId, 0, 5, "James", 0.9, AnnotationMethod.LLM_EXTRACTION,
            ownerSubjectId, Instant.now());
    }

    @Test
    void byEntityRemovesAnnotationsByOwnerSubject() {
        index.addAnnotation(annotation("node-1", "mem-1", "alice", "t1"));
        index.addAnnotation(annotation("node-2", "mem-2", "alice", "t1"));
        index.addAnnotation(annotation("node-1", "mem-3", "bob", "t1"));

        observer.onErased(new MemoryEntityErased.ByEntity("t1", 2,
            Subject.of("agent", "alice"), Instant.now()));

        assertThat(index.findByEntity("node-1", "t1")).hasSize(1);
        assertThat(index.findByEntity("node-2", "t1")).isEmpty();
    }

    @Test
    void byRequestRemovesAnnotationsByOwnerSubject() {
        index.addAnnotation(annotation("node-1", "mem-1", "alice", "t1"));

        observer.onErased(new MemoryEntityErased.ByRequest("t1", 1,
            Subject.of("agent", "alice"), new io.casehub.neocortex.memory.MemoryDomain("experience"),
            Instant.now()));

        assertThat(index.findByEntity("node-1", "t1")).isEmpty();
    }

    @Test
    void crossTenantRemovesAcrossTenants() {
        index.addAnnotation(annotation("node-1", "mem-1", "alice", "t1"));
        index.addAnnotation(annotation("node-1", "mem-2", "alice", "t2"));
        index.addAnnotation(annotation("node-1", "mem-3", "bob", "t1"));

        observer.onErased(new MemoryEntityErased.CrossTenant(2,
            Subject.of("agent", "alice"), Set.of("t1", "t2"), Instant.now()));

        assertThat(index.findByEntity("node-1", "t1")).hasSize(1);
        assertThat(index.findByEntity("node-1", "t2")).isEmpty();
    }

    @Test
    void doesNotAffectOtherOwners() {
        index.addAnnotation(annotation("node-1", "mem-1", "alice", "t1"));
        index.addAnnotation(annotation("node-1", "mem-2", "bob", "t1"));

        observer.onErased(new MemoryEntityErased.ByEntity("t1", 1,
            Subject.of("agent", "alice"), Instant.now()));

        assertThat(index.findByEntity("node-1", "t1")).hasSize(1);
        assertThat(index.findByEntity("node-1", "t1").getFirst().ownerSubjectId()).isEqualTo("bob");
    }
}
