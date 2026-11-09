package io.casehub.neocortex.cognition.regulation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RegulationContextTest {

    @Test
    void recordFieldsAccessible() {
        var ctx = new RegulationContext("library", List.of("dick-dastardly"), "treasure");
        assertThat(ctx.location()).isEqualTo("library");
        assertThat(ctx.interactingAgents()).containsExactly("dick-dastardly");
        assertThat(ctx.topic()).isEqualTo("treasure");
    }

    @Test
    void nullFieldsAllowed() {
        var ctx = new RegulationContext(null, null, null);
        assertThat(ctx.location()).isNull();
        assertThat(ctx.interactingAgents()).isNull();
        assertThat(ctx.topic()).isNull();
    }

    @Test
    void matchesLocation() {
        var ctx = new RegulationContext("library", null, null);
        assertThat(ctx.matchesDimension("library", ProactiveAvoidanceMode.LOCATION_ONLY)).isTrue();
        assertThat(ctx.matchesDimension("kitchen", ProactiveAvoidanceMode.LOCATION_ONLY)).isFalse();
    }
}
