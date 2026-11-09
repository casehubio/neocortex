package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.HtmlToMarkdownParser;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebCapturePipelineSmokeTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "smoke-test");

    private InMemoryEntryStoreStub store;
    private HtmlToMarkdownParser htmlParser;
    private WebImageProcessor imageProcessor;
    private InterventionQueue interventionQueue;
    private CorpusApiBean bean;

    @BeforeEach
    void setUp() {
        store = new InMemoryEntryStoreStub();
        htmlParser = (html, url) -> {
            String md = "#@ page: 1\n#@ panel: main\n";
            if (html.contains("<img")) {
                md += "#@ figure: caption=\"Screenshot\"\n[Screenshot]\n";
            }
            md += "Parsed: " + html.substring(0, Math.min(html.length(), 50)) + "\n";
            return md;
        };
        imageProcessor = new WebImageProcessor((bytes, mime) -> "Stub image description") {
            @Override
            byte[] fetchImageBytes(String url) {
                return new byte[]{1, 2, 3};
            }
        };
        interventionQueue = new InterventionQueue(Duration.ofMinutes(10));
        bean = new CorpusApiBean(store, CORPUS, htmlParser, imageProcessor, interventionQueue);
    }

    @Test
    void freshCaptureCreatesEntryWithParsedMarkdown() {
        String html = "<html><body><p>Fresh article content</p></body></html>";
        var result = bean.webCapture("smoke-test", "https://example.com/article", html, 30, null);

        assertThat(result.entryId()).isNotNull();
        assertThat(result.cached()).isFalse();
        assertThat(result.body()).contains("Parsed:");
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
    void imageCaptureCreatesRelatedEntries() {
        String html = "<html><body>"
            + "<img src=\"https://example.com/diagram.png\" alt=\"Screenshot\">"
            + "<p>Article with image</p></body></html>";

        var result = bean.webCapture("smoke-test", "https://example.com/with-image", html, 30, null);

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
        assertThat(stored.body()).contains("Parsed:");
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
