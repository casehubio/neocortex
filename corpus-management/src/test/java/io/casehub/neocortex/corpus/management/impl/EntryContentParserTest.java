package io.casehub.neocortex.corpus.management.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EntryContentParserTest {

    @Test
    void parsesValidFrontmatterAndBody() {
        String content = """
                ---
                title: Test Entry
                domain: jvm
                artifact_ref: corpus://test/ref
                ---

                This is the body text.""";

        var entry = EntryContentParser.parse("e1", content.getBytes(StandardCharsets.UTF_8));

        assertThat(entry.id()).isEqualTo("e1");
        assertThat(entry.stringField("title")).isEqualTo("Test Entry");
        assertThat(entry.stringField("domain")).isEqualTo("jvm");
        assertThat(entry.artifactRef()).isEqualTo("corpus://test/ref");
        assertThat(entry.body()).isEqualTo("This is the body text.");
    }

    @Test
    void parsesContentWithoutFrontmatter() {
        String content = "Plain text without frontmatter.";

        var entry = EntryContentParser.parse("e2", content.getBytes(StandardCharsets.UTF_8));

        assertThat(entry.id()).isEqualTo("e2");
        assertThat(entry.metadata()).isEmpty();
        assertThat(entry.body()).isEqualTo("Plain text without frontmatter.");
        assertThat(entry.artifactRef()).isNull();
    }

    @Test
    void parsesContentWithUnterminatedFrontmatter() {
        String content = "---\ntitle: Broken\nNo closing delimiter";

        var entry = EntryContentParser.parse("e3", content.getBytes(StandardCharsets.UTF_8));

        assertThat(entry.id()).isEqualTo("e3");
        assertThat(entry.metadata()).isEmpty();
        assertThat(entry.body()).isEqualTo(content.strip());
    }

    @Test
    void parsesContentWithEmptyFrontmatter() {
        String content = "---\n---\n\nBody only.";

        var entry = EntryContentParser.parse("e4", content.getBytes(StandardCharsets.UTF_8));

        assertThat(entry.id()).isEqualTo("e4");
        assertThat(entry.metadata()).isEmpty();
        assertThat(entry.body()).isEqualTo("Body only.");
    }

    @Test
    void parsesContentWithListMetadata() {
        String content = """
                ---
                title: Tagged
                tags:
                  - java
                  - quarkus
                ---

                Body with tags.""";

        var entry = EntryContentParser.parse("e5", content.getBytes(StandardCharsets.UTF_8));

        assertThat(entry.id()).isEqualTo("e5");
        assertThat(entry.stringField("title")).isEqualTo("Tagged");
        assertThat(entry.stringListField("tags")).containsExactly("java", "quarkus");
        assertThat(entry.body()).isEqualTo("Body with tags.");
    }

    @Test
    void handlesWindowsLineEndings() {
        String content = "---\r\ntitle: Windows\r\n---\r\n\r\nBody.";

        var entry = EntryContentParser.parse("e6", content.getBytes(StandardCharsets.UTF_8));

        assertThat(entry.id()).isEqualTo("e6");
        assertThat(entry.stringField("title")).isEqualTo("Windows");
        assertThat(entry.body()).isEqualTo("Body.");
    }

    @Test
    void nullArtifactRefWhenAbsent() {
        String content = """
                ---
                title: No Ref
                ---

                Body.""";

        var entry = EntryContentParser.parse("e7", content.getBytes(StandardCharsets.UTF_8));

        assertThat(entry.artifactRef()).isNull();
    }
}
