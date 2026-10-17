package io.casehub.neocortex.caps.engine;

import io.casehub.neocortex.caps.CapsConnection;
import io.casehub.neocortex.caps.CapsNode;
import io.casehub.neocortex.caps.CapsTopology;
import io.casehub.neocortex.caps.DistortionEffect;
import io.casehub.neocortex.caps.NodeRange;
import io.casehub.neocortex.caps.NodeType;
import io.casehub.neocortex.caps.WeightProvenance;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CapsTopologyLoaderTest {

    @Test
    void loadsTopologyFromClasspath() {
        var loader = new CapsTopologyLoader();
        CapsTopology topology = loader.loadFromClasspath("caps-topology.yaml");

        assertThat(topology.version()).isEqualTo(1);
        assertThat(topology.nodes()).isNotEmpty();
        assertThat(topology.connections()).isNotEmpty();
    }

    @Test
    void allInputNodesPresent() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var inputNodes = topology.nodes().values().stream()
            .filter(n -> n.type() == NodeType.INPUT).toList();

        assertThat(inputNodes).hasSizeGreaterThanOrEqualTo(28);
        assertThat(inputNodes.stream().map(CapsNode::id))
            .contains("secure_attachment", "physical_threat", "reward",
                       "mastery", "acceptance", "scarcity", "powerlessness");
    }

    @Test
    void allMediatingNodesPresent() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var mediating = topology.nodes().values().stream()
            .filter(n -> n.type() == NodeType.MEDIATING).toList();

        assertThat(mediating).hasSizeGreaterThanOrEqualTo(18);
        assertThat(mediating.stream().map(CapsNode::id))
            .contains("self_worth", "BAS_activation", "BIS_activation",
                       "threat_sensitivity", "reinforcement_expectation");
    }

    @Test
    void allOutputNodesPresent() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var output = topology.nodes().values().stream()
            .filter(n -> n.type() == NodeType.OUTPUT).toList();

        assertThat(output).hasSizeGreaterThanOrEqualTo(24);
        assertThat(output.stream().map(CapsNode::id))
            .contains("approach", "withdraw", "trust", "fight", "freeze",
                       "persist", "comply", "explore");
    }

    @Test
    void connectionIdsAreDeterministic() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");

        var conn = topology.connections().stream()
            .filter(c -> "secure_attachment".equals(c.from()) && "self_worth".equals(c.to()))
            .findFirst().orElseThrow();

        assertThat(conn.id()).isEqualTo("secure_attachment__self_worth");
        assertThat(conn.defaultWeight()).isCloseTo(0.30, within(0.001));
        assertThat(conn.provenance()).isEqualTo(WeightProvenance.EMPIRICAL);
    }

    @Test
    void connectionTagsLoaded() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");

        var bisConn = topology.connections().stream()
            .filter(c -> "BIS_activation".equals(c.from()) && "cautious_approach".equals(c.to()))
            .findFirst().orElseThrow();

        assertThat(bisConn.tags()).contains("bis");
    }

    @Test
    void bipolarNodesHaveCorrectRange() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var selfWorth = topology.nodes().get("self_worth");

        assertThat(selfWorth.range()).isEqualTo(NodeRange.BIPOLAR);
        assertThat(selfWorth.type()).isEqualTo(NodeType.MEDIATING);
    }

    @Test
    void inputNodesDefaultToUnipolar() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var reward = topology.nodes().get("reward");

        assertThat(reward.range()).isEqualTo(NodeRange.UNIPOLAR);
        assertThat(reward.type()).isEqualTo(NodeType.INPUT);
    }

    @Test
    void weightUpdateParametersLoaded() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var params = topology.weightUpdate();

        assertThat(params.maxIterations()).isEqualTo(100);
        assertThat(params.epsilon()).isCloseTo(0.001, within(0.0001));
        assertThat(params.scheduleModifiers()).containsKey("variable_ratio");
        assertThat(params.scheduleModifiers().get("variable_ratio")).isCloseTo(3.0, within(0.01));
    }

    @Test
    void dispositionModifiersLoaded() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");

        assertThat(topology.dispositionModifiers()).containsKey("socialOrient");
        var social = topology.dispositionModifiers().get("socialOrient");
        assertThat(social.valueModifiers()).containsKey("cooperative");
    }

    @Test
    void inputNodesHaveVocabulary() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var secureAttachment = topology.nodes().get("secure_attachment");

        assertThat(secureAttachment.vocabulary()).isNotEmpty();
        assertThat(secureAttachment.vocabulary()).containsKeys("secure", "warmth");
        assertThat(secureAttachment.vocabulary().get("secure")).isBetween(0.1, 1.0);
    }

    @Test
    void inputNodesHaveDescriptions() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var reward   = topology.nodes().get("reward");

        assertThat(reward.description()).isNotEmpty();
        assertThat(reward.description()).contains("plan succeeding");

        var secureAttachment = topology.nodes().get("secure_attachment");
        assertThat(secureAttachment.description()).isNotEmpty();

        // Mediating nodes don't have descriptions — only input nodes do
        var selfWorth = topology.nodes().get("self_worth");
        assertThat(selfWorth.description()).isEmpty();
    }


    @Test
    void allInputNodesHaveConnections() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var inputNodeIds = topology.nodes().values().stream()
                                   .filter(n -> n.type() == NodeType.INPUT)
                                   .map(CapsNode::id)
                                   .toList();

        var connectedSourceIds = topology.connections().stream()
                                         .map(CapsConnection::from)
                                         .collect(java.util.stream.Collectors.toSet());

        var disconnected = inputNodeIds.stream()
                                       .filter(id -> !connectedSourceIds.contains(id))
                                       .toList();

        assertThat(disconnected)
                .as("All INPUT nodes should have at least one outgoing connection")
                .isEmpty();
    }


    @Test
    void distortionsLoaded() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");

        assertThat(topology.distortions()).hasSizeGreaterThanOrEqualTo(5);
        var catastrophizing = topology.distortions().stream()
            .filter(d -> "catastrophizing".equals(d.id()))
            .findFirst().orElseThrow();

        assertThat(catastrophizing.baseThreshold()).isCloseTo(0.7, within(0.01));
        assertThat(catastrophizing.effect()).isEqualTo(DistortionEffect.MULTIPLICATIVE);
        assertThat(catastrophizing.targetCategories()).contains("world_model");
    }

    @Test
    void strictRuleFollowing_boostsInternalInhibition() {
        var topology = new CapsTopologyLoader().loadFromClasspath("caps-topology.yaml");
        var strict = topology.dispositionModifiers().get("ruleFollowing")
            .valueModifiers().get("strict");

        assertThat(strict)
            .as("strict ruleFollowing should boost an internal self-directed inhibition " +
                "mechanism (guilt, self-judgment), not just external social threat response")
            .containsKey("punishment_to_BIS_activation");
    }
}
