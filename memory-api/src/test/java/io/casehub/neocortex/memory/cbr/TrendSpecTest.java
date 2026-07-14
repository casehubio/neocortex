package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrendSpecTest {

    @Test
    void validConstruction() {
        var spec = new TrendSpec(Set.of(TrendType.SLOPE, TrendType.DELTA), ChronoUnit.DAYS);
        assertThat(spec.types()).containsExactlyInAnyOrder(TrendType.SLOPE, TrendType.DELTA);
        assertThat(spec.timeUnit()).isEqualTo(ChronoUnit.DAYS);
    }

    @Test
    void defaultTimeUnit_isHours() {
        var spec = new TrendSpec(Set.of(TrendType.SLOPE));
        assertThat(spec.timeUnit()).isEqualTo(ChronoUnit.HOURS);
    }

    @Test
    void emptyTypes_rejected() {
        assertThatThrownBy(() -> new TrendSpec(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullTypes_rejected() {
        assertThatThrownBy(() -> new TrendSpec(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullTimeUnit_rejected() {
        assertThatThrownBy(() -> new TrendSpec(Set.of(TrendType.SLOPE), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void defensivelyCopied() {
        var types = new java.util.HashSet<>(Set.of(TrendType.SLOPE));
        var spec = new TrendSpec(types);
        types.add(TrendType.DELTA);
        assertThat(spec.types()).containsExactly(TrendType.SLOPE);
    }
}
