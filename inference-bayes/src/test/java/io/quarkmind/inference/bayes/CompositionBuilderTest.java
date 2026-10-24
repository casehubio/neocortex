package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompositionBuilderTest {

    private JunctionTree simpleTree() {
        return BayesNetworkBuilder.create()
            .variable("X", "low", "high").prior(0.5, 0.5)
            .variable("Y", "low", "high").given("X").cpt(0.8, 0.2, 0.3, 0.7)
            .buildJunctionTree();
    }

    @Test
    void simpleChain_buildsSuccessfully() {
        JunctionTree treeA = simpleTree();
        JunctionTree treeB = simpleTree();

        NetworkComposition comp = CompositionBuilder.create("test")
            .slot("A", treeA)
            .slot("B", treeB)
            .link("A", "Y").to("B", "X")
            .build();

        assertEquals("test", comp.name());
        assertEquals(2, comp.slots().size());
        assertEquals(1, comp.links().size());
    }

    @Test
    void sameNameLink_usesShorthand() {
        JunctionTree tree = simpleTree();

        NetworkComposition comp = CompositionBuilder.create("test")
            .slot("A", tree)
            .slot("B", tree)
            .link("A", "Y").to("B")
            .build();

        assertEquals("Y", comp.links().get(0).variableMapping().get("Y"));
    }

    @Test
    void defaultTransferMode_isPosterior() {
        JunctionTree tree = simpleTree();

        NetworkComposition comp = CompositionBuilder.create("test")
            .slot("A", tree)
            .slot("B", tree)
            .link("A", "Y").to("B", "X")
            .build();

        assertEquals(TransferMode.POSTERIOR, comp.links().get(0).transferMode());
    }

    @Test
    void explicitMapMode() {
        JunctionTree tree = simpleTree();

        NetworkComposition comp = CompositionBuilder.create("test")
            .slot("A", tree)
            .slot("B", tree)
            .link("A", "Y").to("B", "X").mode(TransferMode.MAP)
            .build();

        assertEquals(TransferMode.MAP, comp.links().get(0).transferMode());
    }

    @Test
    void slotWithVariants() {
        JunctionTree defaultTree = simpleTree();
        JunctionTree variantTree = simpleTree();

        NetworkComposition comp = CompositionBuilder.create("test")
            .slot("A", defaultTree)
                .variant("ctx1", variantTree)
            .slot("B", simpleTree())
            .link("A", "Y").to("B", "X")
            .build();

        assertEquals(1, comp.slotByName("A").variants().size());
        assertTrue(comp.slotByName("A").variants().containsKey("ctx1"));
    }

    @Test
    void duplicateSlotName_throws() {
        JunctionTree tree = simpleTree();

        assertThrows(IllegalArgumentException.class, () ->
            CompositionBuilder.create("test")
                .slot("A", tree)
                .slot("A", tree)
                .build());
    }

    @Test
    void multipleLinks_buildCorrectly() {
        JunctionTree tree = simpleTree();

        NetworkComposition comp = CompositionBuilder.create("test")
            .slot("A", tree)
            .slot("B", tree)
            .slot("C", tree)
            .link("A", "Y").to("B", "X")
            .link("A", "Y").to("C", "X").mode(TransferMode.MAP)
            .build();

        assertEquals(2, comp.links().size());
        assertEquals(TransferMode.POSTERIOR, comp.links().get(0).transferMode());
        assertEquals(TransferMode.MAP, comp.links().get(1).transferMode());
    }
}
