package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.corpus.management.WebCaptureMetadata;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.RetrievalTracker;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class WebCacheCurationService {

    private final CorpusEntryStore store;
    private final RetrievalTracker retrievalTracker;

    public WebCacheCurationService(CorpusEntryStore store, RetrievalTracker retrievalTracker) {
        this.store = store;
        this.retrievalTracker = retrievalTracker;
    }

    public PurgeResult purgeExpired(CorpusRef corpus) {
        List<CorpusEntry> webCacheEntries = store.findByMetadataField(
                corpus, "domain", WebCaptureMetadata.DOMAIN);

        if (webCacheEntries.isEmpty()) {
            return new PurgeResult(0, 0, 0);
        }

        Instant now = Instant.now();
        int examined = webCacheEntries.size();
        int purged = 0;
        int renewed = 0;

        int maxTtl = webCacheEntries.stream()
                .mapToInt(this::ttlDays)
                .max().orElse(WebCaptureMetadata.DEFAULT_TTL_DAYS);
        Instant earliestCutoff = now.minus(Duration.ofDays(maxTtl));

        Set<String> recentlyAccessed = retrievalTracker != null
                ? retrievalTracker.findRetrievedDocumentIds(corpus, earliestCutoff, now)
                : Set.of();

        List<String> toRemove = new ArrayList<>();

        for (CorpusEntry entry : webCacheEntries) {
            String status = entry.stringField("status");
            if ("archived".equalsIgnoreCase(status)) continue;

            int ttl = ttlDays(entry);
            Instant cutoff = now.minus(Duration.ofDays(ttl));
            String fetchedAt = entry.stringField(WebCaptureMetadata.FETCHED_AT);

            if (fetchedAt == null) {
                toRemove.add(entry.id());
                continue;
            }

            Instant fetchTime = Instant.parse(fetchedAt);

            if (fetchTime.isAfter(cutoff)) continue;

            if (recentlyAccessed.contains(entry.id())) {
                renewed++;
                continue;
            }

            toRemove.add(entry.id());
        }

        for (String entryId : toRemove) {
            store.remove(corpus, entryId);
            purged++;
        }

        return new PurgeResult(examined, purged, renewed);
    }

    private int ttlDays(CorpusEntry entry) {
        Object ttl = entry.metadata().get(WebCaptureMetadata.TTL_DAYS);
        if (ttl instanceof Number n) return n.intValue();
        return WebCaptureMetadata.DEFAULT_TTL_DAYS;
    }

    public record PurgeResult(int examined, int purged, int renewed) {}
}
