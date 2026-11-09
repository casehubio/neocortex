package io.casehub.neocortex.caps.testing;

import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.NodeRange;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.engine.CapsTopologyLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegulationTopologyTest {

    private static CapsTopology topology;

    @BeforeAll
    static void loadTopology() {
        topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
    }

    @Test
    void regulationOutputNodesExist() {
        var expected = List.of(
            "situation_selection", "situation_modification",
            "attentional_deployment", "cognitive_change", "response_modulation",
            "social_support_seeking", "social_sharing", "social_withdrawal",
            "co_regulation", "deliberate_provocation");
        for (var id : expected) {
            var node = topology.nodes().get(id);
            assertThat(node).as("Missing regulation output node: %s", id).isNotNull();
            assertThat(node.type()).isEqualTo(NodeType.OUTPUT);
            assertThat(node.range()).isEqualTo(NodeRange.UNIPOLAR);
            assertThat(node.category()).isEqualTo("regulation");
        }
    }

    @Test
    void socialContextInputNodesExist() {
        var expected = List.of(
            "perceived_responsiveness", "target_distress",
            "social_isolation", "power_asymmetry");
        for (var id : expected) {
            var node = topology.nodes().get(id);
            assertThat(node).as("Missing social-context input node: %s", id).isNotNull();
            assertThat(node.type()).isEqualTo(NodeType.INPUT);
            assertThat(node.category()).isEqualTo("social_context");
        }
    }

    @Test
    void intrapersonalRegulationConnectionsExist() {
        var connections = topology.connections();
        assertThat(connections).anyMatch(
            c -> c.from().equals("arousal_level") && c.to().equals("attentional_deployment"));
        assertThat(connections).anyMatch(
            c -> c.from().equals("self_efficacy") && c.to().equals("cognitive_change"));
        assertThat(connections).anyMatch(
            c -> c.from().equals("BIS_activation") && c.to().equals("response_modulation"));
        assertThat(connections).anyMatch(
            c -> c.from().equals("threat_sensitivity") && c.to().equals("situation_selection"));
        assertThat(connections).anyMatch(
            c -> c.from().equals("self_efficacy") && c.to().equals("situation_modification"));
    }

    @Test
    void socialRegulationConnectionsExist() {
        var connections = topology.connections();
        assertThat(connections).anyMatch(
            c -> c.from().equals("other_safety") && c.to().equals("social_support_seeking"));
        assertThat(connections).anyMatch(
            c -> c.from().equals("BIS_activation") && c.to().equals("social_withdrawal"));
        assertThat(connections).anyMatch(
            c -> c.from().equals("target_distress") && c.to().equals("co_regulation"));
        assertThat(connections).anyMatch(
            c -> c.from().equals("power_asymmetry") && c.to().equals("deliberate_provocation"));
    }

    @Test
    void socialContextToMediatingConnectionsExist() {
        var connections = topology.connections();
        assertThat(connections).anyMatch(
            c -> c.from().equals("perceived_responsiveness") && c.to().equals("other_safety"));
        assertThat(connections).anyMatch(
            c -> c.from().equals("social_isolation") && c.to().equals("BIS_activation"));
        assertThat(connections).anyMatch(
            c -> c.from().equals("power_asymmetry") && c.to().equals("social_comparison"));
    }

    @Test
    void topologyNodeCountIsCorrect() {
        assertThat(topology.nodes()).hasSize(91);
    }

    @Test
    void topologyConnectionCountIsCorrect() {
        assertThat(topology.connections()).hasSize(110);
    }
}
