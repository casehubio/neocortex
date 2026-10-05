package io.casehub.neocortex.cognition.appraisal;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class SecResultTest {

    @Test
    void dimensionReturnsValueWhenPresent() {
        var result = new SecResult("relevance", Map.of("novelty", 0.8, "urgency", 0.5));
        assertThat(result.dimension("novelty")).isCloseTo(0.8, within(0.001));
    }

    @Test
    void dimensionReturnsZeroWhenAbsent() {
        var result = new SecResult("relevance", Map.of("novelty", 0.8));
        assertThat(result.dimension("missing")).isCloseTo(0.0, within(0.001));
    }

    @Test
    void factoryMethodsSingleDimension() {
        var result = SecResult.of("test", "dim1", 0.7);
        assertThat(result.checkName()).isEqualTo("test");
        assertThat(result.dimension("dim1")).isCloseTo(0.7, within(0.001));
    }

    @Test
    void factoryMethodsTwoDimensions() {
        var result = SecResult.of("test", "a", 0.3, "b", 0.9);
        assertThat(result.dimension("a")).isCloseTo(0.3, within(0.001));
        assertThat(result.dimension("b")).isCloseTo(0.9, within(0.001));
    }

    @Test
    void factoryMethodsThreeDimensions() {
        var result = SecResult.of("test", "a", 0.1, "b", 0.2, "c", 0.3);
        assertThat(result.dimension("a")).isCloseTo(0.1, within(0.001));
        assertThat(result.dimension("c")).isCloseTo(0.3, within(0.001));
    }

    @Test
    void nullDimensionsDefaultsToEmpty() {
        var result = new SecResult("test", null);
        assertThat(result.dimensions()).isEmpty();
    }

    @Test
    void dimensionsAreImmutable() {
        var result = new SecResult("test", Map.of("x", 1.0));
        assertThatThrownBy(() -> result.dimensions().put("y", 2.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
