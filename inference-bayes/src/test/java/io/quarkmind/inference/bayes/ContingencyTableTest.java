package io.quarkmind.inference.bayes;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContingencyTableTest {

    @Test
    void buildMarginal_twoVariables() {
        List<Map<String, String>> data = List.of(
            Map.of("X", "a", "Y", "c"),
            Map.of("X", "a", "Y", "c"),
            Map.of("X", "a", "Y", "d"),
            Map.of("X", "b", "Y", "c"),
            Map.of("X", "b", "Y", "d"),
            Map.of("X", "b", "Y", "d")
        );

        List<ContingencyTable> tables = ContingencyTable.build(
            "X", "Y", Set.of(), data);
        assertEquals(1, tables.size());

        ContingencyTable t = tables.get(0);
        assertEquals(6, t.total());
        assertEquals(2, t.counts().length);
        assertEquals(2, t.counts()[0].length);
        assertEquals(2, t.counts()[0][0]); // a,c
        assertEquals(1, t.counts()[0][1]); // a,d
        assertEquals(1, t.counts()[1][0]); // b,c
        assertEquals(2, t.counts()[1][1]); // b,d
    }

    @Test
    void buildConditional_withConditioningSet() {
        List<Map<String, String>> data = List.of(
            Map.of("X", "a", "Y", "c", "Z", "z0"),
            Map.of("X", "a", "Y", "d", "Z", "z0"),
            Map.of("X", "b", "Y", "c", "Z", "z0"),
            Map.of("X", "a", "Y", "c", "Z", "z1"),
            Map.of("X", "b", "Y", "d", "Z", "z1"),
            Map.of("X", "b", "Y", "d", "Z", "z1")
        );

        List<ContingencyTable> tables = ContingencyTable.build(
            "X", "Y", Set.of("Z"), data);
        assertEquals(2, tables.size());

        ContingencyTable z0 = tables.get(0);
        assertEquals(3, z0.total());
        ContingencyTable z1 = tables.get(1);
        assertEquals(3, z1.total());
    }

    @Test
    void buildForVariable_noParents() {
        List<Map<String, String>> data = List.of(
            Map.of("X", "a"),
            Map.of("X", "a"),
            Map.of("X", "b")
        );

        ContingencyTable t = ContingencyTable.buildForVariable(
            "X", List.of(), data);
        assertEquals(1, t.counts().length);
        assertEquals(2, t.counts()[0].length);
        assertEquals(2, t.counts()[0][0]); // a
        assertEquals(1, t.counts()[0][1]); // b
        assertEquals(3, t.total());
    }

    @Test
    void buildForVariable_withParents() {
        List<Map<String, String>> data = List.of(
            Map.of("A", "a0", "B", "b0", "C", "c0"),
            Map.of("A", "a0", "B", "b0", "C", "c0"),
            Map.of("A", "a0", "B", "b1", "C", "c1"),
            Map.of("A", "a1", "B", "b0", "C", "c0"),
            Map.of("A", "a1", "B", "b1", "C", "c1"),
            Map.of("A", "a1", "B", "b1", "C", "c1")
        );

        ContingencyTable t = ContingencyTable.buildForVariable(
            "C", List.of("A", "B"), data);
        assertEquals(4, t.counts().length);
        assertEquals(2, t.counts()[0].length);
        assertEquals(2, t.counts()[0][0]); // (a0,b0): c0=2
        assertEquals(0, t.counts()[0][1]); // (a0,b0): c1=0
    }

    @Test
    void skipsMissingValues() {
        List<Map<String, String>> data = List.of(
            Map.of("X", "a", "Y", "c"),
            Map.of("X", "a"),        // Y missing
            Map.of("Y", "d")         // X missing
        );

        List<ContingencyTable> tables = ContingencyTable.build(
            "X", "Y", Set.of(), data);
        assertEquals(1, tables.size());
        assertEquals(1, tables.get(0).total());
    }
}
