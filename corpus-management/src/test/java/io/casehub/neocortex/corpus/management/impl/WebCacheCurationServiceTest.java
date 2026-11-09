package io.casehub.neocortex.corpus.management.impl;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WebCacheCurationServiceTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "web-cache");

    private WebCaptureServiceTest.InMemoryEntryStore entryStore;
    private StubRetrievalTracker tracker;
    private WebCacheCurationService service;

    @BeforeEach
    void setUp() {
        entryStore = new WebCaptureServiceTest.InMemoryEntryStore();
        tracker = new StubRetrievalTracker();
        service = new WebCacheCurationService(entryStore, tracker);
    }

    @Test
    void purgesExpiredEntries() {
        addWebCacheEntry("old-1", 60, 30);

        var result = service.purgeExpired(CORPUS);

        assertThat(result.examined()).isEqualTo(1);
        assertThat(result.purged()).isEqualTo(1);
        assertThat(entryStore.get(CORPUS, "old-1")).isEmpty();
    }

    @Test
    void keepsFreshEntries() {
        addWebCacheEntry("fresh-1", 5, 30);

        var result = service.purgeExpired(CORPUS);

        assertThat(result.purged()).isZero();
        assertThat(entryStore.get(CORPUS, "fresh-1")).isPresent();
    }

    @Test
    void renewsAccessedEntries() {
        addWebCacheEntry("accessed-1", 60, 30);
        tracker.accessed.add("accessed-1");

        var result = service.purgeExpired(CORPUS);

        assertThat(result.purged()).isZero();
        assertThat(result.renewed()).isEqualTo(1);
        assertThat(entryStore.get(CORPUS, "accessed-1")).isPresent();
    }

    @Test
    void exemptsArchivedEntries() {
        var metadata = webCacheMetadata(60, 30);
        metadata.put("status", "archived");
        entryStore.add(CORPUS, new CorpusEntry("archived-1", metadata, "body", null));

        var result = service.purgeExpired(CORPUS);

        assertThat(result.purged()).isZero();
        assertThat(entryStore.get(CORPUS, "archived-1")).isPresent();
    }

    @Test
    void respectsPerEntryTtl() {
        addWebCacheEntry("short-ttl", 10, 7);
        addWebCacheEntry("long-ttl", 10, 14);

        var result = service.purgeExpired(CORPUS);

        assertThat(result.purged()).isEqualTo(1);
        assertThat(entryStore.get(CORPUS, "short-ttl")).isEmpty();
        assertThat(entryStore.get(CORPUS, "long-ttl")).isPresent();
    }

    @Test
    void handlesNoWebCacheEntries() {
        var result = service.purgeExpired(CORPUS);

        assertThat(result.examined()).isZero();
        assertThat(result.purged()).isZero();
    }

    @Test
    void worksWithoutRetrievalTracker() {
        service = new WebCacheCurationService(entryStore, null);
        addWebCacheEntry("old-1", 60, 30);

        var result = service.purgeExpired(CORPUS);

        assertThat(result.purged()).isEqualTo(1);
    }

    private void addWebCacheEntry(String id, int daysAgo, int ttlDays) {
        entryStore.add(CORPUS, new CorpusEntry(id, webCacheMetadata(daysAgo, ttlDays), "body", null));
    }

    private Map<String, Object> webCacheMetadata(int daysAgo, int ttlDays) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("domain", WebCaptureMetadata.DOMAIN);
        metadata.put(WebCaptureMetadata.URL, "https://example.com/" + System.nanoTime());
        metadata.put(WebCaptureMetadata.FETCHED_AT,
                     Instant.now().minus(daysAgo, ChronoUnit.DAYS).toString());
        metadata.put(WebCaptureMetadata.TTL_DAYS, ttlDays);
        return metadata;
    }

    static class StubRetrievalTracker implements RetrievalTracker {
        final Set<String> accessed = new HashSet<>();

        @Override
        public String record(RetrievalQuery query, CorpusRef corpus,
                             List<RetrievedChunk> results, int maxResults) {
            return "rec-1";
        }

        @Override
        public void feedback(String retrievalId, String sourceDocumentId,
                             RetrievalOutcome outcome, FeedbackContext context) {}

        @Override
        public List<RetrievalRecord> findRecords(CorpusRef corpus, Instant since, Instant until) {
            return List.of();
        }

        @Override
        public List<RetrievalFeedback> findFeedback(CorpusRef corpus, Instant since, Instant until) {
            return List.of();
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
