package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MultiAgentInstanceTest {

    private JunctionTree sharedTree() {
        return BayesNetworkBuilder.create("shared")
            .variable("X", "a", "b").prior(0.5, 0.5)
            .variable("Y", "a", "b").given("X").cpt(0.8, 0.2, 0.3, 0.7)
            .buildJunctionTree();
    }

    private MultiAgentComposition twoAgents() {
        return MultiAgentBuilder.create("test")
            .sharedNetwork(sharedTree())
            .agent("alice")
            .agent("bob")
            .build();
    }

    @Test
    void independentEvidence_independentPosteriors() {
        var instance = new MultiAgentInstance(twoAgents());
        instance.setEvidence("alice", "X", new double[]{0.9, 0.1});
        instance.setEvidence("bob", "X", new double[]{0.1, 0.9});
        instance.update();

        double[] aliceY = instance.marginalize("alice", "Y");
        double[] bobY = instance.marginalize("bob", "Y");
        assertTrue(aliceY[0] > aliceY[1], "Alice: X=a likely -> Y=a likely");
        assertTrue(bobY[1] > bobY[0], "Bob: X=b likely -> Y=b likely");
    }

    @Test
    void sameEvidence_identicalPosteriors() {
        var comp = MultiAgentBuilder.create("test")
            .sharedNetwork(sharedTree())
            .agent("alice")
            .agent("bob")
            .agent("charlie")
            .build();
        var instance = new MultiAgentInstance(comp);

        instance.setEvidence("alice", "X", new double[]{0.9, 0.1});
        instance.setEvidence("bob", "X", new double[]{0.9, 0.1});
        instance.setEvidence("charlie", "X", new double[]{0.9, 0.1});
        instance.update();

        double[] a = instance.marginalize("alice", "Y");
        double[] b = instance.marginalize("bob", "Y");
        double[] c = instance.marginalize("charlie", "Y");
        assertArrayEquals(a, b, 1e-10);
        assertArrayEquals(b, c, 1e-10);
    }

    @Test
    void commonEvidence_propagatesToAllAgents() {
        var comp = MultiAgentBuilder.create("test")
            .sharedNetwork(sharedTree())
            .agent("alice")
            .agent("bob")
            .commonKnowledge("X")
            .build();
        var instance = new MultiAgentInstance(comp);

        instance.setCommonEvidence("X", new double[]{0.9, 0.1});
        instance.update();

        double[] a = instance.marginalize("alice", "Y");
        double[] b = instance.marginalize("bob", "Y");
        assertArrayEquals(a, b, 1e-10);
    }

    @Test
    void commonVariableGuard_throwsOnDirectSet() {
        var comp = MultiAgentBuilder.create("test")
            .sharedNetwork(sharedTree())
            .agent("alice")
            .agent("bob")
            .commonKnowledge("X")
            .build();
        var instance = new MultiAgentInstance(comp);

        assertThrows(IllegalArgumentException.class, () ->
            instance.setEvidence("alice", "X", new double[]{0.9, 0.1}));
    }

    @Test
    void divergence_identicalBeliefs_zero() {
        var instance = new MultiAgentInstance(twoAgents());
        instance.setEvidence("alice", "X", new double[]{0.9, 0.1});
        instance.setEvidence("bob", "X", new double[]{0.9, 0.1});
        instance.update();

        BeliefDivergence div = instance.divergence("alice", "bob", "Y");
        assertEquals(0.0, div.klDivergence(), 1e-10);
        assertEquals(0.0, div.totalVariation(), 1e-10);
    }

    @Test
    void divergence_differentBeliefs_positive() {
        var instance = new MultiAgentInstance(twoAgents());
        instance.setEvidence("alice", "X", new double[]{0.9, 0.1});
        instance.setEvidence("bob", "X", new double[]{0.1, 0.9});
        instance.update();

        BeliefDivergence div = instance.divergence("alice", "bob", "Y");
        assertTrue(div.klDivergence() > 0);
        assertTrue(div.totalVariation() > 0);
        assertTrue(div.totalVariation() <= 1.0);
    }

    @Test
    void allDivergences_returnsAllPairs() {
        var comp = MultiAgentBuilder.create("test")
            .sharedNetwork(sharedTree())
            .agent("a").agent("b").agent("c")
            .build();
        var instance = new MultiAgentInstance(comp);
        instance.setEvidence("a", "X", new double[]{0.9, 0.1});
        instance.setEvidence("b", "X", new double[]{0.5, 0.5});
        instance.setEvidence("c", "X", new double[]{0.1, 0.9});
        instance.update();

        List<BeliefDivergence> divs = instance.allDivergences("Y");
        assertEquals(3, divs.size());
    }

    @Test
    void evidenceIsolation_settingOnAliceDoesNotAffectBob() {
        var instance = new MultiAgentInstance(twoAgents());
        instance.update();
        double[] bobBefore = instance.marginalize("bob", "Y");

        instance.setEvidence("alice", "X", new double[]{0.9, 0.1});
        instance.update();
        double[] bobAfter = instance.marginalize("bob", "Y");

        assertArrayEquals(bobBefore, bobAfter, 1e-10);
    }

    @Test
    void update_returnsStats() {
        var instance = new MultiAgentInstance(twoAgents());
        instance.setEvidence("alice", "X", new double[]{0.9, 0.1});
        CompositionStats stats = instance.update();
        assertEquals(2, stats.slotsUpdated());
    }

    @Test
    void reset_clearsEvidence() {
        var instance = new MultiAgentInstance(twoAgents());
        instance.setEvidence("alice", "X", new double[]{0.9, 0.1});
        instance.update();
        double[] biasedY = instance.marginalize("alice", "Y").clone();

        instance.reset();
        instance.update();

        double[] resetY = instance.marginalize("alice", "Y");
        // Prior: Y=a has P=0.8*0.5 + 0.3*0.5 = 0.55, Y=b has 0.45
        assertEquals(0.55, resetY[0], 1e-10);
        // After reset, should be different from the biased posterior
        assertTrue(Math.abs(biasedY[0] - resetY[0]) > 0.01,
            "Reset posteriors should differ from biased posteriors");
    }

    @Test
    void frozenTreeGuard_originalTreeNotFrozen() {
        JunctionTree original = sharedTree();
        var comp = MultiAgentBuilder.create("test")
            .sharedNetwork(original)
            .agent("alice").agent("bob")
            .build();
        new MultiAgentInstance(comp);
        assertFalse(original.isFrozen());
    }

    @Test
    void agents_returnsList() {
        var instance = new MultiAgentInstance(twoAgents());
        assertEquals(List.of("alice", "bob"), instance.agents());
    }

    @Test
    void getEvidenceVariables_returnsSetEvidence() {
        var instance = new MultiAgentInstance(twoAgents());
        instance.setEvidence("alice", "X", new double[]{0.9, 0.1});
        var evidence = instance.getEvidenceVariables("alice");
        assertTrue(evidence.containsKey("X"));
        assertArrayEquals(new double[]{0.9, 0.1}, evidence.get("X"), 1e-10);
    }

    @Test
    void perAgentMode_linkedVariableFlows() {
        JunctionTree tree1 = sharedTree();
        JunctionTree tree2 = sharedTree();
        var comp = MultiAgentBuilder.create("test")
            .agent("alice", tree1)
            .agent("bob", tree2)
            .link("alice", "X").to("bob", "X")
            .build();
        var instance = new MultiAgentInstance(comp);

        instance.setEvidence("alice", "X", new double[]{0.9, 0.1});
        instance.update();

        double[] bobY = instance.marginalize("bob", "Y");
        assertTrue(bobY[0] > bobY[1],
            "Alice's X evidence should flow to Bob via link");
    }
}
