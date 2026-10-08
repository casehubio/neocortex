package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.memory.experience.Action;
import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.casehub.neocortex.memory.experience.FormativeExperience;
import io.casehub.neocortex.memory.experience.Observation;
import io.casehub.neocortex.memory.experience.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubThoughtExtractionObserverTest {

    private SubThoughtExtractionObserver observer;
    private RecordingEvent<SubThoughtExtractionRequested> event;

    @BeforeEach
    void setUp() {
        observer = new SubThoughtExtractionObserver();
        event = new RecordingEvent<>();
        observer.extractionEvent = event;
    }

    @Test
    void firesAsyncForObservation() {
        var obs = new Observation("agent1", "t1", null, null, Instant.now(),
                "Sarah seemed happy", null, Map.of(), "world");
        observer.onExperienceRecorded(new ExperienceRecorded(obs, "mem-1"));

        assertThat(event.asyncFired).hasSize(1);
        assertThat(event.asyncFired.getFirst().memoryId()).isEqualTo("mem-1");
        assertThat(event.asyncFired.getFirst().experienceText()).isEqualTo("Sarah seemed happy");
    }

    @Test
    void firesAsyncForFormativeExperience() {
        var fe = new FormativeExperience("agent1", "t1", null, null, Instant.now(),
                "Childhood memory", null, Map.of(), "cat-1", List.of("social_threat"),
                1.0, null, null, null, null, null);
        observer.onExperienceRecorded(new ExperienceRecorded(fe, "mem-2"));

        assertThat(event.asyncFired).hasSize(1);
        assertThat(event.asyncFired.getFirst().memoryId()).isEqualTo("mem-2");
    }

    @Test
    void doesNotFireForAction() {
        var action = new Action("agent1", "t1", null, null, Instant.now(),
                "Did something", null, Map.of(), null);
        observer.onExperienceRecorded(new ExperienceRecorded(action, "mem-3"));

        assertThat(event.asyncFired).isEmpty();
    }

    @Test
    void doesNotFireForOutcome() {
        var outcome = new Outcome("agent1", "t1", null, null, Instant.now(),
                "It worked", null, Map.of(), "success", null);
        observer.onExperienceRecorded(new ExperienceRecorded(outcome, "mem-4"));

        assertThat(event.asyncFired).isEmpty();
    }

    @Test
    void rateLimitsSameAgentWithinCooldown() {
        var obs1 = new Observation("agent1", "t1", null, null, Instant.now(),
                "First observation", null, Map.of(), "world");
        var obs2 = new Observation("agent1", "t1", null, null, Instant.now(),
                "Second observation", null, Map.of(), "world");

        observer.onExperienceRecorded(new ExperienceRecorded(obs1, "mem-1"));
        observer.onExperienceRecorded(new ExperienceRecorded(obs2, "mem-2"));

        assertThat(event.asyncFired).hasSize(1);
        assertThat(event.asyncFired.getFirst().memoryId()).isEqualTo("mem-1");
    }

    @Test
    void differentAgentsNotRateLimited() {
        var obs1 = new Observation("agent1", "t1", null, null, Instant.now(),
                "First agent", null, Map.of(), "world");
        var obs2 = new Observation("agent2", "t1", null, null, Instant.now(),
                "Second agent", null, Map.of(), "world");

        observer.onExperienceRecorded(new ExperienceRecorded(obs1, "mem-1"));
        observer.onExperienceRecorded(new ExperienceRecorded(obs2, "mem-2"));

        assertThat(event.asyncFired).hasSize(2);
    }

    private static class RecordingEvent<T> implements jakarta.enterprise.event.Event<T> {
        final List<T> asyncFired = new ArrayList<>();

        @Override
        public void fire(T event) {}

        @Override
        public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(U event) {
            asyncFired.add(event);
            return java.util.concurrent.CompletableFuture.completedFuture(event);
        }

        @Override
        public <U extends T> java.util.concurrent.CompletionStage<U> fireAsync(U event, jakarta.enterprise.event.NotificationOptions options) {
            asyncFired.add(event);
            return java.util.concurrent.CompletableFuture.completedFuture(event);
        }

        @Override
        public jakarta.enterprise.event.Event<T> select(java.lang.annotation.Annotation... qualifiers) { return this; }

        @Override
        public <U extends T> jakarta.enterprise.event.Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }

        @Override
        public <U extends T> jakarta.enterprise.event.Event<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    }
}
