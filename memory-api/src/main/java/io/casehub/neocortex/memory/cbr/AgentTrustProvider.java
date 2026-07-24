package io.casehub.neocortex.memory.cbr;

import java.util.OptionalDouble;

@FunctionalInterface
public interface AgentTrustProvider {
    OptionalDouble currentTrustScore(String agentId);
}
