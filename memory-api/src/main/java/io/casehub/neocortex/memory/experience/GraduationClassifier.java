package io.casehub.neocortex.memory.experience;

import io.casehub.neocortex.memory.Memory;

public interface GraduationClassifier {
    GraduationResult classify(Memory memory);
}
