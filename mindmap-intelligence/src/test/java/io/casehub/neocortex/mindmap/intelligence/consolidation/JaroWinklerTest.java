package io.casehub.neocortex.mindmap.intelligence.consolidation;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JaroWinklerTest {

    @Test
    void identicalStrings_returnsOne() {
        assertThat(JaroWinkler.similarity("Alice", "Alice")).isEqualTo(1.0);
    }

    @Test
    void completelyDifferent_returnsLow() {
        assertThat(JaroWinkler.similarity("abc", "xyz")).isLessThan(0.5);
    }

    @Test
    void similarNames_returnsHigh() {
        assertThat(JaroWinkler.similarity("Mark Proctor", "Mark Procter"))
            .isGreaterThan(0.9);
    }

    @Test
    void emptyStrings_returnsOne() {
        assertThat(JaroWinkler.similarity("", "")).isEqualTo(1.0);
    }

    @Test
    void oneEmpty_returnsZero() {
        assertThat(JaroWinkler.similarity("abc", "")).isEqualTo(0.0);
    }

    @Test
    void transposition_handledCorrectly() {
        double score = JaroWinkler.similarity("Martha", "Marhta");
        assertThat(score).isCloseTo(0.961, within(0.01));
    }
}
