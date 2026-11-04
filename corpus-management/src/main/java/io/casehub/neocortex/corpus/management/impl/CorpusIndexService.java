package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.rag.ChunkInput;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.DocumentQualitySignal;
import io.casehub.neocortex.rag.EmbeddingIngestor;
import io.casehub.neocortex.rag.ExtractionResult;
import io.casehub.neocortex.rag.QualitySignal;
import io.casehub.neocortex.rag.QualityThresholds;
import io.casehub.neocortex.rag.RetrievalAnalyzer;
import io.casehub.neocortex.rag.RetrievalOutcome;
import io.casehub.neocortex.rag.RetrievalRecord;
import io.casehub.neocortex.rag.RetrievalTracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CorpusIndexService {

    private static final Logger LOG = Logger.getLogger(CorpusIndexService.class.getName());

    private final EmbeddingIngestor embeddingIngestor;
    private final RetrievalTracker retrievalTracker;
    private final GardenMetadataExtractor metadataExtractor;
    private final ProvenanceStore provenanceStore;

    public CorpusIndexService(EmbeddingIngestor embeddingIngestor,
                               RetrievalTracker retrievalTracker,
                               GardenMetadataExtractor metadataExtractor,
                               ProvenanceStore provenanceStore) {
        this.embeddingIngestor = embeddingIngestor;
        this.retrievalTracker = retrievalTracker;
        this.metadataExtractor = metadataExtractor;
        this.provenanceStore = provenanceStore;
    }

    public CorpusStatus status(CorpusRef corpusRef, String corpusPath) {
        try {
            int count = embeddingIngestor.listDocuments(corpusRef).size();
            return new CorpusStatus(corpusRef.corpusName(), corpusPath, count, true);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to count indexed entries", e);
            return new CorpusStatus(corpusRef.corpusName(), corpusPath, -1, false);
        }
    }

    public String deleteEntry(CorpusRef corpusRef, String entryId) {
        Map<String, String> idMap = buildEntryIdToDocIdMap(corpusRef);
        String docId = idMap.get(entryId);
        if (docId == null) {
            return "Entry " + entryId + " not found in index — nothing to delete.";
        }
        try {
            embeddingIngestor.deleteDocument(corpusRef, docId);
            return "Deleted vectors for " + entryId + " from index.";
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to delete entry vectors", e);
            return "Failed to delete " + entryId + ": " + e.getMessage();
        }
    }

    public String reindexEntry(CorpusRef corpusRef, String entryId, Path corpusRoot) {
        Map<String, String> idMap = buildEntryIdToDocIdMap(corpusRef);
        String docId = idMap.get(entryId);
        if (docId == null) {
            docId = resolveEntryPath(corpusRoot, entryId);
            if (docId == null) {
                return "Entry " + entryId + " not found in index or filesystem.";
            }
        }

        try {
            embeddingIngestor.deleteDocument(corpusRef, docId);
        } catch (Exception e) {
            LOG.log(Level.FINE, "No existing vectors to delete for " + entryId, e);
        }

        Path filePath = corpusRoot.resolve(docId);
        if (!Files.isRegularFile(filePath)) {
            return "File not found on disk: " + docId + ". Old vectors deleted if they existed.";
        }

        try {
            byte[] content = Files.readAllBytes(filePath);
            ExtractionResult extraction = metadataExtractor.extract(docId, content);
            if (extraction.body().isBlank()) {
                return "Entry " + entryId + " has no extractable content — skipped.";
            }
            ChunkInput chunk = new ChunkInput(extraction.body(), docId,
                    extraction.metadata(), extraction.listMetadata());
            embeddingIngestor.ingest(corpusRef, List.of(chunk));
            return "Re-indexed " + entryId + " in corpus.";
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to re-index entry", e);
            return "Failed to re-index " + entryId + ": " + e.getMessage();
        }
    }

    public UnretrievedReport unretrieved(CorpusRef corpusRef, int minDays, int staleDays) {
        QualityThresholds thresholds = new QualityThresholds(
                3, 3, 0.7, Duration.ofDays(staleDays));

        List<DocumentQualitySignal> signals = RetrievalAnalyzer.qualitySignals(
                retrievalTracker, embeddingIngestor, corpusRef,
                Instant.EPOCH, Instant.now(), thresholds);

        List<String> unretrieved = signals.stream()
                .filter(s -> s.signal() == QualitySignal.NEVER_RETRIEVED)
                .map(DocumentQualitySignal::sourceDocumentId)
                .filter(id -> passesMinDaysFilter(id, minDays))
                .sorted()
                .toList();

        List<String> stale = signals.stream()
                .filter(s -> s.signal() == QualitySignal.STALE)
                .map(DocumentQualitySignal::sourceDocumentId)
                .sorted()
                .toList();

        List<String> lowQuality = signals.stream()
                .filter(s -> s.signal() == QualitySignal.HIGH_RETRIEVAL_LOW_QUALITY)
                .map(DocumentQualitySignal::sourceDocumentId)
                .sorted()
                .toList();

        return new UnretrievedReport(unretrieved, stale, lowQuality,
                unretrieved.size(), stale.size(), lowQuality.size());
    }

    public String recordFeedback(CorpusRef corpusRef, List<String> entryIds,
                                  String outcome, String stack,
                                  String issueRepo, Integer issueNumber) {
        boolean isOutdated = "OUTDATED".equals(outcome);
        if (isOutdated && (stack == null || stack.isBlank())) {
            return "Error: OUTDATED outcome requires a stack parameter.";
        }

        RetrievalOutcome parsedOutcome;
        try {
            parsedOutcome = isOutdated ? RetrievalOutcome.NOT_RELEVANT
                    : RetrievalOutcome.valueOf(outcome);
        } catch (IllegalArgumentException e) {
            return "Error: invalid outcome '" + outcome
                    + "'. Must be: HIGHLY_RELEVANT, RELEVANT, PARTIALLY_RELEVANT, NOT_RELEVANT, OUTDATED";
        }

        Map<String, String> idMap = buildEntryIdToDocIdMap(corpusRef);
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        List<RetrievalRecord> recentRecords = retrievalTracker.findRecords(
                corpusRef, since, Instant.now());

        int recorded = 0;
        int skipped = 0;
        for (String entryId : entryIds) {
            String docId = idMap.get(entryId);
            if (docId == null) { skipped++; continue; }

            String retrievalId = recentRecords.stream()
                    .filter(r -> r.documents().stream()
                            .anyMatch(d -> d.sourceDocumentId().equals(docId)))
                    .max(Comparator.comparing(RetrievalRecord::timestamp))
                    .map(RetrievalRecord::retrievalId)
                    .orElse(null);
            if (retrievalId == null) { skipped++; continue; }

            retrievalTracker.feedback(retrievalId, docId, parsedOutcome);
            recorded++;

            if (provenanceStore != null) {
                provenanceStore.recordFeedbackContext(docId,
                        issueRepo != null ? issueRepo : "",
                        issueNumber != null ? issueNumber : 0,
                        outcome);
                if (isOutdated) {
                    provenanceStore.recordStaleness(docId, stack, "corpus-api");
                }
            }
        }

        return "Feedback recorded: " + recorded + " entries, " + skipped + " skipped"
                + (isOutdated ? " (marked OUTDATED for stack: " + stack + ")" : "");
    }

    public EntryDetail entry(Path corpusRoot, String entryId) {
        String filename = entryId + ".md";
        Path filePath;
        try (var stream = Files.walk(corpusRoot)) {
            filePath = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
        if (filePath == null) return null;

        try {
            byte[] content = Files.readAllBytes(filePath);
            String docId = corpusRoot.relativize(filePath).toString();
            ExtractionResult extraction = metadataExtractor.extract(docId, content);
            Map<String, String> meta = extraction.metadata();
            List<String> seeAlso = extraction.listMetadata().getOrDefault("see_also", List.of());
            return new EntryDetail(
                    entryId,
                    meta.getOrDefault("title", entryId),
                    meta.getOrDefault("domain", ""),
                    meta.getOrDefault("type", ""),
                    0.0,
                    extraction.body(),
                    meta.getOrDefault("source", ""),
                    meta.getOrDefault("source_prefix", ""),
                    seeAlso);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to read entry " + entryId, e);
            return null;
        }
    }

    static String extractDocumentId(String path) {
        if (path == null) return "";
        String withoutExt = path.replaceFirst("\\.md$", "");
        String filename = withoutExt.contains("/")
                ? withoutExt.substring(withoutExt.lastIndexOf('/') + 1)
                : withoutExt;
        if (filename.matches("GE-\\d{8}-[0-9a-f]{6}")) {
            return filename;
        }
        return withoutExt;
    }

    static boolean passesMinDaysFilter(String documentId, int minDays) {
        String filename = documentId.contains("/")
                ? documentId.substring(documentId.lastIndexOf('/') + 1) : documentId;
        if (filename.matches("GE-\\d{8}-[0-9a-f]{6}\\.md")) {
            String dateStr = filename.substring(3, 11);
            try {
                LocalDate entryDate = LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE);
                return ChronoUnit.DAYS.between(entryDate, LocalDate.now()) >= minDays;
            } catch (DateTimeParseException e) {
                return true;
            }
        }
        return true;
    }

    private Map<String, String> buildEntryIdToDocIdMap(CorpusRef corpusRef) {
        try {
            return embeddingIngestor.listDocuments(corpusRef).stream()
                    .collect(Collectors.toMap(
                            CorpusIndexService::extractDocumentId,
                            id -> id,
                            (a, b) -> a));
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String resolveEntryPath(Path corpusRoot, String entryId) {
        String filename = entryId + ".md";
        try (var stream = Files.walk(corpusRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(filename))
                    .map(p -> corpusRoot.relativize(p).toString())
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
