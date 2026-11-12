package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.corpus.management.EntryRelationship;
import io.casehub.neocortex.corpus.management.WebCaptureMetadata;
import io.casehub.neocortex.rag.CorpusRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class WebCaptureServiceTest {

    private static final CorpusRef CORPUS = new CorpusRef("tenant", "web-cache");
    private static final String URL = "https://example.com/article";

    private InMemoryEntryStore store;
    private WebCaptureService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryEntryStore();
        service = new WebCaptureService(store);
    }

    @Test
    void captureCreatesNewEntry() {
        var result = service.capture(CORPUS, URL, "Article content", 200, "text/html", "playwright", "agent-1");

        assertThat(result.status()).isEqualTo(WebCaptureService.CaptureResult.Status.CREATED);
        assertThat(result.entryId()).isNotNull();
        assertThat(result.previousEntryId()).isNull();

        var entry = store.get(CORPUS, result.entryId());
        assertThat(entry).isPresent();
        assertThat(entry.get().stringField(WebCaptureMetadata.URL)).isEqualTo(URL);
        assertThat(entry.get().stringField("domain")).isEqualTo("web-cache");
        assertThat(entry.get().body()).isEqualTo("Article content");
    }

    @Test
    void captureDeduplicatesWithinTtl() {
        var first = service.capture(CORPUS, URL, "Content v1", 200, "text/html", "playwright", "agent-1");
        var second = service.capture(CORPUS, URL, "Content v2", 200, "text/html", "playwright", "agent-1");

        assertThat(second.status()).isEqualTo(WebCaptureService.CaptureResult.Status.DEDUPLICATED);
        assertThat(second.entryId()).isEqualTo(first.entryId());
    }

    @Test
    void captureCreatesVersionedEntryWhenStale() {
        Map<String, Object> staleMetadata = new LinkedHashMap<>();
        staleMetadata.put("domain", WebCaptureMetadata.DOMAIN);
        staleMetadata.put(WebCaptureMetadata.URL, URL);
        staleMetadata.put(WebCaptureMetadata.FETCHED_AT,
                          Instant.now().minus(60, ChronoUnit.DAYS).toString());
        staleMetadata.put(WebCaptureMetadata.TTL_DAYS, 30);

        var staleEntry = new CorpusEntry("stale-1", staleMetadata, "Old content", null);
        store.add(CORPUS, staleEntry);

        var result = service.capture(CORPUS, URL, "Fresh content", 200, "text/html", "playwright", "agent-1");

        assertThat(result.status()).isEqualTo(WebCaptureService.CaptureResult.Status.UPDATED);
        assertThat(result.entryId()).isNotEqualTo("stale-1");
        assertThat(result.previousEntryId()).isEqualTo("stale-1");

        assertThat(store.relationships).hasSize(1);
        var rel = store.relationships.get(0);
        assertThat(rel.fromEntryId()).isEqualTo(result.entryId());
        assertThat(rel.toEntryId()).isEqualTo("stale-1");
        assertThat(rel.relationshipType()).isEqualTo("updates");
    }

    @Test
    void captureMetadataIncludesAllFields() {
        var result = service.capture(CORPUS, URL, "Body", 200, "text/html", "playwright", 14, "agent-1");

        var entry = store.get(CORPUS, result.entryId()).orElseThrow();
        assertThat(entry.stringField(WebCaptureMetadata.URL)).isEqualTo(URL);
        assertThat(entry.stringField(WebCaptureMetadata.FETCHED_AT)).isNotNull();
        assertThat(entry.metadata().get(WebCaptureMetadata.HTTP_STATUS)).isEqualTo(200);
        assertThat(entry.stringField(WebCaptureMetadata.CONTENT_TYPE)).isEqualTo("text/html");
        assertThat(entry.stringField(WebCaptureMetadata.CAPTURE_METHOD)).isEqualTo("playwright");
        assertThat(entry.metadata().get(WebCaptureMetadata.TTL_DAYS)).isEqualTo(14);
    }

    @Test
    void findFreshByUrlReturnsEmptyWhenNoMatch() {
        var result = service.findFreshByUrl(CORPUS, "https://nonexistent.com", 30);
        assertThat(result).isEmpty();
    }

    @Test
    void findFreshByUrlReturnsEntryWithinTtl() {
        service.capture(CORPUS, URL, "Content", 200, "text/html", "playwright", "agent-1");

        var result = service.findFreshByUrl(CORPUS, URL, 30);
        assertThat(result).isPresent();
        assertThat(result.get().stringField(WebCaptureMetadata.URL)).isEqualTo(URL);
    }

    @Test
    void findFreshByUrlReturnsEmptyWhenExpired() {
        Map<String, Object> staleMetadata = new LinkedHashMap<>();
        staleMetadata.put(WebCaptureMetadata.URL, URL);
        staleMetadata.put(WebCaptureMetadata.FETCHED_AT,
                          Instant.now().minus(60, ChronoUnit.DAYS).toString());

        store.add(CORPUS, new CorpusEntry("old-1", staleMetadata, "Old content", null));

        var result = service.findFreshByUrl(CORPUS, URL, 30);
        assertThat(result).isEmpty();
    }

    @Test
    void captureDefaultsTtlTo30Days() {
        var result = service.capture(CORPUS, URL, "Body", 200, "text/html", "playwright", "agent-1");

        var entry = store.get(CORPUS, result.entryId()).orElseThrow();
        assertThat(entry.metadata().get(WebCaptureMetadata.TTL_DAYS)).isEqualTo(30);
    }

    static class InMemoryEntryStore implements CorpusEntryStore {
        final Map<String, CorpusEntry> entries = new ConcurrentHashMap<>();
        final List<EntryRelationship> relationships = new ArrayList<>();

        @Override
        public String add(CorpusRef corpus, CorpusEntry entry) {
            entries.put(entry.id(), entry);
            return entry.id();
        }

        @Override
        public String add(CorpusRef corpus, CorpusEntry entry, String actorId) {
            return add(corpus, entry);
        }

        @Override
        public void update(CorpusRef corpus, String entryId, CorpusEntry entry) {
            entries.put(entryId, entry);
        }

        @Override
        public void remove(CorpusRef corpus, String entryId) {
            entries.remove(entryId);
        }

        @Override
        public Optional<CorpusEntry> get(CorpusRef corpus, String entryId) {
            return Optional.ofNullable(entries.get(entryId));
        }

        @Override
        public List<CorpusEntry> findByMetadataField(CorpusRef corpus, String key, String value) {
            return entries.values().stream()
                    .filter(e -> value.equals(e.stringField(key)))
                    .toList();
        }

        @Override
        public String addRelationship(CorpusRef corpus, String fromEntryId, String toEntryId,
                                      String relationshipType, String createdBy) {
            String relId = "rel-" + relationships.size();
            relationships.add(new EntryRelationship(relId, fromEntryId, toEntryId,
                    relationshipType, createdBy, Instant.now()));
            return relId;
        }
    }
}
