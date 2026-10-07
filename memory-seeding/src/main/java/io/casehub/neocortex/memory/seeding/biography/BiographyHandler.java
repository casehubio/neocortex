package io.casehub.neocortex.memory.seeding.biography;

import java.util.Set;

public interface BiographyHandler {
    Set<String> handledTypes();
    void handle(BiographyProfile profile, String agentId, String tenantId);
}
