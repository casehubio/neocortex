package io.casehub.neocortex.memory.cbr.tracking;

import io.casehub.neocortex.memory.cbr.CbrAdaptationTrace;
import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.CbrAdaptationRecorded;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrPlanAdapter;
import io.casehub.neocortex.memory.cbr.CbrPlanRecord;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Decorator
@Priority(50)
@IfBuildProperty(name = "casehub.cbr.adaptation-tracking.enabled", stringValue = "true")
public class TrackingCbrPlanAdapter implements CbrPlanAdapter {

    private static final Logger LOG = Logger.getLogger(TrackingCbrPlanAdapter.class);

    private final CbrPlanAdapter                  delegate;
    private final Consumer<CbrAdaptationRecorded> eventSink;

    @Inject
    TrackingCbrPlanAdapter(@Delegate @Any CbrPlanAdapter delegate,
                           Event<CbrAdaptationRecorded> recordedEvent) {
        this(delegate, recordedEvent::fire);
    }

    TrackingCbrPlanAdapter(CbrPlanAdapter delegate,
                           Consumer<CbrAdaptationRecorded> eventSink) {
        this.delegate = delegate;
        this.eventSink = eventSink;
    }

    @Override
    public AdaptedPlan adapt(String caseType, CbrMatch<CbrPlanRecord> retrieved,
                             Map<String, FeatureValue> currentFeatures) {
        AdaptedPlan result = delegate.adapt(caseType, retrieved, currentFeatures);
        try {
            var trace = new CbrAdaptationTrace(
                    UUID.randomUUID().toString(),
                    null,
                    caseType,
                    retrieved.caseId(),
                    retrieved.score(),
                    result.steps(),
                    currentFeatures,
                    Instant.now()
            );
            eventSink.accept(new CbrAdaptationRecorded(trace));
        } catch (Exception e) {
            LOG.warn("CBR adaptation tracking failed — returning result unchanged", e);
        }
        return result;
    }
}
