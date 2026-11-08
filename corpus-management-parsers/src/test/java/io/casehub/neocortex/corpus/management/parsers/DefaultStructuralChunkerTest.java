package io.casehub.neocortex.corpus.management.parsers;

import io.casehub.neocortex.corpus.management.StructuredChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStructuralChunkerTest {

    private final DefaultStructuralChunker chunker = new DefaultStructuralChunker(64);

    @Test
    void plainTextSingleChunk() {
        List<StructuredChunk> chunks = chunker.chunk("Hello world.", 512);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("Hello world.");
        assertThat(chunks.get(0).metadata()).isEmpty();
    }

    @Test
    void singlePanelSingleChunk() {
        String input = "#@ panel: main\nHello world.";
        List<StructuredChunk> chunks = chunker.chunk(input, 512);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("Hello world.");
        assertThat(chunks.get(0).metadata()).containsEntry("panel", "main");
    }

    @Test
    void twoPanelsProduceTwoChunks() {
        String input = "#@ panel: left\nLeft text.\n#@ panel: right\nRight text.";
        List<StructuredChunk> chunks = chunker.chunk(input, 512);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).text().strip()).isEqualTo("Left text.");
        assertThat(chunks.get(0).metadata()).containsEntry("panel", "left");
        assertThat(chunks.get(1).text().strip()).isEqualTo("Right text.");
        assertThat(chunks.get(1).metadata()).containsEntry("panel", "right");
    }

    @Test
    void pageAnnotationAddsPageMetadata() {
        String input = "#@ page: 1\n#@ panel: main\nPage one.\n#@ page: 2\n#@ panel: main\nPage two.";
        List<StructuredChunk> chunks = chunker.chunk(input, 512);
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).metadata()).containsEntry("page", "1");
        assertThat(chunks.get(1).metadata()).containsEntry("page", "2");
    }

    @Test
    void largeTextFallsBackToCharacterSplitting() {
        String longText = "#@ panel: main\n" + "A".repeat(100);
        List<StructuredChunk> chunks = chunker.chunk(longText, 50);
        assertThat(chunks.size()).isGreaterThan(1);
        for (StructuredChunk chunk : chunks) {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(50 + 64);
            assertThat(chunk.metadata()).containsEntry("panel", "main");
        }
    }

    @Test
    void figureAnnotationSetsMetadata() {
        String input = "#@ panel: main\n#@ figure: caption=\"Chart 1\"\n[Figure here]";
        List<StructuredChunk> chunks = chunker.chunk(input, 512);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata()).containsEntry("has_figure", "true");
    }

    @Test
    void tableAnnotationSetsMetadata() {
        String input = "#@ panel: main\n#@ table: rows=3 cols=2\n| A | B |";
        List<StructuredChunk> chunks = chunker.chunk(input, 512);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).metadata()).containsEntry("has_table", "true");
    }

    @Test
    void headingSplitsWithinPanel() {
        String input = "#@ panel: main\n# Section 1\nText one.\n## Section 2\nText two.";
        List<StructuredChunk> chunks = chunker.chunk(input, 20);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        assertThat(chunks.get(0).metadata()).containsEntry("section", "Section 1");
    }

    @Test
    void emptyInput() {
        List<StructuredChunk> chunks = chunker.chunk("", 512);
        assertThat(chunks).isEmpty();
    }

    @Test
    void nullInput() {
        List<StructuredChunk> chunks = chunker.chunk(null, 512);
        assertThat(chunks).isEmpty();
    }

    @Test
    void layoutAnnotationIsSplitPoint() {
        String input = "#@ layout: single\n#@ panel: main\nText one.\n#@ layout: 2-column\n#@ panel: left\nText two.";
        List<StructuredChunk> chunks = chunker.chunk(input, 512);
        assertThat(chunks).hasSize(2);
    }
}
