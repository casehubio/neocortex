package io.casehub.neocortex.entity.token;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SurfaceFormTrieTest {

    @Test
    void scanFindsExactMatch() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "first", "p1")));
        var matches = trie.scan("Hello Jane world");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).form().text()).isEqualTo("Jane");
        assertThat(matches.get(0).startOffset()).isEqualTo(6);
        assertThat(matches.get(0).endOffset()).isEqualTo(10);
    }

    @Test
    void scanCaseInsensitive() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "first", "p1")));
        var matches = trie.scan("hello jane world");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).form().text()).isEqualTo("Jane");
    }

    @Test
    void scanLongestMatchWins() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "first", "p1"),
            new SurfaceForm("Jane Doe", "full", "p1")));
        var matches = trie.scan("Hello Jane Doe world");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).form().text()).isEqualTo("Jane Doe");
    }

    @Test
    void scanRespectsWordBoundaries() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "first", "p1")));
        assertThat(trie.scan("Janeway is a captain")).isEmpty();
        assertThat(trie.scan("jane,")).hasSize(1);
        assertThat(trie.scan("Jane.")).hasSize(1);
        assertThat(trie.scan("Jane")).hasSize(1);
        assertThat(trie.scan("(Jane)")).hasSize(1);
    }

    @Test
    void scanWordBoundarySubstringBlocked() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jo", "first", "p1")));
        assertThat(trie.scan("job")).isEmpty();
        assertThat(trie.scan("Jo is here")).hasSize(1);
    }

    @Test
    void scanMultipleNonOverlapping() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "first", "p1"),
            new SurfaceForm("Diego", "first", "p2")));
        var matches = trie.scan("Jane met Diego");
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).form().entityId()).isEqualTo("p1");
        assertThat(matches.get(1).form().entityId()).isEqualTo("p2");
    }

    @Test
    void scanNoMatchesInEmptyText() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "first", "p1")));
        assertThat(trie.scan("")).isEmpty();
        assertThat(trie.scan("no entities here")).isEmpty();
    }

    @Test
    void buildFromEmptyList() {
        var trie = SurfaceFormTrie.build(List.of());
        assertThat(trie.scan("anything")).isEmpty();
    }

    @Test
    void scanNullText() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Jane", "first", "p1")));
        assertThat(trie.scan(null)).isEmpty();
    }

    @Test
    void scanMultiWordEntity() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Mexico City", "short", "place1")));
        var matches = trie.scan("She was born in Mexico City in 1907");
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).form().text()).isEqualTo("Mexico City");
    }

    @Test
    void scanMultipleEntitiesInSentence() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Frida", "first", "p1"),
            new SurfaceForm("Diego", "first", "p2"),
            new SurfaceForm("Mexico City", "short", "place1")));
        var matches = trie.scan("Frida and Diego lived in Mexico City");
        assertThat(matches).hasSize(3);
        assertThat(matches).extracting(m -> m.form().text())
            .containsExactly("Frida", "Diego", "Mexico City");
    }

    @Test
    void scanAtStartAndEnd() {
        var trie = SurfaceFormTrie.build(List.of(
            new SurfaceForm("Frida", "first", "p1")));
        assertThat(trie.scan("Frida")).hasSize(1);
        assertThat(trie.scan("Frida painted")).hasSize(1);
        assertThat(trie.scan("about Frida")).hasSize(1);
    }
}
