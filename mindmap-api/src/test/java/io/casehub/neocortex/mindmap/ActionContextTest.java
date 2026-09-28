package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.PadProjection;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class ActionContextTest {

    private static final Instant NOW = Instant.now();

    @Test
    void validContextCreated() {
        var ctx = new ActionContext("agent-a", "agent-a", "t1", "turn-1",
                "completed task", "planning", ActionOutcome.SUCCESS, 0.8,
                PadProjection.NEUTRAL, AppraisalWeights.NEUTRAL, NOW);
        assertThat(ctx.actingAgentId()).isEqualTo("agent-a");
        assertThat(ctx.isSelfAction()).isTrue();
    }

    @Test
    void otherAgentDetected() {
        var ctx = new ActionContext("agent-b", "agent-a", "t1", "turn-1",
                "helped with task", null, ActionOutcome.SUCCESS, 0.5,
                PadProjection.NEUTRAL, AppraisalWeights.NEUTRAL, NOW);
        assertThat(ctx.isSelfAction()).isFalse();
    }

    @Test
    void goalRelevanceBelowMinThrows() {
        assertThatThrownBy(() -> new ActionContext("a", "a", "t1", "turn-1",
                "desc", null, ActionOutcome.NEUTRAL, -1.1,
                PadProjection.NEUTRAL, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void goalRelevanceAboveMaxThrows() {
        assertThatThrownBy(() -> new ActionContext("a", "a", "t1", "turn-1",
                "desc", null, ActionOutcome.NEUTRAL, 1.1,
                PadProjection.NEUTRAL, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullWeightsDefaultsToNeutral() {
        var ctx = new ActionContext("a", "a", "t1", "turn-1",
                "desc", null, ActionOutcome.NEUTRAL, 0.0,
                PadProjection.NEUTRAL, null, NOW);
        assertThat(ctx.weights()).isEqualTo(AppraisalWeights.NEUTRAL);
    }

    @Test
    void nullActingAgentThrows() {
        assertThatThrownBy(() -> new ActionContext(null, "a", "t1", "turn-1",
                "desc", null, ActionOutcome.NEUTRAL, 0.0,
                PadProjection.NEUTRAL, null, NOW))
                .isInstanceOf(NullPointerException.class);
    }
}
