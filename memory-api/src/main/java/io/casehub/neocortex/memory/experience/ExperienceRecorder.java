package io.casehub.neocortex.memory.experience;

import java.util.List;

public interface ExperienceRecorder {
    String record(ExperienceEvent event);
    ExperienceStoreResult recordAll(List<ExperienceEvent> events);
}
