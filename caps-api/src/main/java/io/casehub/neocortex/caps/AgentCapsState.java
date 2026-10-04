package io.casehub.neocortex.caps;

import java.util.Map;

public record AgentCapsState(
    String agentId,
    String tenantId,
    long generation,
    Map<String, ConnectionWeight> weights,
    Map<String, NodeState> nodeStates
) {}
