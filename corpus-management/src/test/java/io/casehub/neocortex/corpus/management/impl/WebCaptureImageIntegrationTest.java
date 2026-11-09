package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.HtmlToMarkdownParser;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;




import static org.assertj.core.api.Assertions.assertThat;

class WebCaptureImageIntegrationTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "test-corpus");

    private InMemoryEntryStoreStub store;
    private HtmlToMarkdownParser htmlParser;
    private WebImageProcessor imageProcessor;

    @BeforeEach
    void setUp() {
        store = new InMemoryEntryStoreStub();
        htmlParser = (html, url) -> {
            String md = "#@ page: 1\n#@ panel: main\n";
            if (html.contains("alt=\"Diagram\"")) {
                md += "#@ figure: caption=\"Diagram\"\n[Diagram]\n";
            }
            md += "Article text\n";
            return md;
        };
        imageProcessor = new WebImageProcessor((bytes, mime) -> "LLM description of image") {
            @Override
            byte[] fetchImageBytes(String url) {
                return new byte[]{1, 2, 3, 4};
            }
        };
    }

    @Test
    void webCaptureStoresImagesAsSeparateEntries() {
        var bean = new CorpusApiBean(store, CORPUS, htmlParser, imageProcessor);

        String html = "<html><body>"
            + "<img src=\"https://example.com/diagram.png\" alt=\"Diagram\">"
            + "<p>Article text</p></body></html>";

        var result = bean.webCapture("test-corpus", "https://example.com/page", html, null, null);

        assertThat(result.imageCount()).isEqualTo(1);
        assertThat(result.cached()).isFalse();

        assertThat(store.entries).hasSize(2);

        var imageEntry = store.entries.values().stream()
            .filter(e -> "web-cache-image".equals(e.stringField("domain")))
            .findFirst()
            .orElseThrow();

        assertThat(imageEntry.body()).isEqualTo("LLM description of image");
        assertThat(imageEntry.stringField("source_url")).isEqualTo("https://example.com/diagram.png");
        assertThat(imageEntry.stringField("parent_url")).isEqualTo("https://example.com/page");
        assertThat(imageEntry.stringField("mime_type")).isEqualTo("image/png");
    }

    @Test
    void webCaptureCreatesRelationship() {
        var bean = new CorpusApiBean(store, CORPUS, htmlParser, imageProcessor);

        String html = "<html><body>"
            + "<img src=\"https://example.com/img.png\" alt=\"Diagram\">"
            + "</body></html>";

        bean.webCapture("test-corpus", "https://example.com/page", html, null, null);

        assertThat(store.relationships).hasSize(1);
        var rel = store.relationships.get(0);
        assertThat(rel.relationshipType()).isEqualTo("derived-from");
    }

    @Test
    void webCaptureEnrichesMarkdownWithDescriptions() {
        var bean = new CorpusApiBean(store, CORPUS, htmlParser, imageProcessor);

        String html = "<html><body>"
            + "<img src=\"https://example.com/img.png\" alt=\"Diagram\">"
            + "</body></html>";

        var result = bean.webCapture("test-corpus", "https://example.com/page", html, null, null);

        assertThat(result.body()).contains("describe=\"LLM description of image\"");
    }

    @Test
    void webCaptureCachedReturnsZeroImageCount() {
        var bean = new CorpusApiBean(store, CORPUS, htmlParser, imageProcessor);

        String html = "<html><body><p>Just text</p></body></html>";
        bean.webCapture("test-corpus", "https://example.com/page", html, null, null);

        var cached = bean.webCapture("test-corpus", "https://example.com/page", html, null, null);
        assertThat(cached.cached()).isTrue();
        assertThat(cached.imageCount()).isEqualTo(0);
    }

    @Test
    void webCaptureWorksWithoutImageProcessor() {
        var bean = new CorpusApiBean(store, CORPUS, htmlParser, null);

        String html = "<html><body>"
            + "<img src=\"https://example.com/img.png\" alt=\"Diagram\">"
            + "</body></html>";

        var result = bean.webCapture("test-corpus", "https://example.com/page", html, null, null);

        assertThat(result.imageCount()).isEqualTo(0);
        assertThat(store.entries).hasSize(1);
    }

}
