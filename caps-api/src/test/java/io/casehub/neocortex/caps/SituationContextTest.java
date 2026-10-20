package io.casehub.neocortex.caps;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SituationContextTest {

    @Test
    void from_buildsConfidenceMapFromActivations() {
        var activations = List.of(
            new SituationActivation("reward", 0.8),
            new SituationActivation("rejection", 0.5));

        var ctx = SituationContext.from(activations);

        assertThat(ctx.activations()).hasSize(2);
        assertThat(ctx.confidenceMap()).containsEntry("reward", 0.8);
        assertThat(ctx.confidenceMap()).containsEntry("rejection", 0.5);
    }

    @Test
    void empty_hasNoActivations() {
        assertThat(SituationContext.EMPTY.activations()).isEmpty();
        assertThat(SituationContext.EMPTY.confidenceMap()).isEmpty();
        assertThat(SituationContext.EMPTY.isEmpty()).isTrue();
    }

    @Test
    void nonEmpty_reportsNotEmpty() {
        var ctx = SituationContext.from(List.of(new SituationActivation("x", 0.5)));
        assertThat(ctx.isEmpty()).isFalse();
    }
}
