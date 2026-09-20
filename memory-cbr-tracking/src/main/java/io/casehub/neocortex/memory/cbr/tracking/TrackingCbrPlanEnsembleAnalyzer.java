package io.casehub.neocortex.memory.cbr.tracking;

import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.CbrEnsembleRecorded;
import io.casehub.neocortex.memory.cbr.CbrMatch;
import io.casehub.neocortex.memory.cbr.EnsemblePlan;
import io.casehub.neocortex.memory.cbr.EnsembleTrace;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.CbrPlanRecord;
import io.casehub.neocortex.memory.cbr.CbrPlanEnsembleAnalyzer;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

@Decorator
@Priority(50)
@IfBuildProperty(name = "casehub.cbr.ensemble-tracking.enabled", stringValue = "true")
public class TrackingCbrPlanEnsembleAnalyzer implements CbrPlanEnsembleAnalyzer {

    private static final Logger LOG = Logger.getLogger(TrackingCbrPlanEnsembleAnalyzer.class);

    private final CbrPlanEnsembleAnalyzer       delegate;
    private final Consumer<CbrEnsembleRecorded> eventSink;

    @Inject
    TrackingCbrPlanEnsembleAnalyzer(@Delegate @Any CbrPlanEnsembleAnalyzer delegate,
                                    Event<CbrEnsembleRecorded> recordedEvent) {
        this(delegate, recordedEvent::fire);
    }

    TrackingCbrPlanEnsembleAnalyzer(CbrPlanEnsembleAnalyzer delegate,
                                    Consumer<CbrEnsembleRecorded> eventSink) {
        this.delegate = delegate;
        this.eventSink = eventSink;
    }

    @Override
    public EnsemblePlan analyze(String caseType,
                                List<CbrMatch<CbrPlanRecord>> scoredCases,
                                List<AdaptedPlan> adaptedPlans,
                                Map<String, FeatureValue> currentFeatures) {
        EnsemblePlan result = delegate.analyze(caseType, scoredCases, adaptedPlans, currentFeatures);
        try {
            var trace = new EnsembleTrace(
                    UUID.randomUUID().toString(),
                    null,
                    caseType,
                    result.sourceCaseIds(),
                    result.stepAnalysis(),
                    result.synthesizedPlan().steps(),
                    result.inputPlanCount(),
                    result.ensembleConfidence(),
                    currentFeatures,
                    Instant.now()
            );
            eventSink.accept(new CbrEnsembleRecorded(trace));
        } catch (Exception e) {
            LOG.warn("CBR ensemble tracking failed — returning result unchanged", e);
        }
        return result;
    }
}
