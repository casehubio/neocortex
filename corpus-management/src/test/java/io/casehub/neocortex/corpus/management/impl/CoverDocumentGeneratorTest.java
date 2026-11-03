package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.ActivityType;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.InteractionContext;
import io.casehub.neocortex.corpus.management.InteractionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CoverDocumentGeneratorTest {

    private final CoverDocumentGenerator generator = new CoverDocumentGenerator();
    private final CoverDocumentParser parser = new CoverDocumentParser();

    @Test
    void generatesTranscriptCoverFromInteractionContext() {
        var context = new InteractionContext(
            "agent-01", "tenant-1",
            InteractionType.AGENT_HUMAN,
            ActivityType.DESIGN_DISCUSSION,
            "neocortex",
            List.of("agent-01", "mark"),
            47,
            "Brainstorming session on corpus management architecture, covering federated search design and cover document parsing.",
            "corpus://agent-01-interactions/transcripts/2026-11-02.zip#entry-42"
        );
        String cover = generator.generateTranscriptCover(context);

        assertThat(cover).startsWith("---");
        assertThat(cover).contains("interaction_type: agent-human");
        assertThat(cover).contains("activity_type: design-discussion");
        assertThat(cover).contains("artifact_ref:");
        assertThat(cover).contains("turn_count: 47");

        CorpusEntry entry = parser.parse(cover, CorpusType.TRANSCRIPT);
        assertThat(entry.id()).startsWith("CE-");
        assertThat(entry.stringField("interaction_type")).isEqualTo("agent-human");
        assertThat(entry.stringField("activity_type")).isEqualTo("design-discussion");
        assertThat(entry.stringField("corpus")).isEqualTo("agent-01-interactions");
        assertThat(entry.stringField("type")).isEqualTo("transcript");
        assertThat(entry.stringField("domain")).isEqualTo("neocortex");
        assertThat(entry.stringListField("participants")).containsExactly("agent-01", "mark");
        assertThat(entry.intField("turn_count")).isEqualTo(47);
        assertThat(entry.artifactRef())
            .isEqualTo("corpus://agent-01-interactions/transcripts/2026-11-02.zip#entry-42");
        assertThat(entry.body()).contains("Brainstorming session on corpus management architecture");
    }

    @Test
    void generatedIdIsUniquePerCall() {
        var context = new InteractionContext(
            "agent-01", "tenant-1",
            InteractionType.AGENT_HUMAN,
            ActivityType.GENERAL,
            null,
            List.of(),
            5,
            "Short chat about nothing in particular.",
            "corpus://agent-01-interactions/transcripts/2026-11-02.zip#entry-99"
        );
        String cover1 = generator.generateTranscriptCover(context);
        String cover2 = generator.generateTranscriptCover(context);

        CorpusEntry entry1 = parser.parse(cover1, CorpusType.TRANSCRIPT);
        CorpusEntry entry2 = parser.parse(cover2, CorpusType.TRANSCRIPT);
        assertThat(entry1.id()).isNotEqualTo(entry2.id());
    }

    @Test
    void includesRelatedNodeIds() {
        var context = new InteractionContext(
            "agent-01", "tenant-1",
            InteractionType.AGENT_HUMAN,
            ActivityType.RESEARCH,
            "cognition",
            List.of("agent-01"),
            12,
            "Exploring CAPS topology node activation patterns.",
            "corpus://agent-01-interactions/transcripts/2026-11-02.zip#entry-7",
            List.of("node-abc", "node-def")
        );
        String cover = generator.generateTranscriptCover(context);

        CorpusEntry entry = parser.parse(cover, CorpusType.TRANSCRIPT);
        assertThat(entry.stringListField("related_nodes")).containsExactly("node-abc", "node-def");
    }

    @Test
    void generatesValidTitleFromBrief() {
        var context = new InteractionContext(
            "agent-01", "tenant-1",
            InteractionType.SELF_REFLECTION,
            ActivityType.GENERAL,
            null,
            List.of("agent-01"),
            1,
            "Reflecting on recent interaction patterns and emotional responses during debugging sessions.",
            "corpus://agent-01-interactions/transcripts/2026-11-02.zip#entry-55"
        );
        String cover = generator.generateTranscriptCover(context);

        CorpusEntry entry = parser.parse(cover, CorpusType.TRANSCRIPT);
        assertThat(entry.stringField("title")).isNotBlank();
        assertThat(entry.stringField("title").length()).isLessThanOrEqualTo(80);
    }

    @Test
    void omitsNullDomain() {
        var context = new InteractionContext(
            "agent-01", "tenant-1",
            InteractionType.TOOL_USE,
            ActivityType.IMPLEMENTATION,
            null,
            List.of(),
            3,
            "Running Maven build to verify compilation.",
            "corpus://agent-01-interactions/transcripts/2026-11-02.zip#entry-10"
        );
        String cover = generator.generateTranscriptCover(context);

        CorpusEntry entry = parser.parse(cover, CorpusType.TRANSCRIPT);
        assertThat(entry.stringField("domain")).isNull();
    }

    @Test
    void omitsEmptyParticipantsAndRelatedNodes() {
        var context = new InteractionContext(
            "agent-01", "tenant-1",
            InteractionType.SELF_REFLECTION,
            ActivityType.GENERAL,
            null,
            List.of(),
            1,
            "Quiet reflection on the day's events.",
            "corpus://agent-01-interactions/transcripts/2026-11-02.zip#entry-88"
        );
        String cover = generator.generateTranscriptCover(context);

        assertThat(cover).doesNotContain("participants:");
        assertThat(cover).doesNotContain("related_nodes:");
    }
}
