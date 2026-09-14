package io.casehub.neocortex.cognitive.observability;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CognitionInspectResultTest {

    @Test
    void shouldBuildInspectResult() {
        var stats = List.of(new CognitionInspectResult.SubgraphStat(
            "sg1", "person", 10, 15, 0.72,
            Map.of("Personable", 3L, "Projectlike", 2L)));
        var histogram = Map.of(
            "0.0-0.2", 2, "0.2-0.4", 3, "0.4-0.6", 5,
            "0.6-0.8", 8, "0.8-1.0", 7);
        var result = new CognitionInspectResult(stats, histogram);

        assertEquals(1, result.subgraphStats().size());
        assertEquals(10, result.subgraphStats().getFirst().nodeCount());
        assertEquals(5, result.confidenceHistogram().get("0.4-0.6"));
    }

    @Test
    void shouldPreserveTraitDistribution() {
        var stats = List.of(new CognitionInspectResult.SubgraphStat(
            "sg1", "concept", 5, 3, 0.5,
            Map.of("Belieflike", 2L, "Intentionlike", 1L)));
        var result = new CognitionInspectResult(stats, Map.of());

        assertEquals(2L, result.subgraphStats().getFirst().traitDistribution().get("Belieflike"));
    }
}
