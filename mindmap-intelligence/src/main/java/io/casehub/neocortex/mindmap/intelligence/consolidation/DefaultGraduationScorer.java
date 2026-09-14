package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.Memory;
import io.casehub.neocortex.memory.experience.GraduationScorer;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class DefaultGraduationScorer implements GraduationScorer {

    @Override
    public double score(Memory memory) {
        if (memory.confidence() != null) {
            return memory.confidence().value();
        }
        return 0.5;
    }
}
