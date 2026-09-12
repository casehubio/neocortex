package io.casehub.neocortex.cognitive.index;

import io.casehub.platform.api.identity.PrincipalId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SocialComparisonTest {

    @Test
    void compareTwoAgentsComputesPadDistance() {
        PrincipalId alice = PrincipalId.agent("alice");
        PrincipalId bob = PrincipalId.agent("bob");

        var perspectives = Map.of(
            alice, ekWithPad(alice, 0.9, 0.3, 0.5),
            bob, ekWithPad(bob, -0.2, 0.7, 0.1));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        assertThat(result.agentCount()).isEqualTo(2);
        double dist = result.distances().distance(alice, bob);
        // sqrt((0.9-(-0.2))^2 + (0.3-0.7)^2 + (0.5-0.1)^2) = sqrt(1.21+0.16+0.16) ≈ 1.237
        assertThat(dist).isCloseTo(1.237, within(0.01));
    }

    @Test
    void compareTwoAgentsComputesSignedDifferences() {
        PrincipalId alice = PrincipalId.agent("alice");
        PrincipalId bob = PrincipalId.agent("bob");

        var perspectives = Map.of(
            alice, ekWithPad(alice, 0.9, 0.3, 0.5),
            bob, ekWithPad(bob, -0.2, 0.7, 0.1));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        double pleasureDiff = result.dimensionDifferences()
            .get(PadDimension.PLEASURE).difference(alice, bob);
        assertThat(pleasureDiff).isCloseTo(1.1, within(0.001));
    }

    @Test
    void compareThreeAgentsAllPairwiseDistances() {
        PrincipalId a = PrincipalId.agent("a");
        PrincipalId b = PrincipalId.agent("b");
        PrincipalId c = PrincipalId.agent("c");

        var perspectives = Map.of(
            a, ekWithPad(a, 0.0, 0.0, 0.0),
            b, ekWithPad(b, 1.0, 0.0, 0.0),
            c, ekWithPad(c, 0.0, 1.0, 0.0));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        assertThat(result.distances().distances()).hasSize(3);
        assertThat(result.distances().distance(a, b)).isCloseTo(1.0, within(0.001));
        assertThat(result.distances().distance(a, c)).isCloseTo(1.0, within(0.001));
        assertThat(result.distances().distance(b, c)).isCloseTo(Math.sqrt(2), within(0.001));
    }

    @Test
    void compareAlignedTrajectories() {
        PrincipalId alice = PrincipalId.agent("alice");
        PrincipalId bob = PrincipalId.agent("bob");

        var perspectives = Map.of(
            alice, ekWithTrajectory(alice, 0.5, 0.1, 0.3),
            bob, ekWithTrajectory(bob, 0.3, 0.05, 0.2));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        AgentPair pair = AgentPair.of(alice, bob);
        assertThat(result.trajectoryAlignment().agreements().get(pair))
            .isEqualTo(TrendAgreement.ALIGNED);
        assertThat(result.trajectoryAlignment().cosineSimilarities().get(pair))
            .isGreaterThan(0.9);
    }

    @Test
    void compareDivergentTrajectories() {
        PrincipalId alice = PrincipalId.agent("alice");
        PrincipalId bob = PrincipalId.agent("bob");

        var perspectives = Map.of(
            alice, ekWithTrajectory(alice, 0.5, 0.1, 0.3),
            bob, ekWithTrajectory(bob, -0.5, -0.1, -0.3));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        AgentPair pair = AgentPair.of(alice, bob);
        assertThat(result.trajectoryAlignment().agreements().get(pair))
            .isEqualTo(TrendAgreement.DIVERGENT);
        assertThat(result.trajectoryAlignment().cosineSimilarities().get(pair))
            .isLessThan(-0.9);
    }

    @Test
    void compareMixedTrajectories() {
        PrincipalId alice = PrincipalId.agent("alice");
        PrincipalId bob = PrincipalId.agent("bob");

        var perspectives = Map.of(
            alice, ekWithTrajectory(alice, 0.5, 0.1, 0.3),
            bob, ekWithStableTrajectory(bob));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        AgentPair pair = AgentPair.of(alice, bob);
        assertThat(result.trajectoryAlignment().agreements().get(pair))
            .isEqualTo(TrendAgreement.MIXED);
    }

    @Test
    void compareInsufficientTrajectoryData() {
        PrincipalId alice = PrincipalId.agent("alice");
        PrincipalId bob = PrincipalId.agent("bob");

        var perspectives = Map.of(
            alice, ekWithTrajectory(alice, 0.5, 0.1, 0.3),
            bob, ekWithPad(bob, 0.5, 0.5, 0.5));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        AgentPair pair = AgentPair.of(alice, bob);
        assertThat(result.trajectoryAlignment().agreements().get(pair))
            .isEqualTo(TrendAgreement.INSUFFICIENT);
    }

    @Test
    void nullPadAgentsExcludedFromDistances() {
        PrincipalId alice = PrincipalId.agent("alice");
        PrincipalId bob = PrincipalId.agent("bob");

        var perspectives = Map.of(
            alice, ekWithPad(alice, null, null, null),
            bob, ekWithPad(bob, 0.5, 0.5, 0.5));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        assertThat(result.unassessedAgents()).containsExactly(alice);
        assertThat(result.distances().distances()).isEmpty();
    }

    @Test
    void partialNullPadAgentsExcludedFromDistances() {
        PrincipalId alice = PrincipalId.agent("alice");
        PrincipalId bob = PrincipalId.agent("bob");

        var perspectives = Map.of(
            alice, ekWithPad(alice, 0.5, null, 0.3),
            bob, ekWithPad(bob, 0.5, 0.5, 0.5));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        assertThat(result.unassessedAgents()).containsExactly(alice);
        assertThat(result.distances().distances()).isEmpty();
    }

    @Test
    void singleAgentTrivialResult() {
        PrincipalId alice = PrincipalId.agent("alice");

        var perspectives = Map.of(alice, ekWithPad(alice, 0.5, 0.3, 0.7));

        PerspectivalComparison result = SocialComparison.compare(perspectives);

        assertThat(result.agentCount()).isEqualTo(1);
        assertThat(result.distances().distances()).isEmpty();
        assertThat(result.perspectives()).containsKey(alice);
    }

    // --- helpers ---

    private EntityKnowledge ekWithPad(PrincipalId agent, Double p, Double a, Double d) {
        var node = new StubNode("id-" + agent.value(), "entity", "sg", "general",
            null, null, Instant.now(), Instant.now(), null, null,
            Set.of(), Set.of(), p, a, d, Map.of(), null, Set.of());
        return new EntityKnowledge(node, List.of(), Map.of(), null, Set.of(), "t1", agent);
    }

    private EntityKnowledge ekWithTrajectory(PrincipalId agent,
            double pleasureSlope, double arousalSlope, double dominanceSlope) {
        var node = StubNode.named("entity");
        var trajectory = new AffectTrajectory(
            pleasureSlope, 0.1, arousalSlope, dominanceSlope,
            pleasureSlope > 0 ? TrendDirection.IMPROVING : TrendDirection.WORSENING,
            Math.abs(pleasureSlope), 10);
        return new EntityKnowledge(node, List.of(), Map.of(), trajectory, Set.of(), "t1", agent);
    }

    private EntityKnowledge ekWithStableTrajectory(PrincipalId agent) {
        var node = StubNode.named("entity");
        var trajectory = new AffectTrajectory(0.0, 0.1, 0.0, 0.0,
            TrendDirection.STABLE, 0.0, 10);
        return new EntityKnowledge(node, List.of(), Map.of(), trajectory, Set.of(), "t1", agent);
    }
}