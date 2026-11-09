package io.casehub.neocortex.cognition.trace;

import java.util.List;
import java.util.Optional;

public interface CognitionStateProvider {
    Optional<PipelineTrace> currentTrace(String agentId, String tenantId);
    List<String> activeAgents(String tenantId);
}
