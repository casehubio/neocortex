package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GoalDecompositionResultTest {

    @Test
    void emptyConstant() {
        assertThat(GoalDecompositionResult.EMPTY.subGoals()).isEmpty();
        assertThat(GoalDecompositionResult.EMPTY.relationships()).isEmpty();
    }

    @Test
    void constructionWithValues() {
        var result = new GoalDecompositionResult(
                List.of(new GoalDecompositionResult.SubGoal("sub", "short", Map.of())),
                List.of(new GoalDecompositionResult.GoalRelationship("parent", "sub", "decomposes-into")));
        assertThat(result.subGoals()).hasSize(1);
        assertThat(result.subGoals().getFirst().description()).isEqualTo("sub");
        assertThat(result.relationships()).hasSize(1);
        assertThat(result.relationships().getFirst().edgeType()).isEqualTo("decomposes-into");
    }

    @Test
    void subGoalDefaultsEmptyProperties() {
        var sg = new GoalDecompositionResult.SubGoal("desc", "medium", null);
        assertThat(sg.properties()).isEmpty();
    }

    @Test
    void listsAreImmutable() {
        var result = new GoalDecompositionResult(
                List.of(new GoalDecompositionResult.SubGoal("a", "short", Map.of())),
                List.of());
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> result.subGoals().add(new GoalDecompositionResult.SubGoal("b", "long", Map.of())));
    }
}
