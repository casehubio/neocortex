package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.memory.Memory;

@FunctionalInterface
public interface GraduationScorer {
    double score(Memory memory);
}
