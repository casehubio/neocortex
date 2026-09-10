package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptiveSearchConfigTest {

    @Test
    void defaults_returnsValidConfig() {
        var config = AdaptiveSearchConfig.defaults();
        assertThat(config.scoreFloor()).isEqualTo(0.3);
        assertThat(config.gapThreshold()).isEqualTo(0.15);
        assertThat(config.minResults()).isEqualTo(3);
        assertThat(config.overfetchMultiplier()).isEqualTo(2.0);
    }

    @Test
    void rejectsNegativeScoreFloor() {
        assertThatThrownBy(() -> new AdaptiveSearchConfig(-0.1, 0.15, 3, 2.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsScoreFloorAboveOne() {
        assertThatThrownBy(() -> new AdaptiveSearchConfig(1.1, 0.15, 3, 2.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeGapThreshold() {
        assertThatThrownBy(() -> new AdaptiveSearchConfig(0.3, -0.1, 3, 2.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeMinResults() {
        assertThatThrownBy(() -> new AdaptiveSearchConfig(0.3, 0.15, -1, 2.0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverfetchMultiplierBelowOne() {
        assertThatThrownBy(() -> new AdaptiveSearchConfig(0.3, 0.15, 3, 0.5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsBoundaryValues() {
        var config = new AdaptiveSearchConfig(0.0, 0.0, 0, 1.0);
        assertThat(config.scoreFloor()).isZero();
        assertThat(config.minResults()).isZero();
    }
}
