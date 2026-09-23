package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecognizedGoalTest {

    @Test
    void constructionWithValidValues() {
        var goal = new RecognizedGoal("explore topic", "conversation", "medium", 0.85);
        assertThat(goal.description()).isEqualTo("explore topic");
        assertThat(goal.origin()).isEqualTo("conversation");
        assertThat(goal.suggestedHorizon()).isEqualTo("medium");
        assertThat(goal.confidence()).isEqualTo(0.85);
    }

    @Test
    void rejectsNullDescription() {
        assertThatThrownBy(() -> new RecognizedGoal(null, "conv", "short", 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullOrigin() {
        assertThatThrownBy(() -> new RecognizedGoal("desc", null, "short", 0.5))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsConfidenceOutOfRange() {
        assertThatThrownBy(() -> new RecognizedGoal("desc", "conv", "short", 1.5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RecognizedGoal("desc", "conv", "short", -0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
