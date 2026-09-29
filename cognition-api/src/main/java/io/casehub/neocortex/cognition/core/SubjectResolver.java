package io.casehub.neocortex.cognition.core;

import java.util.Set;

@FunctionalInterface
public interface SubjectResolver {
    Set<String> relevantSubjects(String agentId, String tenantId);
}
