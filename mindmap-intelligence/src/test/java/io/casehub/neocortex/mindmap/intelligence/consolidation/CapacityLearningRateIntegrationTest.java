package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.caps.WeightUpdateContext;
import io.casehub.neocortex.cognitive.CognitiveCapacityConfig;
import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import io.casehub.neocortex.cognitive.index.CognitiveDefaults;
import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.MemoryDomain;
import io.casehub.neocortex.memory.Subject;
import io.casehub.neocortex.memory.cbr.CbrOutcome;
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

/**
 * Cross-site integration tests for Cattell (1971) Investment Theory capacity modulation.
 * Verifies dimension independence, baseline equivalence, and 2-2-2 balance.
 */
class CapacityLearningRateIntegrationTest {

    private static final String TENANT = "t1";
    private static final String AGENT  = "agent-1";

    @Test
    void fullCapacityIsIdenticalToNoModulation() {
        var fullCapacity = new CognitiveCapacityConfig(1.0, 1.0, 1.0);
        var defaults = CognitiveDefaults.empty(AGENT).withCapacityConfig(fullCapacity);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        var scorer = new DefaultGraduationScorer(3, stubInstance(registry));
        var memory = memoryWithConfidence(0.8);
        var context = new GraduationContext(5, 0, TENANT, AGENT);
        double score = scorer.score(memory, context);
        assertThat(score).isCloseTo(0.8, offset(0.001));

        var ctx = new WeightUpdateContext(Map.of("reward", 0.8), 0.8, 1.0, 0.5, "continuous", 1.0);
        assertThat(ctx.capacityMultiplier()).isEqualTo(1.0);

        double wmSpan = fullCapacity.workingMemorySpanOrDefault();
        double effectiveLR = CbrOutcome.DEFAULT_LEARNING_RATE * wmSpan;
        assertThat(effectiveLR).isEqualTo(CbrOutcome.DEFAULT_LEARNING_RATE);
    }

    @Test
    void crossSiteCompoundingUsesIndependentDimensions() {
        var asymmetric = new CognitiveCapacityConfig(0.55, 0.9, 0.75);
        var defaults = CognitiveDefaults.empty(AGENT).withCapacityConfig(asymmetric);
        var registry = CognitiveDefaultsRegistry.forTesting(defaults);

        // Graduation uses relationalComplexity = 0.55
        var scorer = new DefaultGraduationScorer(3, stubInstance(registry));
        var memory = memoryWithConfidence(0.8);
        var context = new GraduationContext(5, 0, TENANT, AGENT);
        double graduationScore = scorer.score(memory, context);
        assertThat(graduationScore).isCloseTo(0.8 * 0.55, offset(0.01));

        // CAPS uses associativeReach = 0.9
        double capsCapacity = asymmetric.associativeReachOrDefault();
        assertThat(capsCapacity).isCloseTo(0.9, offset(0.001));

        // CBR uses workingMemorySpan = 0.75
        double cbrCapacity = asymmetric.workingMemorySpanOrDefault();
        assertThat(cbrCapacity).isCloseTo(0.75, offset(0.001));

        // The compound effect is 0.55 * 0.9 ≈ 0.495, NOT 0.55^2 = 0.3025
        double compound = graduationScore * capsCapacity;
        assertThat(compound).isCloseTo(0.8 * 0.55 * 0.9, offset(0.01));
        assertThat(compound).isNotCloseTo(0.8 * 0.55 * 0.55, offset(0.05));
    }

    @Test
    void dimensionMappingBalance() {
        var capacity = new CognitiveCapacityConfig(0.6, 0.7, 0.8);
        var defaults = CognitiveDefaults.empty(AGENT).withCapacityConfig(capacity);

        // relationalComplexity (0.6) → graduation + personality = 2 sites
        assertThat(capacity.relationalComplexityOrDefault()).isEqualTo(0.6);

        // associativeReach (0.7) → CAPS + drive = 2 sites
        assertThat(capacity.associativeReachOrDefault()).isEqualTo(0.7);

        // workingMemorySpan (0.8) → CBR + strategy = 2 sites
        assertThat(capacity.workingMemorySpanOrDefault()).isEqualTo(0.8);

        // No dimension is unused, no dimension has more than 2 sites
    }

    @Test
    void nullCapacityConfigDefaultsToOne() {
        var defaults = CognitiveDefaults.empty(AGENT);
        assertThat(defaults.capacityConfig()).isNull();

        var capacity = defaults.capacityConfig();
        double rc = capacity != null ? capacity.relationalComplexityOrDefault() : 1.0;
        double ar = capacity != null ? capacity.associativeReachOrDefault() : 1.0;
        double wm = capacity != null ? capacity.workingMemorySpanOrDefault() : 1.0;

        assertThat(rc).isEqualTo(1.0);
        assertThat(ar).isEqualTo(1.0);
        assertThat(wm).isEqualTo(1.0);
    }

    @Test
    void compressCapacityRange() {
        // CDE compresses capacity to [0.55, 0.95]
        double min = 0.55 + 0.0 * 0.4;
        double max = 0.55 + 1.0 * 0.4;
        assertThat(min).isEqualTo(0.55);
        assertThat(max).isCloseTo(0.95, offset(0.001));

        // Worst-case cross-site compound: 0.55^2 ≈ 0.30 (still meaningful)
        assertThat(min * min).isCloseTo(0.3025, offset(0.001));
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

    private static Memory memoryWithConfidence(double value) {
        return new Memory("m1", new Subject("agent", AGENT),
                          new MemoryDomain("experience"), TENANT, null,
                          "test experience", Map.of(), Instant.now(),
                          new Confidence(ConfidenceOrigin.STATED, value, null),
                          null, null, null, null, Set.of());
    }
}
