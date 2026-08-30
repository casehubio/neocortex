package io.casehub.neocortex.memory.experience;

import java.time.Instant;
import java.util.Map;

public sealed interface ExperienceEvent permits Observation, Action, Outcome {
    String agentId();
    String tenantId();
    String caseId();
    String turnId();

    Instant timestamp();

    String description();
    Double confidence();
    Map<String, String> metadata();
}
