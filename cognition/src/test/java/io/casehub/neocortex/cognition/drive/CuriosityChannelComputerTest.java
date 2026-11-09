package io.casehub.neocortex.cognition.drive;

import io.casehub.neocortex.mindmap.intelligence.CuriosityChannelComputer;
import io.casehub.neocortex.mindmap.intelligence.InformationGapScore;
import io.casehub.neocortex.mindmap.intelligence.InformationGapScore.GapType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CuriosityChannelComputerTest {

    private final CuriosityChannelComputer computer = new CuriosityChannelComputer();

    private static InformationGapScore gap(String name, double intensity, double entropy,
                                            int edges, double confidence, GapType type) {
        return new InformationGapScore("id-" + name, name, "concept",
                intensity, entropy, edges, confidence, type, 0.0);
    }

    @Test
    void noGaps_returnsNull() {
        assertThat(computer.compute(List.of(), 0.5)).isNull();
    }

    @Test
    void lowDeprivationBias_iType() {
        var gaps = List.of(gap("topic", 0.5, 0.9, 3, 0.4, GapType.PARTIAL_KNOWLEDGE));
        var channel = computer.compute(gaps, 0.1);
        assertThat(channel.hedonic()).isEqualTo(CuriosityChannel.HedonicType.I_TYPE);
    }

    @Test
    void highDeprivationBias_dType() {
        var gaps = List.of(gap("topic", 0.5, 0.9, 3, 0.4, GapType.PARTIAL_KNOWLEDGE));
        var channel = computer.compute(gaps, 0.8);
        assertThat(channel.hedonic()).isEqualTo(CuriosityChannel.HedonicType.D_TYPE);
    }

    @Test
    void midDeprivationBias_blended() {
        var gaps = List.of(gap("topic", 0.5, 0.9, 3, 0.4, GapType.PARTIAL_KNOWLEDGE));
        var channel = computer.compute(gaps, 0.5);
        assertThat(channel.hedonic()).isEqualTo(CuriosityChannel.HedonicType.BLENDED);
    }

    @Test
    void singleGap_specificFocus() {
        var gaps = List.of(gap("only-one", 0.8, 0.9, 5, 0.4, GapType.PARTIAL_KNOWLEDGE));
        var channel = computer.compute(gaps, 0.5);
        assertThat(channel.focus()).isEqualTo(CuriosityChannel.FocusType.SPECIFIC);
    }

    @Test
    void manyEqualGaps_diversiveFocus() {
        var gaps = List.of(
                gap("a", 0.5, 0.9, 3, 0.4, GapType.PARTIAL_KNOWLEDGE),
                gap("b", 0.5, 0.9, 3, 0.4, GapType.PARTIAL_KNOWLEDGE),
                gap("c", 0.5, 0.9, 3, 0.4, GapType.PARTIAL_KNOWLEDGE),
                gap("d", 0.5, 0.9, 3, 0.4, GapType.PARTIAL_KNOWLEDGE),
                gap("e", 0.5, 0.9, 3, 0.4, GapType.PARTIAL_KNOWLEDGE));
        var channel = computer.compute(gaps, 0.5);
        assertThat(channel.focus()).isEqualTo(CuriosityChannel.FocusType.DIVERSIVE);
    }

    @Test
    void oneDominantGapAmongMany_specificFocus() {
        var gaps = List.of(
                gap("dominant", 0.9, 1.0, 5, 0.5, GapType.PARTIAL_KNOWLEDGE),
                gap("minor1", 0.1, 0.3, 1, 0.1, GapType.CONNECTED_UNKNOWN),
                gap("minor2", 0.1, 0.3, 1, 0.1, GapType.CONNECTED_UNKNOWN));
        var channel = computer.compute(gaps, 0.5);
        assertThat(channel.focus()).isEqualTo(CuriosityChannel.FocusType.SPECIFIC);
    }

    @Test
    void highConfidenceGaps_highComprehension() {
        var gaps = List.of(
                gap("almost", 0.3, 0.5, 5, 0.7, GapType.TIP_OF_TONGUE));
        var channel = computer.compute(gaps, 0.5);
        assertThat(channel.comprehension()).isGreaterThanOrEqualTo(0.6);
    }

    @Test
    void lowConfidenceGaps_lowComprehension() {
        var gaps = List.of(
                gap("unknown", 0.3, 0.4, 2, 0.15, GapType.CONNECTED_UNKNOWN));
        var channel = computer.compute(gaps, 0.5);
        assertThat(channel.comprehension()).isLessThan(0.3);
    }

    @Test
    void moderateConfidence_peakGapProximity() {
        double entropyAt05 = 1.0; // binary entropy peaks at 1.0 for confidence=0.5
        var gaps = List.of(gap("sweet-spot", 0.8, entropyAt05, 5, 0.5, GapType.PARTIAL_KNOWLEDGE));
        var channel = computer.compute(gaps, 0.5);
        assertThat(channel.gapProximity()).isGreaterThan(0.8);
    }

    @Test
    void extremeConfidence_lowGapProximity() {
        double entropyAt005 = 0.286; // binary entropy is low at extremes
        var gaps = List.of(gap("extreme", 0.2, entropyAt005, 2, 0.05, GapType.CONNECTED_UNKNOWN));
        var channel = computer.compute(gaps, 0.5);
        assertThat(channel.gapProximity()).isLessThan(0.5);
    }
}
