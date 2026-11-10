package io.casehub.neocortex.corpus.management.impl;

import io.casehub.connectors.web.model.CapturedImage;
import io.casehub.connectors.web.model.ImageProcessingResult;
import io.casehub.connectors.web.ref.InMemoryWebBackend;
import io.casehub.connectors.web.ref.RefWebPlatform;
import io.casehub.connectors.web.spi.WebPlatform;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.WebCaptureMetadata;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.FeedbackContext;
import io.casehub.neocortex.rag.RetrievalFeedback;
import io.casehub.neocortex.rag.RetrievalOutcome;
import io.casehub.neocortex.rag.RetrievalQuery;
import io.casehub.neocortex.rag.RetrievalRecord;
import io.casehub.neocortex.rag.RetrievalTracker;
import io.casehub.neocortex.rag.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebCapturePipelineSmokeTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "smoke-test");

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
    void freshCaptureCreatesEntryWithParsedMarkdown() {
        String html = "<html><body><p>Fresh article content</p></body></html>";
        var result = bean.webCapture("smoke-test", "https://example.com/article", html, 30, null);

        assertThat(result.entryId()).isNotNull();
        assertThat(result.cached()).isFalse();
        assertThat(result.body()).contains("Fresh article content");
        assertThat(store.entries).hasSize(1);

        var entry = store.entries.get(result.entryId());
        assertThat(entry.stringField("domain")).isEqualTo("web-cache");
        assertThat(entry.stringField(WebCaptureMetadata.URL)).isEqualTo("https://example.com/article");
    }

    @Test
    void cacheHitReturnsSameEntryWithoutReparse() {
        String html = "<html><body><p>Cached article</p></body></html>";
        var first = bean.webCapture("smoke-test", "https://example.com/cached", html, 30, null);
        var second = bean.webCapture("smoke-test", "https://example.com/cached", html, 30, null);

        assertThat(second.cached()).isTrue();
        assertThat(second.entryId()).isEqualTo(first.entryId());
        assertThat(second.imageCount()).isEqualTo(0);
        assertThat(store.entries).hasSize(1);
    }

    @Test
    void webCaptureThrowsWhenNoHtmlAndNoFetchCapability() {
        var noWebBean = new CorpusApiBean(store, CORPUS, (WebPlatform) null);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                                                                   noWebBean.webCapture("smoke-test", "https://example.com/page", null, 30, null)
                                                          ).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void webCaptureWorksWithNullWebPlatformWhenHtmlProvided() {
        var    noWebBean = new CorpusApiBean(store, CORPUS, (WebPlatform) null);
        String html      = "<html><body><p>Direct HTML</p></body></html>";
        var    result    = noWebBean.webCapture("smoke-test", "https://example.com/page", html, 30, null);

        assertThat(result.entryId()).isNotNull();
        assertThat(result.cached()).isFalse();
        assertThat(result.body()).contains("Direct HTML");
    }

    @Test
    void fetchPathStoresContentFromWebPlatform() {
        webBackend.registerUrl("https://example.com/remote", "<html><body>Fetched content</body></html>");
        var result = bean.webCapture("smoke-test", "https://example.com/remote", null, 30, null);

        assertThat(result.entryId()).isNotNull();
        assertThat(result.cached()).isFalse();
        assertThat(result.body()).contains("Fetched content");
    }

    @Test
    void fetchFailureReturnsErrorResultInsteadOfThrowing() {
        var throwingPlatform = new RefWebPlatform(webBackend) {
            @Override
            public Fetch fetch() {
                return new Fetch() {
                    @Override
                    public io.casehub.connectors.web.model.FetchResult fetch(String url) {
                        throw new RuntimeException("Connection refused");
                    }

                    @Override
                    public io.casehub.connectors.web.model.FetchResult fetch(String url, io.casehub.connectors.web.model.FetchOptions options) {
                        throw new RuntimeException("Connection refused");
                    }
                };
            }
        };
        var throwingBean = new CorpusApiBean(store, CORPUS, throwingPlatform);
        var result       = throwingBean.webCapture("smoke-test", "https://unreachable.example.com", null, 30, null);

        assertThat(result.error()).isNotNull();
        assertThat(result.error()).contains("Connection refused");
        assertThat(result.entryId()).isNull();
        assertThat(result.url()).isEqualTo("https://unreachable.example.com");
        assertThat(store.entries).isEmpty();
    }

    @Test
    void fetchBadStatusReturnsErrorResult() {
        var result = bean.webCapture("smoke-test", "https://example.com/nonexistent", null, 30, null);

        assertThat(result.error()).isNotNull();
        assertThat(result.error()).contains("HTTP 404");
        assertThat(result.entryId()).isNull();
        assertThat(result.url()).isEqualTo("https://example.com/nonexistent");
        assertThat(store.entries).isEmpty();
    }

    @Test
    void configTtlDefaultIsUsedWhenTtlDaysNull() {
        var customConfig = new io.casehub.neocortex.corpus.management.WebCaptureConfig(7, 5);
        var configBean = new CorpusApiBean(null, null, null, null, null, null,
                                           CORPUS, null, store, null, webPlatform, customConfig);
        String html   = "<html><body><p>TTL test</p></body></html>";
        var    result = configBean.webCapture("smoke-test", "https://example.com/ttl-test", html, null, null);

        assertThat(result.entryId()).isNotNull();
        var entry = store.entries.get(result.entryId());
        assertThat(entry.intField(io.casehub.neocortex.corpus.management.WebCaptureMetadata.TTL_DAYS)).isEqualTo(7);
    }


    @Test
    void imageCaptureCreatesRelatedEntries() {
        var testImages = List.of(
            new CapturedImage("https://example.com/diagram.png", "Screenshot",
                new byte[]{1, 2, 3}, "image/png", "Stub image description")
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

        String html = "<html><body><p>Article with image</p></body></html>";
        var result = imageBean.webCapture("smoke-test", "https://example.com/with-image", html, 30, null);

        assertThat(result.imageCount()).isEqualTo(1);
        assertThat(store.entries).hasSize(2);

        var imageEntry = store.entries.values().stream()
            .filter(e -> "web-cache-image".equals(e.stringField("domain")))
            .findFirst().orElseThrow();
        assertThat(imageEntry.body()).isEqualTo("Stub image description");

        assertThat(store.relationships).hasSize(1);
        assertThat(store.relationships.get(0).relationshipType()).isEqualTo("derived-from");
    }

    @Test
    void interventionRequestResolveAndStore() {
        var intervention = bean.requestIntervention("smoke-test",
            "https://paywall.example.com", "Paywall detected", null);
        assertThat(intervention.id()).isNotNull();
        assertThat(bean.pendingInterventions("smoke-test")).hasSize(1);

        String resolvedHtml = "<html><body><p>Unlocked content</p></body></html>";
        var result = bean.resolveIntervention("smoke-test",
            intervention.id(), resolvedHtml, 30, null);

        assertThat(result.error()).isNull();
        assertThat(result.entryId()).isNotNull();
        assertThat(result.url()).isEqualTo("https://paywall.example.com");
        assertThat(bean.pendingInterventions("smoke-test")).isEmpty();

        var stored = store.entries.get(result.entryId());
        assertThat(stored).isNotNull();
        assertThat(stored.body()).contains("Unlocked content");
    }

    @Test
    void ttlCurationPurgesExpiredEntries() {
        Map<String, Object> expiredMeta = new LinkedHashMap<>();
        expiredMeta.put("domain", WebCaptureMetadata.DOMAIN);
        expiredMeta.put(WebCaptureMetadata.URL, "https://example.com/old");
        expiredMeta.put(WebCaptureMetadata.FETCHED_AT,
            Instant.now().minus(60, ChronoUnit.DAYS).toString());
        expiredMeta.put(WebCaptureMetadata.TTL_DAYS, 30);
        store.add(CORPUS, new CorpusEntry("expired-1", expiredMeta, "Old content", null));

        bean.webCapture("smoke-test", "https://example.com/fresh", "<p>Fresh</p>", 30, null);

        assertThat(store.entries).hasSize(2);

        var curationService = new WebCacheCurationService(store, new StubRetrievalTracker());
        var purgeResult = curationService.purgeExpired(CORPUS);

        assertThat(purgeResult.purged()).isEqualTo(1);
        assertThat(store.entries).hasSize(1);
        assertThat(store.entries.containsKey("expired-1")).isFalse();
    }

    @Test
    void ttlCurationRenewsRecentlyAccessedEntries() {
        Map<String, Object> oldButAccessedMeta = new LinkedHashMap<>();
        oldButAccessedMeta.put("domain", WebCaptureMetadata.DOMAIN);
        oldButAccessedMeta.put(WebCaptureMetadata.URL, "https://example.com/popular");
        oldButAccessedMeta.put(WebCaptureMetadata.FETCHED_AT,
            Instant.now().minus(45, ChronoUnit.DAYS).toString());
        oldButAccessedMeta.put(WebCaptureMetadata.TTL_DAYS, 30);
        store.add(CORPUS, new CorpusEntry("popular-1", oldButAccessedMeta, "Popular content", null));

        var tracker = new StubRetrievalTracker();
        tracker.accessed = Set.of("popular-1");

        var curationService = new WebCacheCurationService(store, tracker);
        var purgeResult = curationService.purgeExpired(CORPUS);

        assertThat(purgeResult.renewed()).isEqualTo(1);
        assertThat(purgeResult.purged()).isEqualTo(0);
        assertThat(store.entries).hasSize(1);
    }

    static class StubRetrievalTracker implements RetrievalTracker {
        Set<String> accessed = Set.of();

        @Override
        public String record(RetrievalQuery query, CorpusRef corpus,
                             java.util.List<RetrievedChunk> results, int maxResults) {
            return "rec-1";
        }

        @Override
        public void feedback(String retrievalId, String sourceDocumentId,
                             RetrievalOutcome outcome, FeedbackContext context) {}

        @Override
        public java.util.List<RetrievalRecord> findRecords(CorpusRef corpus, Instant since, Instant until) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<RetrievalFeedback> findFeedback(CorpusRef corpus, Instant since, Instant until) {
            return java.util.List.of();
        }

        @Override
        public Set<String> findRetrievedDocumentIds(CorpusRef corpus, Instant since, Instant until) {
            return accessed;
        }

        @Override
        public int purgeOlderThan(Instant cutoff) {
            return 0;
        }
    }
}
