package io.casehub.neocortex.caps.testing;

import io.casehub.neocortex.caps.*;
import io.casehub.neocortex.caps.engine.CapsSettler;
import io.casehub.neocortex.caps.engine.CapsTopologyLoader;
import io.casehub.neocortex.caps.engine.CapsWeightUpdater;
import io.casehub.neocortex.caps.engine.DispositionWeightMapper;
import io.casehub.neocortex.cognitive.index.DispositionAxes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCapsEngine implements CapsEngine {

    private final CapsTopology topology;
    private final CapsSettler settler;
    private final CapsWeightUpdater weightUpdater;
    private final DispositionWeightMapper dispositionMapper;
    private final Map<String, AgentCapsState> states = new ConcurrentHashMap<>();

    public InMemoryCapsEngine() {
        this.topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        this.settler = new CapsSettler(topology);
        this.weightUpdater = new CapsWeightUpdater(topology);
        this.dispositionMapper = new DispositionWeightMapper(topology);
    }

    @Override
    public CapsTopology topology() { return topology; }

    @Override
    public AgentCapsState loadState(String tenantId, String agentId) {
        return states.get(tenantId + "/" + agentId);
    }

    @Override
    public void saveState(AgentCapsState state) {
        states.put(state.tenantId() + "/" + state.agentId(), state);
    }

    @Override
    public AgentCapsState initializeAgent(String tenantId, String agentId,
                                           DispositionAxes disposition) {
        AgentCapsState state = dispositionMapper.initializeWeights(
            tenantId, agentId, disposition);
        saveState(state);
        return state;
    }

    @Override
    public SettlingResult settle(AgentCapsState state,
                                 Map<String, Double> inputActivations) {
        return settler.settle(state, inputActivations);
    }

    @Override
    public AgentCapsState updateWeights(AgentCapsState state,
                                         Map<String, Double> inputActivations,
                                         double outcomeIntensity,
                                         double outcomeValence,
                                         double salienceMultiplier,
                                         String reinforcementSchedule) {
        return weightUpdater.update(state, inputActivations,
            outcomeIntensity, outcomeValence, salienceMultiplier,
            reinforcementSchedule);
    }

    public void clear() { states.clear(); }
}
