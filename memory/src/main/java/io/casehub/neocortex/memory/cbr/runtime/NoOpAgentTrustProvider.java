package io.casehub.neocortex.memory.cbr.runtime;

import io.casehub.neocortex.memory.cbr.AgentTrustProvider;
import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.OptionalDouble;

@DefaultBean
@ApplicationScoped
public class NoOpAgentTrustProvider implements AgentTrustProvider {
    @Override
    public OptionalDouble currentTrustScore(String agentId) {
        return OptionalDouble.empty();
    }
}
