package io.casehub.neocortex.mindmap.intelligence;

import io.casehub.neocortex.memory.experience.ExperienceEvent;
import io.casehub.neocortex.memory.experience.ExperienceRecorder;
import io.casehub.neocortex.memory.experience.ExperienceStoreResult;
import io.casehub.neocortex.mindmap.inmem.InMemoryMindMapStore;
import io.casehub.platform.api.identity.PrincipalId;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class CheckInServiceSubThoughtTest {

    private static final String TENANT = "t1";
    private static final PrincipalId PRINCIPAL = PrincipalId.agent("agent-1");

    private InMemoryMindMapStore store;
    private StubExperienceRecorder recorder;
    private StubExtractionEvent extractionEvent;
    private CheckInService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryMindMapStore();
        recorder = new StubExperienceRecorder();
        extractionEvent = new StubExtractionEvent();
        service = new CheckInService(store, recorder, extractionEvent);
    }

    @Test
    void checkInWithPrincipalCreatesExperienceMemory() {
        var request = CheckInRequest.of("Lunch with Sarah", "La Trattoria")
            .withDate(Instant.parse("2026-10-07T12:00:00Z"))
            .withNotes("Great pasta, Sarah seemed distracted");

        CheckInResult result = service.checkIn(request, TENANT, PRINCIPAL);

        assertThat(result.memoryId()).isEqualTo("mem-0");
        assertThat(recorder.recorded).hasSize(1);
    }

    @Test
    void checkInWithPrincipalFiresExtractionEvent() {
        var request = CheckInRequest.of("Lunch with Sarah", "La Trattoria")
            .withNotes("Sarah seemed distracted");

        service.checkIn(request, TENANT, PRINCIPAL);

        assertThat(extractionEvent.fired).hasSize(1);
        var event = extractionEvent.fired.getFirst();
        assertThat(event.memoryId()).isEqualTo("mem-0");
        assertThat(event.tenantId()).isEqualTo(TENANT);
        assertThat(event.experienceText()).contains("Lunch with Sarah");
        assertThat(event.experienceText()).contains("La Trattoria");
    }

    @Test
    void checkInWithoutPrincipalSkipsMemoryAndEvent() {
        var request = CheckInRequest.of("Lunch", "Ondine");

        CheckInResult result = service.checkIn(request, TENANT);

        assertThat(result.memoryId()).isNull();
        assertThat(recorder.recorded).isEmpty();
        assertThat(extractionEvent.fired).isEmpty();
    }

    @Test
    void checkInStillCreatesGraphNodesWithPrincipal() {
        var request = CheckInRequest.of("Dinner at Ondine", "Ondine")
            .withParticipants(List.of(new CheckInRequest.Participant("Tom")));

        CheckInResult result = service.checkIn(request, TENANT, PRINCIPAL);

        assertThat(result.activityNodeId()).isNotNull();
        assertThat(result.placeNodeId()).isNotNull();
        assertThat(result.participantEdgeIds()).hasSize(1);
    }

    static class StubExperienceRecorder implements ExperienceRecorder {
        final List<ExperienceEvent> recorded = new ArrayList<>();
        private int counter = 0;

        @Override
        public String record(ExperienceEvent event) {
            recorded.add(event);
            return "mem-" + counter++;
        }

        @Override
        public ExperienceStoreResult recordAll(List<ExperienceEvent> events) {
            throw new UnsupportedOperationException();
        }
    }

    static class StubExtractionEvent implements Event<SubThoughtExtractionRequested> {
        final List<SubThoughtExtractionRequested> fired = new ArrayList<>();

        @Override
        public void fire(SubThoughtExtractionRequested event) {
            fired.add(event);
        }

        @Override
        public <U extends SubThoughtExtractionRequested> CompletionStage<U> fireAsync(U event) {
            fired.add(event);
            return java.util.concurrent.CompletableFuture.completedFuture(event);
        }

        @Override
        public <U extends SubThoughtExtractionRequested> CompletionStage<U> fireAsync(U event, NotificationOptions options) {
            return fireAsync(event);
        }

        @Override
        public Event<SubThoughtExtractionRequested> select(java.lang.annotation.Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends SubThoughtExtractionRequested> Event<U> select(Class<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <U extends SubThoughtExtractionRequested> Event<U> select(jakarta.enterprise.util.TypeLiteral<U> subtype, java.lang.annotation.Annotation... qualifiers) {
            throw new UnsupportedOperationException();
        }
    }
}
