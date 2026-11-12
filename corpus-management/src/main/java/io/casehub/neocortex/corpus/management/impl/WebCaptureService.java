package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.corpus.management.WebCaptureMetadata;
import io.casehub.neocortex.rag.CorpusRef;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WebCaptureService {

    private final CorpusEntryStore store;

    public WebCaptureService(CorpusEntryStore store) {
        this.store = store;
    }

    public CaptureResult capture(CorpusRef corpus, String url, String body,
                                 int httpStatus, String contentType,
                                 String captureMethod, String actorId) {
        return capture(corpus, url, body, httpStatus, contentType, captureMethod,
                       WebCaptureMetadata.DEFAULT_TTL_DAYS, actorId);
    }

    public CaptureResult capture(CorpusRef corpus, String url, String body,
                                 int httpStatus, String contentType,
                                 String captureMethod, int ttlDays, String actorId) {
        return capture(corpus, url, body, httpStatus, contentType, captureMethod, ttlDays, null, actorId);
    }

    public CaptureResult capture(CorpusRef corpus, String url, String body,
                                 int httpStatus, String contentType,
                                 String captureMethod, int ttlDays,
                                 String artifactRef, String actorId) {

        Optional<CorpusEntry> existing = findFreshByUrl(corpus, url, ttlDays);
        if (existing.isPresent()) {
            return CaptureResult.deduplicated(existing.get().id());
        }

        Map<String, Object> metadata = WebCaptureMetadata.build(url, httpStatus, contentType,
                                                                 captureMethod, ttlDays);

        String entryId = "wc-" + url.hashCode() + "-" + Instant.now().toEpochMilli();
        CorpusEntry entry = new CorpusEntry(entryId, metadata, body, artifactRef);
        String storedId = store.add(corpus, entry, actorId);

        Optional<CorpusEntry> stale = findStaleByUrl(corpus, url, ttlDays);
        if (stale.isPresent()) {
            store.addRelationship(corpus, storedId, stale.get().id(), "updates", actorId);
            return CaptureResult.updated(storedId, stale.get().id());
        }

        return CaptureResult.created(storedId);
    }

    public Optional<CorpusEntry> findFreshByUrl(CorpusRef corpus, String url, int ttlDays) {
        List<CorpusEntry> entries = store.findByMetadataField(corpus, WebCaptureMetadata.URL, url);
        Instant cutoff = Instant.now().minus(Duration.ofDays(ttlDays));

        return entries.stream()
                .filter(e -> {
                    String fetchedAt = e.stringField(WebCaptureMetadata.FETCHED_AT);
                    if (fetchedAt == null) return false;
                    return Instant.parse(fetchedAt).isAfter(cutoff);
                })
                .findFirst();
    }

    private Optional<CorpusEntry> findStaleByUrl(CorpusRef corpus, String url, int ttlDays) {
        List<CorpusEntry> entries = store.findByMetadataField(corpus, WebCaptureMetadata.URL, url);
        Instant cutoff = Instant.now().minus(Duration.ofDays(ttlDays));

        return entries.stream()
                .filter(e -> {
                    String fetchedAt = e.stringField(WebCaptureMetadata.FETCHED_AT);
                    if (fetchedAt == null) return true;
                    return !Instant.parse(fetchedAt).isAfter(cutoff);
                })
                .reduce((a, b) -> {
                    String aTime = a.stringField(WebCaptureMetadata.FETCHED_AT);
                    String bTime = b.stringField(WebCaptureMetadata.FETCHED_AT);
                    if (aTime == null) return b;
                    if (bTime == null) return a;
                    return Instant.parse(aTime).isAfter(Instant.parse(bTime)) ? a : b;
                });
    }

    public record CaptureResult(Status status, String entryId, String previousEntryId) {

        public enum Status { CREATED, DEDUPLICATED, UPDATED }

        static CaptureResult created(String entryId) {
            return new CaptureResult(Status.CREATED, entryId, null);
        }

        static CaptureResult deduplicated(String entryId) {
            return new CaptureResult(Status.DEDUPLICATED, entryId, null);
        }

        static CaptureResult updated(String entryId, String previousEntryId) {
            return new CaptureResult(Status.UPDATED, entryId, previousEntryId);
        }
    }
}
