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

    @Test
    void contentDomainsHasNineValues() {
        assertThat(GoalVocabulary.CONTENT_DOMAINS).hasSize(9);
        assertThat(GoalVocabulary.CONTENT_DOMAINS).contains(
                "mastery-and-competence", "self-actualisation", "avoidance",
                "social-relating", "health", "family",
                "morality-and-virtue", "religion-and-spirituality",
                "financial-and-occupational");
    }

    @Test
    void motivationOriginsHasFourValues() {
        assertThat(GoalVocabulary.MOTIVATION_ORIGINS).hasSize(4);
        assertThat(GoalVocabulary.MOTIVATION_ORIGINS).contains(
                "intrinsic", "identified", "introjected", "external");
    }

    @Test
    void contentSubdomainsHas25Values() {
        assertThat(GoalVocabulary.CONTENT_SUBDOMAINS).hasSize(25);
        assertThat(GoalVocabulary.CONTENT_SUBDOMAINS).contains(
                "self-efficacy", "achievement", "religion-and-spirituality",
                "intimacy", "leadership", "finances");
    }

}
