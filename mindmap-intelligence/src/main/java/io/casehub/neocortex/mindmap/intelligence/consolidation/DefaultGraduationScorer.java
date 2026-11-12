package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.cognitive.index.CognitiveDefaultsRegistry;
import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.FormativeScoring;
import io.casehub.neocortex.memory.experience.GraduationContext;
import io.casehub.neocortex.memory.experience.GraduationScorer;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@DefaultBean
@ApplicationScoped
public class DefaultGraduationScorer implements GraduationScorer {

    private final int minCorroboration;
    private final Instance<CognitiveDefaultsRegistry> registryInstance;

    @Inject
    DefaultGraduationScorer(Instance<ExperienceConsolidationConfig> config,
                             Instance<CognitiveDefaultsRegistry> registryInstance) {
        var c = config.isResolvable() ? config.get() : null;
        this.minCorroboration = c != null ? c.minCorroboration() : 3;
        this.registryInstance = registryInstance;
    }

    DefaultGraduationScorer(int minCorroboration, Instance<CognitiveDefaultsRegistry> registryInstance) {
        this.minCorroboration = minCorroboration;
        this.registryInstance = registryInstance;
    }

    DefaultGraduationScorer(int minCorroboration) {
        this(minCorroboration, null);
    }

    DefaultGraduationScorer() {
        this(3, null);
    }

    /**
     * Scores experience graduation worthiness. Formative memories are exempt from
     * capacity modulation — they represent seeded developmental history.
     *
     * <p>Cattell's (1971) Investment Theory: {@code relationalComplexity} (Halford 1998)
     * scales the graduation score — higher relational complexity enables better
     * abstraction from experience, producing higher-quality knowledge representations.
     *
     * <p>Note: this score flows into BehavioralSynthesisPhase as outcomeIntensity (beta).
     * Combined with CAPS alpha modulation (associativeReach), agents uniformly low across
     * dimensions experience cross-site compounding via independent cognitive substrates.
     */
    @Override
    public double score(Memory memory, GraduationContext context) {
        if (FormativeScoring.isFormative(memory)) {
            return FormativeScoring.score(memory);
        }

        int effectiveCorroboration = Math.max(context.corroboratingCount(), context.textSimilarityCount());
        if (effectiveCorroboration < minCorroboration) {return 0.0;}

        double rawScore = memory.confidence() != null ? memory.confidence().value() : 0.5;
        return rawScore * resolveRelationalComplexity(context.agentId());
    }

    private double resolveRelationalComplexity(String agentId) {
        if (agentId == null || registryInstance == null || !registryInstance.isResolvable()) {
            return 1.0;
        }
        var defaults = registryInstance.get().forAgentOrDefaults(agentId);
        var cap = defaults.capacityConfig();
        return cap != null ? cap.relationalComplexityOrDefault() : 1.0;
    }
}
