package io.casehub.neocortex.memory.relationship.runtime;

import io.casehub.neocortex.memory.CaseMemoryStore;
import io.casehub.neocortex.memory.MemoryInput;
import io.casehub.neocortex.memory.experience.*;
import io.casehub.neocortex.memory.relationship.*;
import jakarta.enterprise.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipObserverTest {

    private StubStore store;
    private StubEvent<RelationshipRecorded> eventSink;
    private RelationshipObserver observer;

    @BeforeEach
    void setUp() {
        store = new StubStore();
        eventSink = new StubEvent<>();
        observer = new RelationshipObserver(store, eventSink);
    }

    @Test
    void storesRelationshipWhenTargetAgentPresent() {
        var action = new Action("a1", "t1", "c1", "turn-1", null, "reviewed code", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), "code-review");
        observer.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));

        assertEquals(1, store.stored.size());
        assertEquals(RelationshipEvents.DOMAIN, store.stored.getFirst().domain());
        assertEquals("b1", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.OTHER_AGENT));
        assertEquals("action", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.SOURCE_EVENT_TYPE));
        assertEquals("neutral", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.QUALITY_SIGNAL));
    }

    @Test
    void firesRelationshipRecordedEvent() {
        var action = new Action("a1", "t1", null, null, null, "did work", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), null);
        observer.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));

        assertEquals(1, eventSink.fired.size());
        assertEquals("b1", eventSink.fired.getFirst().event().otherAgentId());
        assertEquals("mem-0", eventSink.fired.getFirst().memoryId());
    }

    @Test
    void skipsWhenNoTargetAgent() {
        var obs = new Observation("a1", "t1", null, null, null, "saw something", null, Map.of(), "subj");
        observer.onExperienceRecorded(new ExperienceRecorded(obs, "exp-1"));

        assertTrue(store.stored.isEmpty());
        assertTrue(eventSink.fired.isEmpty());
    }

    @Test
    void skipsWhenTargetAgentBlank() {
        var action = new Action("a1", "t1", null, null, null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "  "), null);
        observer.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));

        assertTrue(store.stored.isEmpty());
    }

    @Test
    void skipsSelfReferential() {
        var action = new Action("a1", "t1", null, null, null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "a1"), null);
        observer.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));

        assertTrue(store.stored.isEmpty());
    }

    @Test
    void mapsObservationToObservationEventType() {
        var obs = new Observation("a1", "t1", null, null, null, "saw agent b1", null,
                                  Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), "b1 status");
        observer.onExperienceRecorded(new ExperienceRecorded(obs, "exp-1"));

        assertEquals("observation", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.SOURCE_EVENT_TYPE));
    }

    @Test
    void mapsOutcomeToOutcomeEventType() {
        var outcome = new Outcome("a1", "t1", null, null, null, "review done", null,
                                  Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), "completed", "code-review");
        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "exp-1"));

        assertEquals("outcome", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.SOURCE_EVENT_TYPE));
    }

    @Test
    void propagatesSecurityException() {
        store.throwOnStore = new SecurityException("forbidden");
        var action = new Action("a1", "t1", null, null, null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), null);
        assertThrows(SecurityException.class, () ->
            observer.onExperienceRecorded(new ExperienceRecorded(action, "exp-1")));
    }

    @Test
    void catchesNonSecurityStoreFailure() {
        store.throwOnStore = new RuntimeException("db down");
        var action = new Action("a1", "t1", null, null, null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), null);
        observer.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));
        assertTrue(eventSink.fired.isEmpty());
    }

    @Test
    void preservesTurnIdFromExperience() {
        var action = new Action("a1", "t1", null, "turn-99", null, "desc", null,
                                Map.of(ExperienceAttributeKeys.TARGET_AGENT, "b1"), null);
        observer.onExperienceRecorded(new ExperienceRecorded(action, "exp-1"));

        assertEquals("turn-99", store.stored.getFirst().attributes().get(RelationshipAttributeKeys.TURN_ID));
    }

    // --- Stubs ---

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

    static class StubEvent<T> implements Event<T> {
        final List<T> fired = new ArrayList<>();
        @Override public void fire(T event) { fired.add(event); }
        @Override public <U extends T> CompletionStage<U> fireAsync(U event) { throw new UnsupportedOperationException(); }
        @Override public <U extends T> CompletionStage<U> fireAsync(U event, jakarta.enterprise.event.NotificationOptions options) { throw new UnsupportedOperationException(); }
        @Override public Event<T> select(java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U extends T> Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U extends T> Event<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    }
}
