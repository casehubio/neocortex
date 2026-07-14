package io.casehub.neocortex.memory.cbr;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class AdaptedPlanTest {

    @Test void validPlan() {
        var step = new AdaptedStep("b", "cap", "w", "SUCCESS", 0,
                Map.of(), AdaptationAction.RETAINED, null);
        var plan = new AdaptedPlan(List.of(step));
        assertThat(plan.steps()).hasSize(1);
        assertThat(plan.steps().getFirst().bindingName()).isEqualTo("b");
    }

    @Test void nullStepsRejected() {
        assertThatNullPointerException().isThrownBy(() -> new AdaptedPlan(null));
    }

    @Test void stepsDefensivelyCopied() {
        var step = new AdaptedStep("b", "cap", "w", "SUCCESS", 0,
                Map.of(), AdaptationAction.RETAINED, null);
        var list = new ArrayList<>(List.of(step));
        var plan = new AdaptedPlan(list);
        list.add(new AdaptedStep("b2", "cap2", "w2", "SUCCESS", 1,
                Map.of(), AdaptationAction.ADDED, "test"));
        assertThat(plan.steps()).hasSize(1);
    }

    @Test void emptyStepsAllowed() {
        var plan = new AdaptedPlan(List.of());
        assertThat(plan.steps()).isEmpty();
    }
}
