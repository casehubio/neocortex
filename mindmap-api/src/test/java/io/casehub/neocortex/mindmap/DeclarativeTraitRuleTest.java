package io.casehub.neocortex.mindmap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeclarativeTraitRuleTest {

    @Test
    void matchesLikePersonable() {
        var rule = new DeclarativeTraitRule("Personable",
            new RuleCondition.AnyOf(List.of(
                new RuleCondition.HasProperty("birthday"),
                new RuleCondition.HasProperty("role"),
                new RuleCondition.HasProperty("email"),
                new RuleCondition.HasProperty("phone"),
                new RuleCondition.HasEdgeTypes(Set.of("parent-of", "child-of", "works-at")))));

        assertThat(rule.traitName()).isEqualTo("Personable");
        assertThat(rule.matches(new TestNode(Map.of("birthday", "1940")), List.of())).isTrue();
        assertThat(rule.matches(new TestNode(Map.of()), List.of(new TestEdge("works-at")))).isTrue();
        assertThat(rule.matches(new TestNode(Map.of()), List.of())).isFalse();
    }

    @Test
    void matchesLikeAppointable() {
        var rule = new DeclarativeTraitRule("Appointable",
            new RuleCondition.PropertyEquals("eventKind", "scheduled"));

        assertThat(rule.matches(new TestNode(Map.of("eventKind", "scheduled")), List.of())).isTrue();
        assertThat(rule.matches(new TestNode(Map.of("eventKind", "anticipated")), List.of())).isFalse();
        assertThat(rule.matches(new TestNode(Map.of()), List.of())).isFalse();
    }

    @Test
    void matchesLikeProjectlike() {
        var rule = new DeclarativeTraitRule("Projectlike",
            new RuleCondition.AnyOf(List.of(
                new RuleCondition.HasProperty("status"),
                new RuleCondition.HasProperty("startDate"),
                new RuleCondition.HasProperty("endDate"),
                new RuleCondition.HasEdgeTypes(Set.of("contributes-to", "depends-on")))));

        assertThat(rule.matches(new TestNode(Map.of("status", "ACTIVE")), List.of())).isTrue();
        assertThat(rule.matches(new TestNode(Map.of()), List.of(new TestEdge("depends-on")))).isTrue();
        assertThat(rule.matches(new TestNode(Map.of()), List.of())).isFalse();
    }
}
