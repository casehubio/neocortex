package io.casehub.neocortex.mindmap;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RelationshipVocabularyTest {

    @Test
    void vocabularyHasExpectedEdgeTypes() {
        var vocab = RelationshipVocabulary.RELATIONSHIP_VOCABULARY;
        assertThat(vocab.edgeTypes()).extracting("canonical")
                .contains("trusts", "distrusts", "fears", "admires",
                           "resents", "protects", "exploits", "relies-on");
    }

    @Test
    void trustsHasExpectedAliases() {
        var trustType = RelationshipVocabulary.RELATIONSHIP_VOCABULARY.edgeTypes().stream()
                .filter(e -> "trusts".equals(e.canonical()))
                .findFirst().orElseThrow();
        assertThat(trustType.aliases()).contains("has-faith-in");
    }
}
