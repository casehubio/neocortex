package io.casehub.neocortex.corpus.management.impl;

import io.casehub.connectors.web.model.CapturedImage;
import io.casehub.connectors.web.model.ImageProcessingResult;
import io.casehub.connectors.web.ref.InMemoryWebBackend;
import io.casehub.connectors.web.ref.RefWebPlatform;
import io.casehub.connectors.web.spi.WebPlatform;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebCaptureImageIntegrationTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "test-corpus");

    private InMemoryEntryStoreStub store;
    private InMemoryWebBackend webBackend;

    @BeforeEach
    void setUp() {
        store = new InMemoryEntryStoreStub();
        webBackend = new InMemoryWebBackend();
    }

    private WebPlatform platformWithImages(List<CapturedImage> images) {
        return new RefWebPlatform(webBackend) {
            @Override
            public ImageCapture imageCapture() {
                return new ImageCapture() {
                    @Override
                    public ImageProcessingResult extractImages(String html, String sourceUrl) {
                        return new ImageProcessingResult(images);
                    }
                    @Override
                    public String enrichMarkdown(String markdown, ImageProcessingResult result) {
                        return markdown;
                    }
                };
            }
        };
    }

    @Test
    void webCaptureStoresImagesAsSeparateEntries() {
        var images = List.of(
            new CapturedImage("https://example.com/diagram.png", "Diagram",
                new byte[]{1, 2, 3, 4}, "image/png", "LLM description of image")
        );
        var bean = new CorpusApiBean(store, CORPUS, platformWithImages(images));

        String html = "<html><body><p>Article text</p></body></html>";
        var result = bean.webCapture("test-corpus", "https://example.com/page", html, 30, null);

        assertThat(result.imageCount()).isEqualTo(1);
        assertThat(store.entries).hasSize(2);

        var imageEntry = store.entries.values().stream()
            .filter(e -> "web-cache-image".equals(e.stringField("domain")))
            .findFirst().orElseThrow();
        assertThat(imageEntry.stringField("source_url")).isEqualTo("https://example.com/diagram.png");
        assertThat(imageEntry.stringField("alt_text")).isEqualTo("Diagram");
        assertThat(imageEntry.stringField("mime_type")).isEqualTo("image/png");
        assertThat(imageEntry.body()).isEqualTo("LLM description of image");
    }

    @Test
    void webCaptureCreatesRelationship() {
        var images = List.of(
            new CapturedImage("https://example.com/img.png", "Image",
                new byte[]{1}, "image/png", "Description")
        );
        var bean = new CorpusApiBean(store, CORPUS, platformWithImages(images));

        var result = bean.webCapture("test-corpus", "https://example.com/page", "<html></html>", 30, null);

        assertThat(store.relationships).hasSize(1);
        var rel = store.relationships.get(0);
        assertThat(rel.relationshipType()).isEqualTo("derived-from");
        assertThat(rel.toEntryId()).isEqualTo(result.entryId());
    }

    @Test
    void webCaptureCachedReturnsZeroImageCount() {
        var images = List.of(
            new CapturedImage("https://example.com/img.png", "Image",
                new byte[]{1}, "image/png", "Description")
        );
        var bean = new CorpusApiBean(store, CORPUS, platformWithImages(images));

        bean.webCapture("test-corpus", "https://example.com/page", "<html></html>", 30, null);
        var cached = bean.webCapture("test-corpus", "https://example.com/page", "<html></html>", 30, null);

        assertThat(cached.cached()).isTrue();
        assertThat(cached.imageCount()).isEqualTo(0);
    }

    @Test
    void webCaptureWorksWithoutImageProcessor() {
        var bean = new CorpusApiBean(store, CORPUS, new RefWebPlatform(webBackend));

        String html = "<html><body><p>No images</p></body></html>";
        var result = bean.webCapture("test-corpus", "https://example.com/page", html, 30, null);

        assertThat(result.imageCount()).isEqualTo(0);
        assertThat(store.entries).hasSize(1);
    }
}
