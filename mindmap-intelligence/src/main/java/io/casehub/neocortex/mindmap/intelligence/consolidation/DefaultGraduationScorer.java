package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.Memory;
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

    @Inject
    DefaultGraduationScorer(Instance<ExperienceConsolidationConfig> config) {
        var c = config.isResolvable() ? config.get() : null;
        this.minCorroboration = c != null ? c.minCorroboration() : 3;
    }

    DefaultGraduationScorer(int minCorroboration) {
        this.minCorroboration = minCorroboration;
    }

    DefaultGraduationScorer() {
        this(3);
    }

    @Override
    public double score(Memory memory, GraduationContext context) {
        if (context.corroboratingCount() < minCorroboration) return 0.0;
        if (memory.confidence() != null) {
            return memory.confidence().value();
        }
        return 0.5;
    }
}
