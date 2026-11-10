package io.casehub.neocortex.corpus.management.impl;

import io.casehub.connectors.web.model.CapturedImage;
import io.casehub.connectors.web.model.ImageProcessingResult;
import io.casehub.connectors.web.ref.InMemoryWebBackend;
import io.casehub.connectors.web.ref.RefWebPlatform;
import io.casehub.connectors.web.spi.WebPlatform;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterventionIntegrationTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "test-corpus");

    private InMemoryEntryStoreStub store;
    private InMemoryWebBackend webBackend;
    private WebPlatform webPlatform;
    private CorpusApiBean bean;

    @BeforeEach
    void setUp() {
        store = new InMemoryEntryStoreStub();
        webBackend = new InMemoryWebBackend();
        webPlatform = new RefWebPlatform(webBackend);
        bean = new CorpusApiBean(store, CORPUS, webPlatform);
    }

    @Test
    void requestInterventionQueuesAndReturnsResult() {
        var result = bean.requestIntervention("test-corpus",
            "https://captcha.example.com", "CAPTCHA detected", null);

        assertThat(result.id()).isNotNull();
        assertThat(result.url()).isEqualTo("https://captcha.example.com");
        assertThat(result.reason()).isEqualTo("CAPTCHA detected");
    }

    @Test
    void pendingInterventionsReturnsList() {
        bean.requestIntervention("test-corpus", "https://a.com", "Login", null);
        bean.requestIntervention("test-corpus", "https://b.com", "CAPTCHA", null);

        var pending = bean.pendingInterventions("test-corpus");
        assertThat(pending).hasSize(2);
    }

    @Test
    void resolveInterventionProcessesHtmlAndStores() {
        var intervention = bean.requestIntervention("test-corpus",
            "https://example.com/locked", "Login required", null);

        var result = bean.resolveIntervention("test-corpus",
            intervention.id(), "<html><body>Unlocked</body></html>", 30, null);

        assertThat(result.error()).isNull();
        assertThat(result.entryId()).isNotNull();
        assertThat(result.url()).isEqualTo("https://example.com/locked");
        assertThat(store.entries).hasSize(1);
    }

    @Test
    void resolveExpiredInterventionReturnsError() {
        var shortBackend = new InMemoryWebBackend(Duration.ofMillis(1));
        var shortPlatform = new RefWebPlatform(shortBackend);
        var shortBean = new CorpusApiBean(store, CORPUS, shortPlatform);

        var intervention = shortBean.requestIntervention("test-corpus",
            "https://example.com", "CAPTCHA", null);

        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        var result = shortBean.resolveIntervention("test-corpus",
            intervention.id(), "<html>Resolved</html>", 30, null);
        assertThat(result.error()).contains("not found or expired");
    }

    @Test
    void resolveUnknownInterventionReturnsError() {
        var result = bean.resolveIntervention("test-corpus",
            "intv-nonexistent", "<html>Resolved</html>", 30, null);
        assertThat(result.error()).contains("not found or expired");
    }

    @Test
    void resolvedInterventionRemovedFromPending() {
        var intervention = bean.requestIntervention("test-corpus",
            "https://example.com/locked", "Login", null);
        assertThat(bean.pendingInterventions("test-corpus")).hasSize(1);

        bean.resolveIntervention("test-corpus",
            intervention.id(), "<html>Resolved</html>", 30, null);
        assertThat(bean.pendingInterventions("test-corpus")).isEmpty();
    }

    @Test
    void requestInterventionThrowsWhenWebPlatformNotConfigured() {
        var noWebBean = new CorpusApiBean(store, CORPUS, (WebPlatform) null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                                                   noWebBean.requestIntervention("test-corpus", "https://example.com", "test", null)
                                                          ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void screenshotRefRoundTrips() {
        var intervention = bean.requestIntervention("test-corpus",
                                                    "https://example.com", "CAPTCHA", "/tmp/screenshot.png");
        assertThat(intervention.screenshotRef()).isEqualTo("/tmp/screenshot.png");

        var pending = bean.pendingInterventions("test-corpus");
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).screenshotRef()).isEqualTo("/tmp/screenshot.png");
    }


    @Test
    void resolveInterventionProcessesImagesWhenProcessorPresent() {
        var testImages = List.of(
            new CapturedImage("https://example.com/img.png", "Diagram",
                new byte[]{1, 2, 3}, "image/png", "Test description")
        );
        var imagePlatform = new RefWebPlatform(webBackend) {
            @Override
            public ImageCapture imageCapture() {
                return new ImageCapture() {
                    @Override
                    public ImageProcessingResult extractImages(String html, String sourceUrl) {
                        return new ImageProcessingResult(testImages);
                    }
                    @Override
                    public String enrichMarkdown(String markdown, ImageProcessingResult result) {
                        return markdown;
                    }
                };
            }
        };
        var imageBean = new CorpusApiBean(store, CORPUS, imagePlatform);

        var intervention = imageBean.requestIntervention("test-corpus",
            "https://example.com/locked", "Login", null);

        var result = imageBean.resolveIntervention("test-corpus",
            intervention.id(), "<html><body>Content</body></html>", 30, null);

        assertThat(result.error()).isNull();
        assertThat(result.imageCount()).isEqualTo(1);
    }
}
