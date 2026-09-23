package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoalVocabularyTest {

    @Test
    void vocabularyHasFiveEdgeTypes() {
        var vocab = GoalVocabulary.GOAL_VOCABULARY;
        assertThat(vocab.edgeTypes()).hasSize(5);
        assertThat(vocab.edgeTypes().stream().map(EdgeTypeDefinition::canonical))
                .containsExactlyInAnyOrder("enables", "blocks", "requires",
                        "contributes-to", "decomposes-into");
    }

    @Test
    void enablesHasAliases() {
        var enables = GoalVocabulary.GOAL_VOCABULARY.edgeTypes().stream()
                .filter(e -> e.canonical().equals("enables")).findFirst().orElseThrow();
        assertThat(enables.aliases()).contains("makes-possible", "unblocks");
    }

    @Test
    void blocksHasAliases() {
        var blocks = GoalVocabulary.GOAL_VOCABULARY.edgeTypes().stream()
                .filter(e -> e.canonical().equals("blocks")).findFirst().orElseThrow();
        assertThat(blocks.aliases()).contains("prevents", "gates");
    }

    @Test
    void decomposesIntoHasAliases() {
        var d = GoalVocabulary.GOAL_VOCABULARY.edgeTypes().stream()
                .filter(e -> e.canonical().equals("decomposes-into")).findFirst().orElseThrow();
        assertThat(d.aliases()).contains("breaks-down-to", "involves");
    }
}
