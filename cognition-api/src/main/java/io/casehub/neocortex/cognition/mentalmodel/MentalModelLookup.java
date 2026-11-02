package io.casehub.neocortex.cognition.mentalmodel;

import java.util.Optional;

@FunctionalInterface
public interface MentalModelLookup {
    Optional<MentalModelSnapshot> lookup(String agentId, String subjectId, String tenantId);
}
