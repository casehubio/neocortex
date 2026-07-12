package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarpingConstraintTest {

    @Test
    void unconstrained_creates() {
        var uc = new WarpingConstraint.Unconstrained();
        assertThat(uc).isNotNull();
    }

    @Test
    void sakoeChibaBand_validWindowSize() {
        var sc = new WarpingConstraint.SakoeChibaBand(5);
        assertThat(sc.windowSize()).isEqualTo(5);
    }

    @Test
    void sakoeChibaBand_windowSizeOne_accepted() {
        var sc = new WarpingConstraint.SakoeChibaBand(1);
        assertThat(sc.windowSize()).isEqualTo(1);
    }

    @Test
    void sakoeChibaBand_windowSizeZero_rejected() {
        assertThatThrownBy(() -> new WarpingConstraint.SakoeChibaBand(0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sakoeChibaBand_negativeWindowSize_rejected() {
        assertThatThrownBy(() -> new WarpingConstraint.SakoeChibaBand(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itakuraParallelogram_validMaxSlope() {
        var ip = new WarpingConstraint.ItakuraParallelogram(2.0);
        assertThat(ip.maxSlope()).isEqualTo(2.0);
    }

    @Test
    void itakuraParallelogram_slopeJustAboveOne() {
        var ip = new WarpingConstraint.ItakuraParallelogram(1.01);
        assertThat(ip.maxSlope()).isEqualTo(1.01);
    }

    @Test
    void itakuraParallelogram_slopeAtOne_rejected() {
        assertThatThrownBy(() -> new WarpingConstraint.ItakuraParallelogram(1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itakuraParallelogram_slopeBelowOne_rejected() {
        assertThatThrownBy(() -> new WarpingConstraint.ItakuraParallelogram(0.5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itakuraParallelogram_negativeSlope_rejected() {
        assertThatThrownBy(() -> new WarpingConstraint.ItakuraParallelogram(-1.0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
