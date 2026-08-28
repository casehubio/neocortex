package io.casehub.neocortex.mindmap.intelligence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CuriositySignalTest {

    @Test
    void signalHoldsAllFields() {
        var signal = new CuriositySignal(
            SignalCategory.STRUCTURAL, 0.75, "node-1", "sg-1",
            "What is Alice's connection?", "Orphan node: Alice");
        assertThat(signal.category()).isEqualTo(SignalCategory.STRUCTURAL);
        assertThat(signal.score()).isEqualTo(0.75);
        assertThat(signal.targetNodeId()).isEqualTo("node-1");
        assertThat(signal.targetSubgraphId()).isEqualTo("sg-1");
        assertThat(signal.question()).isEqualTo("What is Alice's connection?");
        assertThat(signal.description()).isEqualTo("Orphan node: Alice");
    }

    @Test
    void signalWithNullTargets() {
        var signal = new CuriositySignal(
            SignalCategory.QUALITY, 0.5, null, "sg-1",
            "Question", "Description");
        assertThat(signal.targetNodeId()).isNull();
    }

    @Test
    void allSignalCategories() {
        assertThat(SignalCategory.values()).containsExactlyInAnyOrder(
            SignalCategory.STRUCTURAL, SignalCategory.QUALITY,
            SignalCategory.TEMPORAL, SignalCategory.CENTRALITY,
            SignalCategory.PROXIMITY);
    }
}
