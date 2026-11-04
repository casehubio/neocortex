package io.casehub.neocortex.entity.token;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EntityTokeniserTest {

    @Test
    void tokenisesKnownEntities() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane Doe", "full", "p1"),
            new SurfaceForm("Jane", "canonicalFirst", "p1")));
        String result = EntityTokeniser.tokenise("Jane Doe painted a mural", trie);
        assertThat(result).isEqualTo("{{p1:full:Jane Doe}} painted a mural");
    }

    @Test
    void leavesUnknownTextAlone() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "canonicalFirst", "p1")));
        String result = EntityTokeniser.tokenise("Diego walked home", trie);
        assertThat(result).isEqualTo("Diego walked home");
    }

    @Test
    void tokenisesMultipleEntities() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "canonicalFirst", "p1"),
            new SurfaceForm("Diego", "canonicalFirst", "p2")));
        String result = EntityTokeniser.tokenise("Jane met Diego", trie);
        assertThat(result).isEqualTo(
            "{{p1:canonicalFirst:Jane}} met {{p2:canonicalFirst:Diego}}");
    }

    @Test
    void preservesOriginalCasing() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "canonicalFirst", "p1")));
        String result = EntityTokeniser.tokenise("JANE was here", trie);
        assertThat(result).isEqualTo("{{p1:canonicalFirst:JANE}} was here");
    }

    @Test
    void nullAndEmptyPassThrough() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "canonicalFirst", "p1")));
        assertThat(EntityTokeniser.tokenise(null, trie)).isNull();
        assertThat(EntityTokeniser.tokenise("", trie)).isEmpty();
    }

    @Test
    void roundTripsWithRenderer() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane Doe", "full", "p1")));
        String tokenised = EntityTokeniser.tokenise("Jane Doe painted", trie);

        var identity = EntityIdentity.of("p1", "Jane Doe",
            java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Set.of());
        String rendered = EntityRenderer.render(tokenised,
            id -> "p1".equals(id) ? identity : null, RenderMode.PROSE);
        assertThat(rendered).isEqualTo("Jane Doe painted");
    }
}
