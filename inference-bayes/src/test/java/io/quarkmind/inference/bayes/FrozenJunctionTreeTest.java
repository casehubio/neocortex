package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FrozenJunctionTreeTest {

    private JunctionTree simpleTree() {
        return BayesNetworkBuilder.create("simple")
            .variable("A", "true", "false").prior(0.5, 0.5)
            .variable("B", "true", "false").given("A").cpt(0.8, 0.2, 0.3, 0.7)
            .buildJunctionTree();
    }

    @Test
    void unfrozenTree_allowsModification() {
        JunctionTree tree = simpleTree();
        assertFalse(tree.isFrozen());
        var instance = new BayesInstance<>(tree);
        assertDoesNotThrow(() ->
            instance.addLeafVariable("C", new String[]{"true", "false"},
                new double[][]{{0.6, 0.4}, {0.4, 0.6}}, "A"));
    }

    @Test
    void freeze_returnsSameTree() {
        JunctionTree tree = simpleTree();
        assertSame(tree, tree.freeze());
        assertTrue(tree.isFrozen());
    }

    @Test
    void frozenTree_setJunctionTreeNodes_throws() {
        JunctionTree tree = simpleTree().freeze();
        assertThrows(IllegalStateException.class, () ->
            tree.setJunctionTreeNodes(new JunctionTreeClique[0]));
    }

    @Test
    void frozenTree_setJunctionTreeSeparators_throws() {
        JunctionTree tree = simpleTree().freeze();
        assertThrows(IllegalStateException.class, () ->
            tree.setJunctionTreeSeparators(new JunctionTreeSeparator[0]));
    }

    @Test
    void frozenTree_addLeafVariable_throws() {
        JunctionTree tree = simpleTree().freeze();
        var instance = new BayesInstance<>(tree);
        assertThrows(IllegalStateException.class, () ->
            instance.addLeafVariable("C", new String[]{"true", "false"},
                new double[][]{{0.6, 0.4}, {0.4, 0.6}}, "A"));
    }

    @Test
    void frozenTree_removeLeafVariable_throws() {
        JunctionTree tree = simpleTree();
        var instance = new BayesInstance<>(tree);
        instance.addLeafVariable("C", new String[]{"true", "false"},
            new double[][]{{0.6, 0.4}, {0.4, 0.6}}, "A");
        tree.freeze();
        assertThrows(IllegalStateException.class, () ->
            instance.removeLeafVariable("C"));
    }

    @Test
    void defensiveCopy_producesSeparateTree() {
        JunctionTree original = simpleTree();
        JunctionTree copy = original.defensiveCopy();
        assertNotSame(original, copy);
        assertNotSame(original.getJunctionTreeNodes(), copy.getJunctionTreeNodes());
        assertNotSame(original.getJunctionTreeSeparators(), copy.getJunctionTreeSeparators());
    }

    @Test
    void defensiveCopy_freezingCopyDoesNotAffectOriginal() {
        JunctionTree original = simpleTree();
        JunctionTree copy = original.defensiveCopy();
        copy.freeze();
        assertTrue(copy.isFrozen());
        assertFalse(original.isFrozen());
    }

    @Test
    void defensiveCopy_inferenceProducesSameResults() {
        JunctionTree original = simpleTree();
        JunctionTree copy = original.defensiveCopy();

        var inst1 = new BayesInstance<>(original);
        inst1.setLikelyhood("A", new double[]{0.9, 0.1});
        inst1.globalUpdate();

        var inst2 = new BayesInstance<>(copy);
        inst2.setLikelyhood("A", new double[]{0.9, 0.1});
        inst2.globalUpdate();

        assertArrayEquals(
            inst1.marginalize("B").getDistribution(),
            inst2.marginalize("B").getDistribution(), 1e-10);
    }
}
