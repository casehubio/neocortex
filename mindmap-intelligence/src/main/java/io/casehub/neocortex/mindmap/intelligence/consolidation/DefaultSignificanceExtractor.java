package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.experience.ExperienceRecorded;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

@DefaultBean
@ApplicationScoped
public class DefaultSignificanceExtractor implements SignificanceExtractor {
    @Override
    public double extract(ExperienceRecorded event) {
        return 1.0;
    }
}
