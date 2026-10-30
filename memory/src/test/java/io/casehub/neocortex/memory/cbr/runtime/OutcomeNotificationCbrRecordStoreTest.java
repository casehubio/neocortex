package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrOutcomeRecorded;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.NotificationOptions;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

class OutcomeNotificationCbrRecordStoreTest {

    private static final Instant FIXED = Instant.parse("2026-10-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED, ZoneOffset.UTC);

    private StubCbrStore stub;
    private List<CbrOutcomeRecorded> firedEvents;
    private OutcomeNotificationCbrRecordStore decorator;

    @BeforeEach
    void setUp() {
        stub = new StubCbrStore();
        firedEvents = new ArrayList<>();
        decorator = new OutcomeNotificationCbrRecordStore(stub, new CapturingEvent(firedEvents), CLOCK);
    }

    @Test
    void firesEventAfterRecordOutcome() {
        var outcome = CbrOutcome.of(0.8, "CONVERGED, 1 attractors", Instant.now());

        decorator.recordOutcome("case-1", outcome, "tenant-1");

        assertThat(stub.recordedCaseIds).containsExactly("case-1");
        assertThat(firedEvents).hasSize(1);
        assertThat(firedEvents.getFirst().tenantId()).isEqualTo("tenant-1");
        assertThat(firedEvents.getFirst().caseId()).isEqualTo("case-1");
        assertThat(firedEvents.getFirst().outcome()).isSameAs(outcome);
        assertThat(firedEvents.getFirst().recordedAt()).isEqualTo(FIXED);
    }

    @Test
    void eventFailureDoesNotPropagate() {
        var outcome = CbrOutcome.of(0.5, "test", Instant.now());
        var failingEvent = new CapturingEvent(firedEvents) {
            @Override public void fire(CbrOutcomeRecorded event) {
                throw new RuntimeException("event bus down");
            }
        };
        var failDecorator = new OutcomeNotificationCbrRecordStore(stub, failingEvent, CLOCK);

        failDecorator.recordOutcome("case-1", outcome, "tenant-1");

        assertThat(stub.recordedCaseIds).containsExactly("case-1");
    }

    @Test
    void delegateFailurePropagates_noEventFired() {
        var failingStore = new StubCbrStore() {
            @Override public void recordOutcome(String caseId, CbrOutcome outcome, String tenantId) {
                throw new RuntimeException("store down");
            }
        };
        var failDecorator = new OutcomeNotificationCbrRecordStore(failingStore, new CapturingEvent(firedEvents), CLOCK);
        var outcome = CbrOutcome.of(0.5, "test", Instant.now());

        try {
            failDecorator.recordOutcome("case-1", outcome, "tenant-1");
        } catch (RuntimeException ignored) {}

        assertThat(firedEvents).isEmpty();
    }

    static class StubCbrStore extends NoOpCbrRecordStore {
        final List<String> recordedCaseIds = new ArrayList<>();

        @Override
        public void recordOutcome(String caseId, CbrOutcome outcome, String tenantId) {
            recordedCaseIds.add(caseId);
        }
    }

    static class CapturingEvent implements Event<CbrOutcomeRecorded> {
        private final List<CbrOutcomeRecorded> events;

        CapturingEvent(List<CbrOutcomeRecorded> events) { this.events = events; }

        @Override public void fire(CbrOutcomeRecorded event) { events.add(event); }
        @Override public <U extends CbrOutcomeRecorded> CompletionStage<U> fireAsync(U event) { return CompletableFuture.completedFuture(event); }
        @Override public <U extends CbrOutcomeRecorded> CompletionStage<U> fireAsync(U event, NotificationOptions options) { return CompletableFuture.completedFuture(event); }
        @Override public Event<CbrOutcomeRecorded> select(Annotation... qualifiers) { return this; }
        @Override public <U extends CbrOutcomeRecorded> Event<U> select(Class<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
        @Override public <U extends CbrOutcomeRecorded> Event<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { throw new UnsupportedOperationException(); }
    }
}
