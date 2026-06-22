package io.casehub.rag.hyde;

import io.casehub.rag.RetrievalQuery;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateQueryExpanderTest {

    @Test
    void expandUsesDefaultTemplate() {
        var expander = new TemplateQueryExpander(stubConfig(Optional.empty()));
        var result = expander.expand(RetrievalQuery.of("what is diabetes?"));

        assertThat(result.text()).isEqualTo("what is diabetes?");
        assertThat(result.expandedText()).contains("what is diabetes?");
        assertThat(result.expandedText()).contains("A document that answers");
    }

    @Test
    void expandUsesCustomTemplate() {
        var expander = new TemplateQueryExpander(
            stubConfig(Optional.of("Product matching query: %s")));
        var result = expander.expand(RetrievalQuery.of("SKU-123"));

        assertThat(result.expandedText()).isEqualTo("Product matching query: SKU-123");
    }

    @Test
    void expandPreservesOriginalText() {
        var expander = new TemplateQueryExpander(stubConfig(Optional.empty()));
        var result = expander.expand(RetrievalQuery.of("test"));

        assertThat(result.text()).isEqualTo("test");
    }

    private static HydeConfig stubConfig(Optional<String> template) {
        return new HydeConfig() {
            @Override public boolean enabled() { return true; }
            @Override public String mode() { return "template"; }
            @Override public Optional<String> promptTemplate() { return Optional.empty(); }
            @Override public Optional<String> template() { return template; }
        };
    }
}
