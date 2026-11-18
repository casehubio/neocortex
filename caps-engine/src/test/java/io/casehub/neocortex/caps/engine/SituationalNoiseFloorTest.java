package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.cognitive.index.DispositionAxes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SituationalNoiseFloorTest {

    private static final double X_FLOOR = 0.6;
    // Per-mode floor from spec is 0.3 — use 0.35 for cumulative (all inputs at once)
    // since modes activate only a subset of inputs
    private static final double M_FLOOR_CUMULATIVE = 0.35;

    private static final Set<String> SITUATION_CATEGORIES = Set.of(
            "relationship", "need", "threat", "agency", "positive_affect");

    private static CapsTopology topology;
    private static Set<String> situationInputNodes;
    private static Set<String> mediatingNodes;

    @BeforeAll
    static void loadTopology() {
        topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        situationInputNodes = topology.nodes().values().stream()
                .filter(n -> n.type() == NodeType.INPUT)
                .filter(n -> SITUATION_CATEGORIES.contains(n.category()))
                .map(CapsNode::id)
                .collect(Collectors.toSet());
        mediatingNodes = topology.nodes().values().stream()
                .filter(n -> n.type() == NodeType.MEDIATING)
                .map(CapsNode::id)
                .collect(Collectors.toSet());
    }

    @Test
    void subFloorInputsProduceSubFloorMediatingResponses() {
        var disposition = new DispositionAxes(
                "cooperative", "moderate", "calculated", "moderate", "analytical");
        var mapper = new DispositionWeightMapper(topology);
        var state  = mapper.initializeWeights("t1", "agent-1", disposition);

        var settler    = new CapsSettler(topology);
        var restResult = settler.settle(state, Map.of());

        var floorInputs = new LinkedHashMap<String, Double>();
        for (var nodeId : situationInputNodes) {
            floorInputs.put(nodeId, X_FLOOR);
        }
        var stressResult = settler.settle(state, floorInputs);

        var violations = new java.util.ArrayList<String>();
        for (var nodeId : mediatingNodes) {
            double restActivation   = restResult.activations().getOrDefault(nodeId, 0.0);
            double stressActivation = stressResult.activations().getOrDefault(nodeId, 0.0);
            double fromRest         = Math.max(0, stressActivation - restActivation);
            if (fromRest > M_FLOOR_CUMULATIVE) {
                violations.add("%s: from-rest=%.4f (rest=%.4f, stressed=%.4f)"
                                       .formatted(nodeId, fromRest, restActivation, stressActivation));
            }
        }
        assertThat(violations)
                .as("mediating nodes exceeding m_floor_cumulative=%s at x_floor=%s", M_FLOOR_CUMULATIVE, X_FLOOR)
                .isEmpty();
    }

    @Test
    void uncalibratedDefaultFloorAlsoSubThreshold() {
        var disposition = new DispositionAxes(
                "competitive", "bold", "flexible", "independent", "confrontational");
        var mapper = new DispositionWeightMapper(topology);
        var state = mapper.initializeWeights("t1", "agent-2", disposition);

        var settler = new CapsSettler(topology);
        var restResult = settler.settle(state, Map.of());

        var floorInputs = new LinkedHashMap<String, Double>();
        for (var nodeId : situationInputNodes) {
            floorInputs.put(nodeId, X_FLOOR);
        }
        var stressResult = settler.settle(state, floorInputs);

        int violationCount = 0;
        for (var nodeId : mediatingNodes) {
            double restActivation = restResult.activations().getOrDefault(nodeId, 0.0);
            double stressActivation = stressResult.activations().getOrDefault(nodeId, 0.0);
            double fromRest = Math.max(0, stressActivation - restActivation);
            if (fromRest > M_FLOOR_CUMULATIVE) {
                violationCount++;
            }
        }
        assertThat(violationCount)
                .as("mediating nodes exceeding m_floor at x_floor=%s", X_FLOOR)
                .isZero();
    }

    @Test
    void situationInputNodesExist() {
        assertThat(situationInputNodes)
                .as("situation input nodes from topology")
                .isNotEmpty();
    }

    @Test
    void mediatingNodesExist() {
        assertThat(mediatingNodes)
                .as("mediating nodes from topology")
                .isNotEmpty();
    }
}
