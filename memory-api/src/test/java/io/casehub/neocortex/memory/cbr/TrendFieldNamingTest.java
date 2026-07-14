package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrendFieldNamingTest {

    @Test
    void perField_slope() {
        assertThat(TrendFieldNaming.name("vitals", TrendType.SLOPE, "heart_rate"))
                .isEqualTo("vitals_slope_heart_rate");
    }

    @Test
    void perField_delta() {
        assertThat(TrendFieldNaming.name("vitals", TrendType.DELTA, "bp_systolic"))
                .isEqualTo("vitals_delta_bp_systolic");
    }

    @Test
    void perField_volatility() {
        assertThat(TrendFieldNaming.name("vitals", TrendType.VOLATILITY, "hr"))
                .isEqualTo("vitals_volatility_hr");
    }

    @Test
    void perField_acceleration() {
        assertThat(TrendFieldNaming.name("vitals", TrendType.ACCELERATION, "hr"))
                .isEqualTo("vitals_acceleration_hr");
    }

    @Test
    void perField_changePoints() {
        assertThat(TrendFieldNaming.name("vitals", TrendType.CHANGE_POINTS, "hr"))
                .isEqualTo("vitals_change_points_hr");
    }

    @Test
    void perTimeSeries_duration() {
        assertThat(TrendFieldNaming.name("vitals", TrendType.DURATION, null))
                .isEqualTo("vitals_duration");
    }

    @Test
    void perTimeSeries_observationCount() {
        assertThat(TrendFieldNaming.name("vitals", TrendType.OBSERVATION_COUNT, null))
                .isEqualTo("vitals_observation_count");
    }

    @Test
    void perField_nullInnerFieldName_rejected() {
        assertThatThrownBy(() -> TrendFieldNaming.name("vitals", TrendType.SLOPE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void perField_blankInnerFieldName_rejected() {
        assertThatThrownBy(() -> TrendFieldNaming.name("vitals", TrendType.SLOPE, "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
