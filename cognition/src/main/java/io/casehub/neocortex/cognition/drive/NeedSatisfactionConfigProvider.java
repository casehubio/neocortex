package io.casehub.neocortex.cognition.drive;

public interface NeedSatisfactionConfigProvider {

    NeedSatisfactionConfig configFor(String agentId);

    static NeedSatisfactionConfigProvider global(NeedSatisfactionConfig config) {
        return agentId -> config;
    }
}
