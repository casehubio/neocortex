package io.casehub.neocortex.caps;

import io.casehub.neocortex.cognitive.index.DispositionAxes;
import java.util.Map;

public interface CapsEngine {
    CapsTopology topology();

    AgentCapsState loadState(String tenantId, String agentId);
    void saveState(AgentCapsState state);
    AgentCapsState initializeAgent(String tenantId, String agentId,
                                   DispositionAxes disposition);

    SettlingResult settle(AgentCapsState state,
                          Map<String, Double> inputActivations);

    AgentCapsState updateWeights(AgentCapsState state,
                                 Map<String, Double> inputActivations,
                                 double outcomeIntensity,
                                 double outcomeValence,
                                 double salienceMultiplier,
                                 String reinforcementSchedule);
}
