package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoverDocumentParserTest {

    private final CoverDocumentParser parser = new CoverDocumentParser();

    @Test
    void parsesValidResearchCover() {
        String cover = """
            ---
            id: CE-20261102-abc123
            title: "Sleep consolidation follows a two-stage process"
            type: research-paper
            corpus: memory-review
            domain: neuroscience
            tags: [consolidation, sleep, memory]
            ---
            Walker (2005) demonstrates that sleep-dependent memory consolidation
            operates in two stages.
            
            ## Key Findings
            - Stage 1: SWS stabilisation
            - Stage 2: REM integration
            """;
        CorpusEntry entry = parser.parse(cover, CorpusType.RESEARCH);

        assertThat(entry.id()).isEqualTo("CE-20261102-abc123");
        assertThat(entry.stringField("title")).isEqualTo("Sleep consolidation follows a two-stage process");
        assertThat(entry.stringField("domain")).isEqualTo("neuroscience");
        assertThat(entry.stringListField("tags")).containsExactly("consolidation", "sleep", "memory");
        assertThat(entry.body()).contains("Walker (2005)");
        assertThat(entry.body()).contains("## Key Findings");
        assertThat(entry.artifactRef()).isNull();
    }

    @Test
    void parsesTranscriptWithArtifactRef() {
        String cover = """
            ---
            id: CE-20261102-t4u5v6
            title: "Design discussion: corpus architecture"
            type: transcript
            corpus: agent-01-interactions
            interaction_type: agent-human
            activity_type: design-discussion
            artifact_ref: corpus://agent-01-interactions/transcripts/2026-11-02.zip#entry-42
            ---
            Brainstorming session on unified knowledge management.
            """;
        CorpusEntry entry = parser.parse(cover, CorpusType.TRANSCRIPT);

        assertThat(entry.id()).isEqualTo("CE-20261102-t4u5v6");
        assertThat(entry.stringField("interaction_type")).isEqualTo("agent-human");
        assertThat(entry.stringField("activity_type")).isEqualTo("design-discussion");
        assertThat(entry.artifactRef())
            .isEqualTo("corpus://agent-01-interactions/transcripts/2026-11-02.zip#entry-42");
    }

    @Test
    void rejectsMissingRequiredField() {
        String cover = """
            ---
            id: CE-123
            title: "Missing domain"
            type: research-paper
            corpus: test
            ---
            Body text.
            """;
        assertThatThrownBy(() -> parser.parse(cover, CorpusType.RESEARCH))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("domain");
    }

    @Test
    void rejectsMissingArtifactRefForTranscript() {
        String cover = """
            ---
            id: CE-123
            title: "No artifact ref"
            type: transcript
            corpus: interactions
            interaction_type: agent-human
            activity_type: debugging
            ---
            Some brief.
            """;
        assertThatThrownBy(() -> parser.parse(cover, CorpusType.TRANSCRIPT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("artifact_ref");
    }

    @Test
    void parsesKnowledgeGardenEntry() {
        String cover = """
            ---
            id: GE-20261102-ff1234
            title: "SmallRye config gotcha"
            type: gotcha
            domain: jvm
            tags: [quarkus, config]
            score: 0.85
            staleness_threshold: 180d
            ---
            SmallRye @ConfigMapping interfaces cannot share defaults
            across profiles.
            """;
        CorpusEntry entry = parser.parse(cover, CorpusType.KNOWLEDGE_GARDEN);

        assertThat(entry.id()).isEqualTo("GE-20261102-ff1234");
        assertThat(entry.stringField("type")).isEqualTo("gotcha");
        assertThat(entry.doubleField("score")).isEqualTo(0.85);
    }

    @Test
    void handlesEmptyBody() {
        String cover = """
            ---
            id: CE-empty
            title: "Empty body"
            type: stub
            domain: test
            ---
            """;
        CorpusEntry entry = parser.parse(cover, CorpusType.KNOWLEDGE_GARDEN);
        assertThat(entry.body()).isEmpty();
    }

    @Test
    void stripsLeadingWhitespaceFromFrontmatter() {
        String      cover = "---\nid: CE-ws\ntitle: \"Whitespace\"\ntype: test\ndomain: test\n---\nBody with leading spaces preserved.\n";
        CorpusEntry entry = parser.parse(cover, CorpusType.KNOWLEDGE_GARDEN);
        assertThat(entry.id()).isEqualTo("CE-ws");
        assertThat(entry.body()).isEqualTo("Body with leading spaces preserved.");
    }
}
