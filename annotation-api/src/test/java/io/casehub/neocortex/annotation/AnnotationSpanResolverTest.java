package io.casehub.neocortex.annotation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationSpanResolverTest {

    private final AnnotationSpanResolver resolver = new AnnotationSpanResolver();

    @Test
    void resolveSingleNamedMention() {
        String text = "James examined the guest book.";
        var mentions = List.of(new MentionInput("James Pond", "James", 0));
        List<ResolvedSpan> spans = resolver.resolve(text, mentions);
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).startOffset()).isEqualTo(0);
        assertThat(spans.get(0).endOffset()).isEqualTo(5);
        assertThat(spans.get(0).surfaceForm()).isEqualTo("James");
        assertThat(text.substring(spans.get(0).startOffset(), spans.get(0).endOffset()))
            .isEqualTo("James");
    }

    @Test
    void resolveMultipleMentionsWithPronouns() {
        String text = "James examined the book. He opened it carefully.";
        var mentions = List.of(
            new MentionInput("James Pond", "James", 0),
            new MentionInput("Guest Book", "book", 1),
            new MentionInput("James Pond", "He", 2),
            new MentionInput("Guest Book", "it", 3));
        List<ResolvedSpan> spans = resolver.resolve(text, mentions);
        assertThat(spans).hasSize(4);
        assertThat(spans.get(0).entityName()).isEqualTo("James Pond");
        assertThat(spans.get(2).entityName()).isEqualTo("James Pond");
        assertThat(spans.get(2).surfaceForm()).isEqualTo("He");
    }

    @Test
    void sortsByMentionIndexBeforeScanning() {
        String text = "James gave Clara the key.";
        var mentions = List.of(
            new MentionInput("James", "James", 0),
            new MentionInput("Key", "key", 2),
            new MentionInput("Clara", "Clara", 1));
        List<ResolvedSpan> spans = resolver.resolve(text, mentions);
        assertThat(spans).hasSize(3);
        assertThat(spans.get(0).surfaceForm()).isEqualTo("James");
        assertThat(spans.get(1).surfaceForm()).isEqualTo("Clara");
        assertThat(spans.get(2).surfaceForm()).isEqualTo("key");
    }

    @Test
    void skipsMissingSurfaceForm() {
        String text = "James walked away.";
        var mentions = List.of(
            new MentionInput("James", "James", 0),
            new MentionInput("Clara", "Clara", 1));
        List<ResolvedSpan> spans = resolver.resolve(text, mentions);
        assertThat(spans).hasSize(1);
        assertThat(spans.get(0).entityName()).isEqualTo("James");
    }

    @Test
    void disambiguatesRepeatedSurfaceForms() {
        String text = "He saw it. He took it.";
        var mentions = List.of(
            new MentionInput("James", "He", 0),
            new MentionInput("Key", "it", 1),
            new MentionInput("James", "He", 2),
            new MentionInput("Key", "it", 3));
        List<ResolvedSpan> spans = resolver.resolve(text, mentions);
        assertThat(spans).hasSize(4);
        assertThat(spans.get(0).startOffset()).isEqualTo(0);
        assertThat(spans.get(2).startOffset()).isEqualTo(11);
    }

    @Test
    void emptyMentionsReturnsEmpty() {
        List<ResolvedSpan> spans = resolver.resolve("Some text.", List.of());
        assertThat(spans).isEmpty();
    }

    @Test
    void substringRecoveryContract() {
        String text = "The detective examined the evidence.";
        var mentions = List.of(new MentionInput("James", "detective", 0));
        List<ResolvedSpan> spans = resolver.resolve(text, mentions);
        assertThat(spans).hasSize(1);
        ResolvedSpan span = spans.get(0);
        assertThat(text.substring(span.startOffset(), span.endOffset()))
            .isEqualTo("detective");
    }
}
