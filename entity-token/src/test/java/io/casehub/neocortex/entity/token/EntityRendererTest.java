package io.casehub.neocortex.entity.token;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EntityRendererTest {

    private final EntityIdentity jane = new EntityIdentity(
        "p1", "Janet Doe",
        Map.of("canonicalFirst", "Janet", "canonicalLast", "Doe"),
        Map.of("pronounsSubject", "she", "pronounsObject", "her"),
        Map.of("preferredShort", "Janey"),
        Set.of());

    @Test
    void renderProseResolvesToCurrentName() {
        String text = "{{p1:canonicalFirst:Jane}} painted";
        String result = EntityRenderer.render(text,
            id -> "p1".equals(id) ? jane : null, RenderMode.PROSE);
        assertThat(result).isEqualTo("Janet painted");
    }

    @Test
    void renderFallsBackToSurfaceFormWhenUnknown() {
        String text = "{{unknown:full:Mystery}} appeared";
        String result = EntityRenderer.render(text,
            id -> null, RenderMode.PROSE);
        assertThat(result).isEqualTo("Mystery appeared");
    }

    @Test
    void renderPassesThroughPlainText() {
        String text = "no tokens here";
        String result = EntityRenderer.render(text,
            id -> null, RenderMode.PROSE);
        assertThat(result).isEqualTo("no tokens here");
    }

    @Test
    void renderProseUsesVariantForResolution() {
        String text = "{{p1:full:Jane Doe}} arrived";
        String result = EntityRenderer.render(text,
            id -> "p1".equals(id) ? jane : null, RenderMode.PROSE);
        assertThat(result).isEqualTo("Janet Doe arrived");
    }

    @Test
    void renderProsePreferredShort() {
        String text = "{{p1:preferredShort:Jane}} said hello";
        String result = EntityRenderer.render(text,
            id -> "p1".equals(id) ? jane : null, RenderMode.PROSE);
        assertThat(result).isEqualTo("Janey said hello");
    }

    @Test
    void renderProsePronounResolvesToPronoun() {
        String text = "{{p1:pronoun:subject:She}} painted";
        String result = EntityRenderer.render(text,
            id -> "p1".equals(id) ? jane : null, RenderMode.PROSE);
        assertThat(result).isEqualTo("she painted");
    }

    @Test
    void renderAnnotatedPronounAddsLabel() {
        String text = "{{p1:pronoun:subject:She}} painted";
        String result = EntityRenderer.render(text,
            id -> "p1".equals(id) ? jane : null, RenderMode.ANNOTATED);
        assertThat(result).isEqualTo("she [Janet] painted");
    }

    @Test
    void renderAnnotatedNonPronounNoLabel() {
        String text = "{{p1:canonicalFirst:Jane}} painted";
        String result = EntityRenderer.render(text,
            id -> "p1".equals(id) ? jane : null, RenderMode.ANNOTATED);
        assertThat(result).isEqualTo("Janet painted");
    }

    @Test
    void renderMultipleTokens() {
        String text = "{{p1:canonicalFirst:Jane}} met {{p2:full:Diego Rivera}}";
        var diego = new EntityIdentity("p2", "Diego Rivera",
            Map.of("canonicalFirst", "Diego"), Map.of(), Map.of(), Set.of());
        String result = EntityRenderer.render(text,
            id -> switch (id) {
                case "p1" -> jane;
                case "p2" -> diego;
                default -> null;
            }, RenderMode.PROSE);
        assertThat(result).isEqualTo("Janet met Diego Rivera");
    }

    @Test
    void renderNullPassesThrough() {
        assertThat(EntityRenderer.render(null, id -> null, RenderMode.PROSE)).isNull();
    }

    @Test
    void renderNoDoubleBracesPassesThrough() {
        String text = "just {single} braces";
        assertThat(EntityRenderer.render(text, id -> null, RenderMode.PROSE))
            .isEqualTo("just {single} braces");
    }

    @Test
    void renderUnknownVariantFallsBackToSurfaceForm() {
        String text = "{{p1:nonexistentVariant:Original Text}} here";
        String result = EntityRenderer.render(text,
            id -> "p1".equals(id) ? jane : null, RenderMode.PROSE);
        assertThat(result).isEqualTo("Original Text here");
    }
}
