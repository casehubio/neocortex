package io.casehub.neocortex.mindmap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class RuleConditionTest {

    @Test
    void hasProperty_trueWhenPresent() {
        var cond = new RuleCondition.HasProperty("birthday");
        assertThat(cond.evaluate(new TestNode(Map.of("birthday", "1940-01-01")), List.of())).isTrue();
    }

    @Test
    void hasProperty_falseWhenAbsent() {
        var cond = new RuleCondition.HasProperty("birthday");
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of())).isFalse();
    }

    @Test
    void propertyEquals_trueWhenMatches() {
        var cond = new RuleCondition.PropertyEquals("eventKind", "scheduled");
        assertThat(cond.evaluate(new TestNode(Map.of("eventKind", "scheduled")), List.of())).isTrue();
    }

    @Test
    void propertyEquals_falseWhenDiffers() {
        var cond = new RuleCondition.PropertyEquals("eventKind", "scheduled");
        assertThat(cond.evaluate(new TestNode(Map.of("eventKind", "anticipated")), List.of())).isFalse();
    }

    @Test
    void propertyEquals_falseWhenAbsent() {
        var cond = new RuleCondition.PropertyEquals("eventKind", "scheduled");
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of())).isFalse();
    }

    @Test
    void propertyIn_trueWhenValueInSet() {
        var cond = new RuleCondition.PropertyIn("status", Set.of("ACTIVE", "CONFIRMED"));
        assertThat(cond.evaluate(new TestNode(Map.of("status", "ACTIVE")), List.of())).isTrue();
    }

    @Test
    void propertyIn_falseWhenValueNotInSet() {
        var cond = new RuleCondition.PropertyIn("status", Set.of("ACTIVE", "CONFIRMED"));
        assertThat(cond.evaluate(new TestNode(Map.of("status", "CANCELLED")), List.of())).isFalse();
    }

    @Test
    void notHasProperty_trueWhenAbsent() {
        var cond = new RuleCondition.NotHasProperty("deletedAt");
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of())).isTrue();
    }

    @Test
    void notHasProperty_falseWhenPresent() {
        var cond = new RuleCondition.NotHasProperty("deletedAt");
        assertThat(cond.evaluate(new TestNode(Map.of("deletedAt", "2026-01-01")), List.of())).isFalse();
    }

    @Test
    void hasEdgeType_trueWhenEdgeExists() {
        var cond = new RuleCondition.HasEdgeType("parent-of");
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of(new TestEdge("parent-of")))).isTrue();
    }

    @Test
    void hasEdgeType_falseWhenNoMatch() {
        var cond = new RuleCondition.HasEdgeType("parent-of");
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of(new TestEdge("works-at")))).isFalse();
    }

    @Test
    void hasEdgeTypes_trueWhenAnyMatch() {
        var cond = new RuleCondition.HasEdgeTypes(Set.of("parent-of", "child-of"));
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of(new TestEdge("child-of")))).isTrue();
    }

    @Test
    void hasEdgeTypes_falseWhenNoneMatch() {
        var cond = new RuleCondition.HasEdgeTypes(Set.of("parent-of", "child-of"));
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of(new TestEdge("works-at")))).isFalse();
    }

    @Test
    void hasAnyEdge_trueWhenEdgesExist() {
        var cond = new RuleCondition.HasAnyEdge();
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of(new TestEdge("x")))).isTrue();
    }

    @Test
    void hasAnyEdge_falseWhenEmpty() {
        var cond = new RuleCondition.HasAnyEdge();
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of())).isFalse();
    }

    @Test
    void anyOf_trueWhenOneMatches() {
        var cond = new RuleCondition.AnyOf(List.of(
            new RuleCondition.HasProperty("birthday"),
            new RuleCondition.HasEdgeType("parent-of")));
        assertThat(cond.evaluate(new TestNode(Map.of("birthday", "x")), List.of())).isTrue();
    }

    @Test
    void anyOf_falseWhenNoneMatch() {
        var cond = new RuleCondition.AnyOf(List.of(
            new RuleCondition.HasProperty("birthday"),
            new RuleCondition.HasEdgeType("parent-of")));
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of())).isFalse();
    }

    @Test
    void allOf_trueWhenAllMatch() {
        var cond = new RuleCondition.AllOf(List.of(
            new RuleCondition.HasProperty("status"),
            new RuleCondition.HasEdgeType("contributes-to")));
        assertThat(cond.evaluate(
            new TestNode(Map.of("status", "ACTIVE")),
            List.of(new TestEdge("contributes-to")))).isTrue();
    }

    @Test
    void allOf_falseWhenOneFails() {
        var cond = new RuleCondition.AllOf(List.of(
            new RuleCondition.HasProperty("status"),
            new RuleCondition.HasEdgeType("contributes-to")));
        assertThat(cond.evaluate(new TestNode(Map.of("status", "ACTIVE")), List.of())).isFalse();
    }

    @Test
    void not_invertsTrue() {
        var cond = new RuleCondition.Not(new RuleCondition.HasProperty("deletedAt"));
        assertThat(cond.evaluate(new TestNode(Map.of()), List.of())).isTrue();
    }

    @Test
    void not_invertsFalse() {
        var cond = new RuleCondition.Not(new RuleCondition.HasProperty("deletedAt"));
        assertThat(cond.evaluate(new TestNode(Map.of("deletedAt", "x")), List.of())).isFalse();
    }

    @Test
    void nestedCombinators() {
        var cond = new RuleCondition.AllOf(List.of(
            new RuleCondition.PropertyIn("status", Set.of("ACTIVE", "IN_PROGRESS")),
            new RuleCondition.Not(new RuleCondition.HasProperty("completedAt")),
            new RuleCondition.HasEdgeType("contributes-to")));
        assertThat(cond.evaluate(
            new TestNode(Map.of("status", "ACTIVE")),
            List.of(new TestEdge("contributes-to")))).isTrue();
        assertThat(cond.evaluate(
            new TestNode(Map.of("status", "ACTIVE", "completedAt", "2026-01-01")),
            List.of(new TestEdge("contributes-to")))).isFalse();
    }
}
