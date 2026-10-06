package io.casehub.neocortex.knowledge;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpandedTermTest {

    @Test
    void canonicalAndVariantsPreserved() {
        var term = new ExpandedTerm("doll", Set.of("doll", "dolly", "dolls"));
        assertThat(term.canonical()).isEqualTo("doll");
        assertThat(term.variants()).containsExactlyInAnyOrder("doll", "dolly", "dolls");
    }

    @Test
    void variantsAreImmutable() {
        var mutable = new HashSet<>(Set.of("a", "b"));
        var term = new ExpandedTerm("a", mutable);
        assertThatThrownBy(() -> term.variants().add("c"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void canonicalCannotBeNull() {
        assertThatThrownBy(() -> new ExpandedTerm(null, Set.of("a")))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void passthroughReturnsSameTermAsCanonicalAndSoleVariant() {
        var term = ExpandedTerm.passthrough("dolly");
        assertThat(term.canonical()).isEqualTo("dolly");
        assertThat(term.variants()).containsExactly("dolly");
    }
}
