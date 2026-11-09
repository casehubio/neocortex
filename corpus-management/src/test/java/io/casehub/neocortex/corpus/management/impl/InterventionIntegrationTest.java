package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.HtmlToMarkdownParser;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;



import static org.assertj.core.api.Assertions.assertThat;

class InterventionIntegrationTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "test-corpus");

    private InMemoryEntryStoreStub store;
    private HtmlToMarkdownParser htmlParser;
    private InterventionQueue interventionQueue;
    private CorpusApiBean bean;

    @BeforeEach
    void setUp() {
        store = new InMemoryEntryStoreStub();
        htmlParser = (html, url) -> "# Title\nParsed content\n";
        interventionQueue = new InterventionQueue(Duration.ofMinutes(10));
        bean = new CorpusApiBean(store, CORPUS, htmlParser, null, interventionQueue);
    }

    @Test
    void requestInterventionQueuesAndReturnsResult() {
        var result = bean.requestIntervention("test-corpus",
            "https://example.com", "Login required", "/tmp/screenshot.png");

        assertThat(result.id()).startsWith("intv-");
        assertThat(result.url()).isEqualTo("https://example.com");
        assertThat(result.reason()).isEqualTo("Login required");
        assertThat(result.screenshotRef()).isEqualTo("/tmp/screenshot.png");
        assertThat(result.submittedAt()).isNotNull();
    }

    @Test
    void pendingInterventionsReturnsList() {
        bean.requestIntervention("test-corpus", "https://a.com", "CAPTCHA", null);
        bean.requestIntervention("test-corpus", "https://b.com", "Paywall", null);

        var pending = bean.pendingInterventions("test-corpus");
        assertThat(pending).hasSize(2);
    }

    @Test
    void resolveInterventionProcessesHtmlAndStores() {
        var intervention = bean.requestIntervention("test-corpus",
            "https://example.com", "Login required", null);

        String resolvedHtml = "<html><body><p>Authenticated content</p></body></html>";
        var result = bean.resolveIntervention("test-corpus",
            intervention.id(), resolvedHtml, null, null);

        assertThat(result.entryId()).isNotNull();
        assertThat(result.url()).isEqualTo("https://example.com");
        assertThat(result.interventionId()).isEqualTo(intervention.id());
        assertThat(result.error()).isNull();
        assertThat(result.cached()).isFalse();

        assertThat(store.entries).hasSize(1);
    }

    @Test
    void resolveExpiredInterventionReturnsError() {
        var shortQueue = new InterventionQueue(Duration.ofMillis(1));
        var shortBean = new CorpusApiBean(store, CORPUS, htmlParser, null, shortQueue);

        var intervention = shortBean.requestIntervention("test-corpus",
            "https://example.com", "CAPTCHA", null);

        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        var result = shortBean.resolveIntervention("test-corpus",
            intervention.id(), "<html>content</html>", null, null);

        assertThat(result.error()).isNotNull();
        assertThat(result.error()).contains("not found");
    }

    @Test
    void resolveUnknownInterventionReturnsError() {
        var result = bean.resolveIntervention("test-corpus",
            "intv-nonexistent", "<html>content</html>", null, null);

        assertThat(result.error()).isNotNull();
        assertThat(result.error()).contains("not found");
    }

    @Test
    void resolvedInterventionRemovedFromPending() {
        var intervention = bean.requestIntervention("test-corpus",
            "https://example.com", "Login", null);

        bean.resolveIntervention("test-corpus",
            intervention.id(), "<html>content</html>", null, null);

        assertThat(bean.pendingInterventions("test-corpus")).isEmpty();
    }

    @Test
    void resolveInterventionProcessesImagesWhenProcessorPresent() {
        var imageProcessor = new WebImageProcessor((bytes, mime) -> "Image from intervention") {
            @Override
            byte[] fetchImageBytes(String url) {
                return new byte[]{1, 2, 3};
            }
        };
        var beanWithImages = new CorpusApiBean(store, CORPUS,
                                               (html, url) -> "#@ page: 1\n#@ panel: main\n#@ figure: caption=\"Logo\"\n[Logo]\nContent\n",
                                               imageProcessor, interventionQueue);

        var intervention = beanWithImages.requestIntervention("test-corpus",
                                                              "https://example.com", "Login required", null);

        String html = "<html><body><img src=\"https://example.com/logo.png\" alt=\"Logo\"><p>Content</p></body></html>";
        var result = beanWithImages.resolveIntervention("test-corpus",
                                                        intervention.id(), html, null, null);

        assertThat(result.imageCount()).isEqualTo(1);
        assertThat(result.interventionId()).isEqualTo(intervention.id());
        assertThat(result.body()).contains("describe=\"Image from intervention\"");

        var imageEntry = store.entries.values().stream()
                                      .filter(e -> "web-cache-image".equals(e.stringField("domain")))
                                      .findFirst()
                                      .orElseThrow();
        assertThat(imageEntry.body()).isEqualTo("Image from intervention");
    }


}
