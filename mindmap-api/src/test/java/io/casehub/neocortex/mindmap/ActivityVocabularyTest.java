package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityVocabularyTest {

    @Test
    void vocabulary_containsThreeEdgeTypes() {
        var vocab = ActivityVocabulary.ACTIVITY_VOCABULARY;
        assertThat(vocab.edgeTypes()).hasSize(3);
    }

    @Test
    void participated_hasAliases() {
        var vocab = ActivityVocabulary.ACTIVITY_VOCABULARY;
        var participated = vocab.edgeTypes().stream()
            .filter(e -> "participated".equals(e.canonical()))
            .findFirst().orElseThrow();
        assertThat(participated.aliases()).containsExactlyInAnyOrder("took-part-in", "attended");
    }

    @Test
    void at_hasAliases() {
        var vocab = ActivityVocabulary.ACTIVITY_VOCABULARY;
        var at = vocab.edgeTypes().stream()
            .filter(e -> "at".equals(e.canonical()))
            .findFirst().orElseThrow();
        assertThat(at.aliases()).containsExactlyInAnyOrder("held-at", "located-at");
    }

    @Test
    void occasion_hasAliases() {
        var vocab = ActivityVocabulary.ACTIVITY_VOCABULARY;
        var occasion = vocab.edgeTypes().stream()
            .filter(e -> "occasion".equals(e.canonical()))
            .findFirst().orElseThrow();
        assertThat(occasion.aliases()).containsExactlyInAnyOrder("linked-event", "calendar-event");
    }
}
