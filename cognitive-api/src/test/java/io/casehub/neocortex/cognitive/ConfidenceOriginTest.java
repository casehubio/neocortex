package io.casehub.neocortex.cognitive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceOriginTest {

    @Test
    void allValuesPresent() {
        assertThat(ConfidenceOrigin.values()).containsExactly(
                ConfidenceOrigin.STATED, ConfidenceOrigin.INFERRED,
                ConfidenceOrigin.SPECULATED, ConfidenceOrigin.UNKNOWN);
    }

    @Test
    void valueOfRoundTrips() {
        for (ConfidenceOrigin origin : ConfidenceOrigin.values()) {
            assertThat(ConfidenceOrigin.valueOf(origin.name())).isEqualTo(origin);
        }
    }
}
