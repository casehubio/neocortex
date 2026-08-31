package io.casehub.neocortex.mindmap;

import io.casehub.neocortex.cognitive.Confidence;
import io.casehub.neocortex.cognitive.ConfidenceOrigin;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class NodeUpdateTest {

    @Test
    void empty_createsWithDefaults() {
        var u = NodeUpdate.empty();
        assertThat(u.name()).isNull();
        assertThat(u.confidence()).isNull();
        assertThat(u.traitsToAdd()).isEmpty();
        assertThat(u.traitsToRemove()).isEmpty();
        assertThat(u.refsToAdd()).isEmpty();
        assertThat(u.refsToRemove()).isEmpty();
        assertThat(u.validFrom()).isNull();
        assertThat(u.validUntil()).isNull();
        assertThat(u.pleasure()).isNull();
        assertThat(u.arousal()).isNull();
        assertThat(u.dominance()).isNull();
        assertThat(u.propertiesToSet()).isEmpty();
        assertThat(u.propertiesToRemove()).isEmpty();
    }

    @Test
    void withName_returnsNewInstance() {
        var u = NodeUpdate.empty();
        var u2 = u.withName("New Name");
        assertThat(u2.name()).isEqualTo("New Name");
        assertThat(u.name()).isNull();
    }

    @Test
    void chaining_setsMultipleFields() {
        var conf = new Confidence(ConfidenceOrigin.STATED, 0.9, null);
        var u = NodeUpdate.empty()
            .withName("Alice")
            .withConfidence(conf)
            .withTraitsToAdd(Set.of("person"))
            .withPad(0.8, 0.3, 0.5)
            .withPropertiesToSet(Map.of("role", "lead"));
        assertThat(u.name()).isEqualTo("Alice");
        assertThat(u.confidence()).isEqualTo(conf);
        assertThat(u.traitsToAdd()).containsExactly("person");
        assertThat(u.pleasure()).isEqualTo(0.8);
        assertThat(u.propertiesToSet()).containsEntry("role", "lead");
    }

    @Test
    void withTraitsToRemove_setsField() {
        var u = NodeUpdate.empty().withTraitsToRemove(Set.of("old"));
        assertThat(u.traitsToRemove()).containsExactly("old");
    }

    @Test
    void withRefsToAdd_setsField() {
        var ref = new NodeRef("github", "123", null);
        var u = NodeUpdate.empty().withRefsToAdd(Set.of(ref));
        assertThat(u.refsToAdd()).containsExactly(ref);
    }

    @Test
    void withPropertiesToRemove_setsField() {
        var u = NodeUpdate.empty().withPropertiesToRemove(Set.of("obsolete"));
        assertThat(u.propertiesToRemove()).containsExactly("obsolete");
    }

    @Test
    void withValidFrom_setsField() {
        var now = Instant.now();
        var u = NodeUpdate.empty().withValidFrom(now);
        assertThat(u.validFrom()).isEqualTo(now);
    }
}
