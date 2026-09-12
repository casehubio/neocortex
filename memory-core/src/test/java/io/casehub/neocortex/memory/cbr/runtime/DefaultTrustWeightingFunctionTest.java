package io.casehub.neocortex.memory.cbr.runtime;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class DefaultTrustWeightingFunctionTest {

    private final DefaultTrustWeightingFunction fn = new DefaultTrustWeightingFunction(0.3, 0.5);

    @Test void authorityOnly_highTrust() {
        double result = fn.apply(0.9, 0.9, OptionalDouble.empty());
        assertThat(result).isCloseTo(0.9 * (1.0 - 0.3 + 0.3 * 0.9), offset(1e-9));
    }

    @Test void authorityOnly_lowTrust() {
        double result = fn.apply(0.9, 0.1, OptionalDouble.empty());
        assertThat(result).isCloseTo(0.9 * (1.0 - 0.3 + 0.3 * 0.1), offset(1e-9));
    }

    @Test void authorityOnly_zeroTrust() {
        double result = fn.apply(0.9, 0.0, OptionalDouble.empty());
        assertThat(result).isCloseTo(0.9 * 0.7, offset(1e-9));
    }

    @Test void trajectory_declining_appliesPenalty() {
        double result = fn.apply(0.9, 0.8, OptionalDouble.of(-0.4));
        double authority = 0.9 * (1.0 - 0.3 + 0.3 * 0.8);
        double trajectoryMultiplier = Math.max(0.5, 1.0 + 0.5 * (-0.4));
        assertThat(result).isCloseTo(authority * trajectoryMultiplier, offset(1e-9));
    }

    @Test void trajectory_declining_floor() {
        double result = fn.apply(0.9, 0.8, OptionalDouble.of(-2.0));
        double authority = 0.9 * (1.0 - 0.3 + 0.3 * 0.8);
        assertThat(result).isCloseTo(authority * 0.5, offset(1e-9));
    }

    @Test void trajectory_improving_ignored() {
        double authorityOnly = fn.apply(0.9, 0.8, OptionalDouble.empty());
        double withImproving = fn.apply(0.9, 0.8, OptionalDouble.of(0.3));
        assertThat(withImproving).isCloseTo(authorityOnly, offset(1e-9));
    }

    @Test void trajectory_absent_noEffect() {
        double authorityOnly = fn.apply(0.9, 0.8, OptionalDouble.empty());
        assertThat(authorityOnly).isCloseTo(0.9 * (1.0 - 0.3 + 0.3 * 0.8), offset(1e-9));
    }
}
