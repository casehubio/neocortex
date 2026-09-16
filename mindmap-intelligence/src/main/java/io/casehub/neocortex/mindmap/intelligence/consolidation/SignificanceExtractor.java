package io.casehub.neocortex.mindmap.intelligence.consolidation;

import io.casehub.neocortex.memory.experience.ExperienceRecorded;

@FunctionalInterface
public interface SignificanceExtractor {
    double extract(ExperienceRecorded event);
}
