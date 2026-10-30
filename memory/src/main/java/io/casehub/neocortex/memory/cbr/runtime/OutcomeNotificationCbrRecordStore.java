package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.CbrOutcome;
import io.casehub.neocortex.memory.cbr.CbrOutcomeRecorded;
import io.casehub.neocortex.memory.cbr.CbrRecordStore;
import io.casehub.neocortex.memory.cbr.DelegatingCbrRecordStore;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

import java.time.Clock;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

@Decorator
@Priority(43)
public class OutcomeNotificationCbrRecordStore extends DelegatingCbrRecordStore {

    private static final Logger LOG = Logger.getLogger(
        OutcomeNotificationCbrRecordStore.class.getName());

    private final Event<CbrOutcomeRecorded> event;
    private final Clock clock;

    @Inject
    public OutcomeNotificationCbrRecordStore(
            @Delegate @Any CbrRecordStore delegate,
            Event<CbrOutcomeRecorded> event) {
        this(delegate, event, Clock.systemUTC());
    }

    OutcomeNotificationCbrRecordStore(CbrRecordStore delegate,
                                      Event<CbrOutcomeRecorded> event,
                                      Clock clock) {
        super(delegate);
        this.event = event;
        this.clock = clock;
    }

    @Override
    public void recordOutcome(String caseId, CbrOutcome outcome, String tenantId) {
        delegate.recordOutcome(caseId, outcome, tenantId);
        try {
            event.fire(new CbrOutcomeRecorded(tenantId, caseId, outcome,
                Instant.now(clock)));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "CDI event notification failed for outcome on " + caseId, e);
        }
    }
}
