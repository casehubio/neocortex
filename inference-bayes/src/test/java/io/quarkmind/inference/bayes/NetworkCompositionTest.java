package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NetworkCompositionTest {

    private JunctionTree twoVarTree() {
        return BayesNetworkBuilder.create()
            .variable("X", "low", "high").prior(0.5, 0.5)
            .variable("Y", "low", "high").given("X").cpt(0.8, 0.2, 0.3, 0.7)
            .buildJunctionTree();
    }

    @Test
    void chainTopology_orderIsABeforeB() {
        JunctionTree treeA = twoVarTree();
        JunctionTree treeB = twoVarTree();

        var composition = new NetworkComposition("chain",
            List.of(new NetworkSlot("B", treeB), new NetworkSlot("A", treeA)),
            List.of(new NetworkLink("A", "B", Map.of("Y", "X"), TransferMode.POSTERIOR)));

        List<String> order = composition.topologicalOrder();
        assertTrue(order.indexOf("A") < order.indexOf("B"),
            "A must come before B, got: " + order);
    }

    @Test
    void diamondTopology_respectsDependencies() {
        JunctionTree tree = twoVarTree();

        var composition = new NetworkComposition("diamond",
            List.of(
                new NetworkSlot("A", tree),
                new NetworkSlot("B", tree),
                new NetworkSlot("C", tree),
                new NetworkSlot("D", tree)
            ),
            List.of(
                new NetworkLink("A", "B", Map.of("Y", "X"), TransferMode.POSTERIOR),
                new NetworkLink("A", "C", Map.of("Y", "X"), TransferMode.POSTERIOR),
                new NetworkLink("B", "D", Map.of("Y", "X"), TransferMode.POSTERIOR),
                new NetworkLink("C", "D", Map.of("Y", "X"), TransferMode.POSTERIOR)
            ));

        List<String> order = composition.topologicalOrder();
        assertTrue(order.indexOf("A") < order.indexOf("B"));
        assertTrue(order.indexOf("A") < order.indexOf("C"));
        assertTrue(order.indexOf("B") < order.indexOf("D"));
        assertTrue(order.indexOf("C") < order.indexOf("D"));
    }

    @Test
    void cyclicLinks_throws() {
        JunctionTree tree = twoVarTree();

        assertThrows(IllegalArgumentException.class, () ->
            new NetworkComposition("cycle",
                List.of(new NetworkSlot("A", tree), new NetworkSlot("B", tree)),
                List.of(
                    new NetworkLink("A", "B", Map.of("Y", "X"), TransferMode.POSTERIOR),
                    new NetworkLink("B", "A", Map.of("Y", "X"), TransferMode.POSTERIOR)
                )));
    }

    @Test
    void duplicateSlotNames_throws() {
        JunctionTree tree = twoVarTree();

        assertThrows(IllegalArgumentException.class, () ->
            new NetworkComposition("dup",
                List.of(new NetworkSlot("A", tree), new NetworkSlot("A", tree)),
                List.of()));
    }

    @Test
    void linkReferencesUnknownSlot_throws() {
        JunctionTree tree = twoVarTree();

        assertThrows(IllegalArgumentException.class, () ->
            new NetworkComposition("bad-ref",
                List.of(new NetworkSlot("A", tree)),
                List.of(new NetworkLink("A", "Z", Map.of("Y", "X"), TransferMode.POSTERIOR))));
    }

    @Test
    void noLinks_allSlotsInOrder() {
        JunctionTree tree = twoVarTree();

        var composition = new NetworkComposition("independent",
            List.of(new NetworkSlot("A", tree), new NetworkSlot("B", tree)),
            List.of());

        assertEquals(2, composition.topologicalOrder().size());
    }

    @Test
    void slotByName_returnsCorrectSlot() {
        JunctionTree tree = twoVarTree();

        var composition = new NetworkComposition("test",
            List.of(new NetworkSlot("A", tree), new NetworkSlot("B", tree)),
            List.of());

        assertEquals("A", composition.slotByName("A").name());
        assertEquals("B", composition.slotByName("B").name());
    }

    @Test
    void slotByName_unknownThrows() {
        JunctionTree tree = twoVarTree();

        var composition = new NetworkComposition("test",
            List.of(new NetworkSlot("A", tree)),
            List.of());

        assertThrows(IllegalArgumentException.class, () ->
            composition.slotByName("Z"));
    }
}
