package io.casehub.memory.runtime;

import io.casehub.neocortex.memory.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class CaseEnrichmentPipelineTest {

    private static final MemoryDomain DOMAIN = new MemoryDomain("test");

    @Test void store_appliesEnrichmentBeforeDelegating() {
        var delegate = new RecordingStore();
        var step = new TestStep("enriched", "value");
        var pipeline = new CaseEnrichmentPipeline(delegate, List.of(step));
        var input = new MemoryInput("e1", DOMAIN, "t1", null, "text", Map.of(), null, null, null, null);
        pipeline.store(input);
        assertThat(delegate.lastInput.attributes()).containsEntry("enriched", "value");
    }

    @Test void store_appliesStepsInPriorityOrder() {
        var delegate = new RecordingStore();
        var stepB = new TestStep("order", "B") { @Override public int priority() { return 10; } };
        var stepA = new TestStep("order", "A") { @Override public int priority() { return 1; } };
        var pipeline = new CaseEnrichmentPipeline(delegate, List.of(stepB, stepA));
        pipeline.store(new MemoryInput("e1", DOMAIN, "t1", null, "text", Map.of(), null, null, null, null));
        assertThat(delegate.lastInput.attributes().get("order")).isEqualTo("B");
    }

    @Test void store_optionalStepFailureDoesNotPreventStore() {
        var delegate = new RecordingStore();
        var failingStep = new CaseEnrichmentStep() {
            @Override public boolean appliesTo(MemoryInput i) { return true; }
            @Override public MemoryInput enrich(MemoryInput i) { throw new RuntimeException("boom"); }
        };
        var pipeline = new CaseEnrichmentPipeline(delegate, List.of(failingStep));
        pipeline.store(new MemoryInput("e1", DOMAIN, "t1", null, "text", Map.of(), null, null, null, null));
        assertThat(delegate.lastInput).isNotNull();
    }

    @Test void store_requiredStepFailurePreventsStore() {
        var delegate = new RecordingStore();
        var requiredStep = new CaseEnrichmentStep() {
            @Override public boolean appliesTo(MemoryInput i) { return true; }
            @Override public MemoryInput enrich(MemoryInput i) { throw new RuntimeException("required failure"); }
            @Override public boolean required() { return true; }
        };
        var pipeline = new CaseEnrichmentPipeline(delegate, List.of(requiredStep));
        assertThatThrownBy(() -> pipeline.store(
            new MemoryInput("e1", DOMAIN, "t1", null, "text", Map.of(), null, null, null, null)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("required failure");
        assertThat(delegate.lastInput).isNull();
    }

    @Test void store_stepThatDoesNotApplyIsSkipped() {
        var delegate = new RecordingStore();
        var step = new CaseEnrichmentStep() {
            @Override public boolean appliesTo(MemoryInput i) { return false; }
            @Override public MemoryInput enrich(MemoryInput i) { return i.withAttribute("applied", "true"); }
        };
        var pipeline = new CaseEnrichmentPipeline(delegate, List.of(step));
        pipeline.store(new MemoryInput("e1", DOMAIN, "t1", null, "text", Map.of(), null, null, null, null));
        assertThat(delegate.lastInput.attributes()).doesNotContainKey("applied");
    }

    @Test void query_delegatesDirectly() {
        var delegate = new RecordingStore();
        var pipeline = new CaseEnrichmentPipeline(delegate, List.of());
        pipeline.query(new MemoryQuery(List.of("e1"), DOMAIN, "t1", null, null, 10, null, MemoryOrder.CHRONOLOGICAL));
        assertThat(delegate.queryCalled).isTrue();
    }

    static class RecordingStore implements CaseMemoryStore {
        MemoryInput lastInput;
        boolean queryCalled;
        @Override public String store(MemoryInput i) { lastInput = i; return "id"; }
        @Override public List<Memory> query(MemoryQuery q) { queryCalled = true; return List.of(); }
        @Override public int erase(EraseRequest r) { return 0; }
    }

    static class TestStep implements CaseEnrichmentStep {
        private final String key, value;
        TestStep(String key, String value) { this.key = key; this.value = value; }
        @Override public boolean appliesTo(MemoryInput i) { return true; }
        @Override public MemoryInput enrich(MemoryInput i) { return i.withAttribute(key, value); }
    }
}
