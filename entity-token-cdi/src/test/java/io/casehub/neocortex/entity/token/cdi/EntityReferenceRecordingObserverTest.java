package io.casehub.neocortex.entity.token.cdi;

import io.casehub.neocortex.entity.token.InMemoryEntityReferenceIndex;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.Observation;
import io.casehub.neocortex.mindmap.NodeInput;
import io.casehub.neocortex.mindmap.SubgraphInput;
import io.casehub.neocortex.mindmap.SubgraphTypes;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntityReferenceRecordingObserverTest {

    private static final String TENANT = "test-tenant";

    private InMemoryMindMapStore store;
    private InMemoryEntityReferenceIndex referenceIndex;
    private SurfaceFormTrieManager trieManager;
    private EntityReferenceRecordingObserver observer;
    private String janeNodeId;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        referenceIndex = new InMemoryEntityReferenceIndex();
        var cache = new EntityIdentityCache(store);
        trieManager = new SurfaceFormTrieManager(cache);
        observer = new EntityReferenceRecordingObserver(referenceIndex, trieManager);

        String sgId = store.createSubgraph(
            new SubgraphInput("People", SubgraphTypes.PERSON, null), TENANT);
        janeNodeId = store.addNode(
            NodeInput.of("Jane Doe", sgId)
                .withProperties(Map.of("canonicalFirst", "Jane")),
            TENANT);
    }

    @AfterEach
    void tearDown() {
        trieManager.shutdown();
    }

    @Test
    void recordsReferencesOnExperienceRecorded() {
        var event = new Observation("agent1", TENANT, "case1", "turn1",
            Instant.now(), "Jane walked to the park", 1.0, Map.of(), "Jane Doe");
        observer.onExperienceRecorded(new ExperienceRecorded(event, "mem-1"));

        assertThat(referenceIndex.findSourcesByEntity(janeNodeId, TENANT))
            .contains("mem-1");
        assertThat(referenceIndex.findEntitiesBySource("memory", "mem-1", TENANT))
            .contains(janeNodeId);
    }

    @Test
    void doesNotRecordWhenNoEntitiesFound() {
        var event = new Observation("agent1", TENANT, "case1", "turn1",
            Instant.now(), "Diego walked home", 1.0, Map.of(), "Diego");
        observer.onExperienceRecorded(new ExperienceRecorded(event, "mem-2"));

        assertThat(referenceIndex.findEntitiesBySource("memory", "mem-2", TENANT))
            .isEmpty();
    }

    @Test
    void noReferencesRecordedWhenNoMatch() {
        var event = new Observation("agent1", TENANT, "case1", "turn1",
            Instant.now(), "nothing matches here", 1.0, Map.of(), "nobody");
        observer.onExperienceRecorded(new ExperienceRecorded(event, "mem-3"));
        assertThat(referenceIndex.entityMentionCounts(TENANT)).isEmpty();
    }
}
