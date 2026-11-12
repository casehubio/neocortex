package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.experience.FormativeScoring;
import io.casehub.neocortex.memory.experience.GraduationContext;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class DefaultGraduationScorerCapacityTest {

    private static final String TENANT = "t1";
    private static final String AGENT  = "agent-1";

    @Test
    void capacityModulatesGraduationScore() {
        var scorer  = scorerWithCapacity(0.55);
        var context = new GraduationContext(5, 0, TENANT, AGENT);

        double score = scorer.score(memoryWithConfidence(0.8, AGENT), context);

        assertThat(score).isCloseTo(0.8 * 0.55, offset(0.01));
    }

    @Test
    void fullCapacityPreservesRawScore() {
        var scorer  = scorerWithCapacity(1.0);
        var context = new GraduationContext(5, 0, TENANT, AGENT);

        double score = scorer.score(memoryWithConfidence(0.8, AGENT), context);

        assertThat(score).isCloseTo(0.8, offset(0.01));
    }

    @Test
    void nullAgentIdDefaultsToNoModulation() {
        var scorer  = scorerWithCapacity(0.55);
        var context = new GraduationContext(5, 0, TENANT, null);

        double score = scorer.score(memoryWithConfidence(0.8, null), context);

        assertThat(score).isCloseTo(0.8, offset(0.01));
    }

    @Test
    void nullRegistryDefaultsToNoModulation() {
        var scorer  = new DefaultGraduationScorer(3, null);
        var context = new GraduationContext(5, 0, TENANT, AGENT);

        double score = scorer.score(memoryWithConfidence(0.8, AGENT), context);

        assertThat(score).isCloseTo(0.8, offset(0.01));
    }

    @Test
    void formativeMemoriesExemptFromCapacityModulation() {
        var scorer  = scorerWithCapacity(0.55);
        var memory  = formativeMemory();
        var context = new GraduationContext(5, 0, TENANT, AGENT);

        double score = scorer.score(memory, context);

        assertThat(score).isEqualTo(FormativeScoring.score(memory));
    }

    @Test
    void belowCorroborationGateReturnsZeroRegardlessOfCapacity() {
        var scorer  = scorerWithCapacity(1.0);
        var context = new GraduationContext(1, 0, TENANT, AGENT);

        double score = scorer.score(memoryWithConfidence(0.8, AGENT), context);

        assertThat(score).isEqualTo(0.0);
    }

    private DefaultGraduationScorer scorerWithCapacity(double relationalComplexity) {
        var capacity = new CognitiveCapacityConfig(relationalComplexity, null, null);
        var defaults = CognitiveDefaults.empty(AGENT).withCapacityConfig(capacity);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);
        return new DefaultGraduationScorer(3, stubInstance(registry));
    }

    @SuppressWarnings("unchecked")
    private static Instance<CognitiveDefaultsRegistry> stubInstance(CognitiveDefaultsRegistry registry) {
        return new Instance<>() {
            @Override public CognitiveDefaultsRegistry get() { return registry; }
            @Override public boolean isResolvable() { return true; }
            @Override public boolean isAmbiguous() { return false; }
            @Override public boolean isUnsatisfied() { return false; }
            @Override public void destroy(CognitiveDefaultsRegistry instance) {}
            @Override public Handle<CognitiveDefaultsRegistry> getHandle() { return null; }
            @Override public Iterable<? extends Handle<CognitiveDefaultsRegistry>> handles() { return null; }
            @Override public Instance<CognitiveDefaultsRegistry> select(Annotation... qualifiers) { return this; }
            @Override public <U extends CognitiveDefaultsRegistry> Instance<U> select(Class<U> subtype, Annotation... qualifiers) { return null; }
            @Override public <U extends CognitiveDefaultsRegistry> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) { return null; }
            @Override public Iterator<CognitiveDefaultsRegistry> iterator() { return java.util.List.of(registry).iterator(); }
        };
    }

    private static Memory memoryWithConfidence(double value, String agentId) {
        return new Memory("m1",
                          agentId != null ? new Subject("agent", agentId) : null,
                          new MemoryDomain("experience"), TENANT, null,
                          "some experience", Map.of(), Instant.now(),
                          new Confidence(ConfidenceOrigin.STATED, value, null),
                          null, null, null, null, Set.of());
    }

    private static Memory formativeMemory() {
        return new Memory("m-f",
                          new Subject("agent", AGENT),
                          new MemoryDomain("experience"), TENANT, null,
                          "formative event", Map.of("event-type", "formative"), Instant.now(),
                          new Confidence(ConfidenceOrigin.STATED, 0.8, null),
                          null, null, null, null, Set.of());
    }
}
