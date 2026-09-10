package io.casehub.neocortex.memory.cbr.tracking;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.memory.cbr.AdaptationAction;
import io.casehub.neocortex.memory.cbr.AdaptedPlan;
import io.casehub.neocortex.memory.cbr.AdaptedStep;
import io.casehub.neocortex.memory.cbr.CbrAdaptationRecorded;
import io.casehub.neocortex.memory.cbr.FeatureValue;
import io.casehub.neocortex.memory.cbr.PlanAdapter;
import io.casehub.neocortex.memory.cbr.ResolvedCase;
import io.casehub.neocortex.memory.cbr.ResolutionStep;
import io.casehub.neocortex.memory.cbr.ScoredCbrCase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingPlanAdapterTest {

    private ScoredCbrCase<ResolvedCase> scored() {
        var trace = new ResolutionStep("b1", "cap1", "w1", "SUCCESS", 0, Map.of(), null);
        var plan = new ResolvedCase("problem", "solution", "WIN", Confidence.unknown(0.9),
                                    Map.of("f", FeatureValue.string("v")), List.of(trace), null, null);
        return new ScoredCbrCase<>(plan, "c1", "test-type", 0.85);
    }

    private PlanAdapter noOpDelegate() {
        return (caseType, retrieved, features) -> new AdaptedPlan(
                retrieved.cbrCase().resolutionStep().stream()
                         .map(t -> new AdaptedStep(t.bindingName(), t.capabilityName(),
                                                   t.workerName(), t.stepOutcome(), t.priority(), t.parameters(),
                                                   AdaptationAction.RETAINED, null))
                         .toList());
    }

    @Test
    void firesEventAfterAdaptation() {
        var eventRef  = new AtomicReference<CbrAdaptationRecorded>();
        var decorator = new TrackingPlanAdapter(noOpDelegate(), eventRef::set);

        Map<String, FeatureValue> features = Map.of("f", FeatureValue.string("q"));
        decorator.adapt("typeA", scored(), features);

        assertThat(eventRef.get()).isNotNull();
        assertThat(eventRef.get().trace().traceId()).isNotBlank();
        assertThat(eventRef.get().trace().caseType()).isEqualTo("typeA");
        assertThat(eventRef.get().trace().steps()).hasSize(1);
        assertThat(eventRef.get().trace().timestamp()).isNotNull();
    }

    @Test
    void traceContainsCorrectFields() {
        var                       eventRef  = new AtomicReference<CbrAdaptationRecorded>();
        var                       decorator = new TrackingPlanAdapter(noOpDelegate(), eventRef::set);
        Map<String, FeatureValue> features  = Map.of("f", FeatureValue.string("q"));

        decorator.adapt("typeB", scored(), features);
        var trace = eventRef.get().trace();

        assertThat(trace.caseType()).isEqualTo("typeB");
        assertThat(trace.sourceCaseId()).isEqualTo("c1");
        assertThat(trace.sourceScore()).isEqualTo(0.85);
        assertThat(trace.currentFeatures()).containsKey("f");
        assertThat(trace.steps().getFirst().action()).isEqualTo(AdaptationAction.RETAINED);
    }

    @Test
    void trackingFailureDoesNotBreakAdaptation() {
        var decorator = new TrackingPlanAdapter(noOpDelegate(), e -> {
            throw new RuntimeException("event sink failure");
        });

        var result = decorator.adapt("typeA", scored(), Map.of());

        assertThat(result.steps()).hasSize(1);
        assertThat(result.steps().getFirst().bindingName()).isEqualTo("b1");
    }

    @Test
    void firesForNoOpAdapter() {
        var eventRef  = new AtomicReference<CbrAdaptationRecorded>();
        var decorator = new TrackingPlanAdapter(noOpDelegate(), eventRef::set);

        var emptyPlan = new ResolvedCase("problem", "solution", null, null,
                                         Map.of(), List.of(), null, null);
        var scored = new ScoredCbrCase<>(emptyPlan, "c2", "test-type", 0.3);
        decorator.adapt("typeA", scored, Map.of());

        assertThat(eventRef.get()).isNotNull();
        assertThat(eventRef.get().trace().steps()).isEmpty();
        assertThat(eventRef.get().trace().sourceCaseId()).isEqualTo("c2");
    }
}
