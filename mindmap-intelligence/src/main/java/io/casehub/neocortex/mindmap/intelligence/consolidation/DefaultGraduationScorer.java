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
        String eventType = memory.attributes().get(
                io.casehub.neocortex.memory.experience.ExperienceAttributeKeys.EVENT_TYPE);

        if ("formative".equals(eventType)) {
            double base = memory.confidence() != null ? memory.confidence().value() : 0.8;
            String salienceStr = memory.attributes().get(
                    io.casehub.neocortex.memory.experience.FormativeAttributeKeys.SALIENCE_MULTIPLIER);
            double salience = salienceStr != null ? Double.parseDouble(salienceStr) : 1.0;
            return Math.min(1.0, base * salience);
        }

        int effectiveCorroboration = Math.max(context.corroboratingCount(), context.textSimilarityCount());
        if (effectiveCorroboration < minCorroboration) {return 0.0;}
        if (memory.confidence() != null) {
            return memory.confidence().value();
        }
        return 0.5;
    }
}
