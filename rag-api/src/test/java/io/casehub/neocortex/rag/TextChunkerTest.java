package io.casehub.neocortex.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextChunkerTest {

    @Test
    void shortTextReturnsUnchanged() {
        var chunker = TextChunker.recursive(100, 0);
        assertThat(chunker.chunk("hello world")).containsExactly("hello world");
    }

    @Test
    void blankTextReturnsEmpty() {
        var chunker = TextChunker.recursive(100, 0);
        assertThat(chunker.chunk("   ")).isEmpty();
        assertThat(chunker.chunk("")).isEmpty();
    }

    @Test
    void splitsOnParagraphBoundary() {
        var chunker = TextChunker.recursive(30, 0);
        var result = chunker.chunk("First paragraph.\n\nSecond paragraph.");
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo("First paragraph.");
        assertThat(result.get(1)).isEqualTo("Second paragraph.");
    }

    @Test
    void splitsOnNewlineWhenParagraphTooLong() {
        var chunker = TextChunker.recursive(20, 0);
        var result = chunker.chunk("Line one.\nLine two.\nLine three.");
        assertThat(result).allSatisfy(chunk ->
            assertThat(chunk.length()).isLessThanOrEqualTo(20));
        assertThat(String.join(" ", result)).contains("Line one");
        assertThat(String.join(" ", result)).contains("Line three");
    }

    @Test
    void splitsOnSentenceBoundary() {
        var chunker = TextChunker.recursive(30, 0);
        var result = chunker.chunk("First sentence. Second sentence. Third sentence.");
        assertThat(result).hasSizeGreaterThan(1);
        assertThat(result).allSatisfy(chunk ->
            assertThat(chunk.length()).isLessThanOrEqualTo(30));
    }

    @Test
    void fallsBackToCharacterSplit() {
        var chunker = TextChunker.recursive(10, 0);
        var result = chunker.chunk("abcdefghijklmnopqrstuvwxyz");
        assertThat(result).isNotEmpty();
        assertThat(result).allSatisfy(chunk ->
            assertThat(chunk.length()).isLessThanOrEqualTo(10));
    }

    @Test
    void overlapIncludesTrailingContext() {
        var chunker = TextChunker.recursive(25, 5);
        var result = chunker.chunk("First paragraph.\n\nSecond paragraph.");
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> TextChunker.recursive(0, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextChunker.recursive(10, -1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextChunker.recursive(10, 10))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chunksNeverExceedMaxSize() {
        var chunker = TextChunker.recursive(50, 10);
        String text = "The quick brown fox jumps over the lazy dog. " +
                       "Pack my box with five dozen liquor jugs. " +
                       "How vexingly quick daft zebras jump. " +
                       "The five boxing wizards jump quickly.";
        var result = chunker.chunk(text);
        assertThat(result).allSatisfy(chunk ->
            assertThat(chunk.length()).isLessThanOrEqualTo(50));
        assertThat(result).isNotEmpty();
    }
}
