package io.casehub.neocortex.memory.relationship.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.experience.*;
import io.casehub.neocortex.memory.relationship.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipProcessorTest {

    private StubStore store;
    private List<RelationshipRecorded> recorded;
    private RelationshipProcessor processor;

    @BeforeEach
    void setUp() {
        store = new StubStore();
        recorded = new ArrayList<>();
        processor = new RelationshipProcessor(store, recorded::add);
    }

    @Test void storesRelationshipWhenTargetAgentPresent() {
        var action = new Action("a1", "t1", "c1", "turn-1", null, "reviewed code", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), "code-review");
        processor.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));
        assertEquals(1, store.stored.size());
        assertEquals(RelationshipEvents.DOMAIN, store.stored.getFirst().domain());
        assertEquals("b1", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.OTHER_AGENT));
        assertEquals("action", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.SOURCE_EVENT_TYPE));
        assertEquals("neutral", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.QUALITY_SIGNAL));
    }

    @Test void firesRelationshipRecordedCallback() {
        var action = new Action("a1", "t1", null, null, null, "did work", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), null);
        processor.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));
        assertEquals(1, recorded.size());
        assertEquals("b1", recorded.getFirst().event().otherAgentId());
        assertEquals("mem-0", recorded.getFirst().memoryId());
    }

    @Test void skipsWhenNoTargetAgent() {
        var obs = new Observation("a1", "t1", null, null, null, "saw something", null, Map.of(), "subj");
        processor.onExperienceRecorded(new ExperienceRecorded(obs, "exp-1"));
        assertTrue(store.stored.isEmpty());
        assertTrue(recorded.isEmpty());
    }

    @Test void skipsWhenTargetAgentBlank() {
        var action = new Action("a1", "t1", null, null, null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "  "), null);
        processor.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));
        assertTrue(store.stored.isEmpty());
    }

    @Test void skipsSelfReferential() {
        var action = new Action("a1", "t1", null, null, null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "a1"), null);
        processor.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));
        assertTrue(store.stored.isEmpty());
    }

    @Test void mapsObservationToObservationEventType() {
        var obs = new Observation("a1", "t1", null, null, null, "saw agent b1", null,
                                  Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), "b1 status");
        processor.onExperienceRecorded(new ExperienceRecorded(obs, "exp-1"));
        assertEquals("observation", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.SOURCE_EVENT_TYPE));
    }

    @Test void mapsOutcomeToOutcomeEventType() {
        var outcome = new Outcome("a1", "t1", null, null, null, "review done", null,
                                  Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), "completed", "code-review");
        processor.onExperienceRecorded(new ExperienceRecorded(outcome, "exp-1"));
        assertEquals("outcome", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.SOURCE_EVENT_TYPE));
    }

    @Test void propagatesSecurityException() {
        store.throwOnStore = new SecurityException("forbidden");
        var action = new Action("a1", "t1", null, null, null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), null);
        assertThrows(SecurityException.class, () ->
            processor.onExperienceRecorded(new ExperienceRecorded(action, "exp-1")));
    }

    @Test void catchesNonSecurityStoreFailure() {
        store.throwOnStore = new RuntimeException("db down");
        var action = new Action("a1", "t1", null, null, null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), null);
        processor.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));
        assertTrue(recorded.isEmpty());
    }

    @Test void preservesTurnIdFromExperience() {
        var action = new Action("a1", "t1", null, "turn-99", null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), null);
        processor.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));
        assertEquals("turn-99", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.TURN_ID));
    }

    static class StubStore implements CaseMemoryStore {
        final List<MemoryInput> stored = new ArrayList<>();
        int counter = 0;
        RuntimeException throwOnStore;
        @Override public String store(MemoryInput input) {
            if (throwOnStore != null) throw throwOnStore;
            stored.add(input);
            return "mem-" + counter++;
        }
        @Override public List<io.casehub.neocortex.memory.Memory> query(io.casehub.neocortex.memory.MemoryQuery q) { return List.of(); }
        @Override public int erase(io.casehub.neocortex.memory.EraseRequest r) { return 0; }
    }
}
