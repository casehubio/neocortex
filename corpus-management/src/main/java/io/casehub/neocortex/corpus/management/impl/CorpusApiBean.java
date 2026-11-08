package io.casehub.neocortex.corpus.management.impl;

import io.casehub.neocortex.corpus.VersionInfo;
import io.casehub.neocortex.corpus.management.ChangeResult;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.rag.CorpusRef;
import io.casehub.neocortex.rag.ProvenanceRecord;
import io.casehub.neocortex.rag.ProvenanceStats;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class CorpusApiBean implements CorpusApi {

    private final SearchResource searchResource;
    private final CorpusIndexService indexService;
    private final GardenReindexService reindexService;
    private final SearchProfileStore profileStore;
    private final ProvenanceStore provenanceStore;
    private final CorpusOutcomeService outcomeService;
    private final CorpusRef corpusRef;
    private final Path      corpusRoot;
    private final CorpusEntryStore entryStore;

    public CorpusApiBean(SearchResource searchResource,
                         CorpusIndexService indexService,
                         GardenReindexService reindexService,
                         SearchProfileStore profileStore,
                         ProvenanceStore provenanceStore,
                         CorpusOutcomeService outcomeService,
                         CorpusRef corpusRef,
                         Path corpusRoot,
                         CorpusEntryStore entryStore) {
        this.searchResource  = searchResource;
        this.indexService    = indexService;
        this.reindexService  = reindexService;
        this.profileStore    = profileStore;
        this.provenanceStore = provenanceStore;
        this.outcomeService  = outcomeService;
        this.corpusRef       = corpusRef;
        this.corpusRoot      = corpusRoot;
        this.entryStore      = entryStore;
    }

    @Override
    public List<SearchResult> search(String corpusId, String query,
                                     List<String> domains, String type, String tags,
                                     Integer limit, String profile, String stack, Integer previewLength, String includeStatus) {
        List<SearchResult> results;
        if (profile != null || stack != null) {
            results = searchResource.searchAdaptive(query, null, domains, type, tags,
                                                    limit, profile, stack, includeStatus).results();
        } else {
            results = searchResource.search(query, domains, type, tags, limit, null, includeStatus);
        }
        return applyPreview(results, previewLength);
    }

    @Override
    public AdaptiveResult adaptiveSearch(String corpusId, String query,
                                         String keywords, List<String> domains, String type,
                                         String tags, Integer limit, String profile, String stack, Integer previewLength, String includeStatus) {
        String expandedKeywords = keywords != null && !keywords.isBlank()
                                  ? keywords.replace("|", " ") : null;
        AdaptiveResult result = searchResource.searchAdaptive(query, expandedKeywords,
                                                              domains, type, tags, limit, profile, stack, includeStatus);
        if (previewLength == null) {return result;}
        return result.withResults(applyPreview(result.results(), previewLength));
    }

    private static List<SearchResult> applyPreview(List<SearchResult> results, Integer previewLength) {
        if (previewLength == null || results.isEmpty()) {return results;}
        return results.stream().map(r -> r.withPreview(previewLength)).toList();
    }

    @Override
    public SearchResult entry(String corpusId, String entryId) {
        if (corpusRoot == null) return null;
        return indexService.entry(corpusRoot, entryId);
    }

    @Override
    public ChangeResult changes(String corpusId, java.time.Instant since) {
        var records = entryStore.recentChanges(corpusRef, since);
        return new ChangeResult(records);
    }

    @Override
    public List<VersionInfo> versions(String corpusId, String entryId) {
        return entryStore.versions(corpusRef, entryId);
    }

    @Override
    public CorpusEntry version(String corpusId, String entryId, int version) {
        return entryStore.getVersion(corpusRef, entryId, version).orElse(null);
    }


    @Override
    public CorpusStatus status(String corpusId) {
        return indexService.status(corpusRef, corpusRoot != null ? corpusRoot.toString() : "");
    }

    @Override
    public UnretrievedReport unretrieved(String corpusId, Integer minDays, Integer staleDays) {
        return indexService.unretrieved(corpusRef,
                minDays != null ? minDays : 30,
                staleDays != null ? staleDays : 90);
    }

    @Override
    public GardenReindexService.ReindexResult reindex(String corpusId) {
        return reindexService.reindex();
    }

    @Override
    public String reindexEntry(String corpusId, String entryId) {
        if (corpusRoot == null) return "No corpus root configured — cannot reindex.";
        return indexService.reindexEntry(corpusRef, entryId, corpusRoot);
    }

    @Override
    public String deleteEntry(String corpusId, String entryId) {
        return indexService.deleteEntry(corpusRef, entryId);
    }

    @Override
    public String profile(String name) {
        return profileStore.getRawStack(name);
    }

    @Override
    public List<String> profiles() {
        return profileStore.list();
    }

    @Override
    public void saveProfile(String name, String stack) {
        profileStore.put(name, stack);
    }

    @Override
    public boolean deleteProfile(String name) {
        return profileStore.delete(name);
    }

    @Override
    public List<ProvenanceRecord> forwardLineage(String issueRepo, Integer issueNumber) {
        return provenanceStore.forwardLineage(issueRepo, issueNumber != null ? issueNumber : 0);
    }

    @Override
    public List<ProvenanceRecord> reverseLineage(String entryId) {
        return provenanceStore.reverseLineage(entryId);
    }

    @Override
    public ProvenanceStats provenanceStats() {
        return provenanceStore.stats();
    }

    @Override
    public int recordProvenance(String issueRepo, Integer issueNumber,
            String specName, String entryIds, String recordedBy) {
        if (entryIds == null || entryIds.isBlank()) return 0;
        List<String> ids = Arrays.stream(entryIds.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return provenanceStore.record(issueRepo, issueNumber != null ? issueNumber : 0,
                specName != null ? specName : "", ids, recordedBy);
    }

    @Override
    public List<OutcomeEntry> outcomeEntries() {
        return outcomeService.outcomeEntries();
    }

    @Override
    public String recordOutcome(String entryId, String issueRepo, Integer issueNumber,
            String workContext, Double successRate, String detail) {
        return outcomeService.recordOutcome(entryId, issueRepo,
                issueNumber != null ? issueNumber : 0,
                workContext, successRate != null ? successRate : 0.0, detail);
    }

    @Override
    public String recordFeedback(String entryIds, String outcome,
            String stack, String issueRepo, Integer issueNumber) {
        if (entryIds == null || entryIds.isBlank()) return "Error: no entry IDs provided.";
        List<String> ids = Arrays.stream(entryIds.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        return indexService.recordFeedback(corpusRef, ids, outcome, stack, issueRepo, issueNumber);
    }

    @Override
    public String recordFinding(String corpusId, String topic, String insight,
                                String sourceIssue, String tags, String domain, String type, String status) {
        if (topic == null || topic.isBlank()) {return "Error: topic is required.";}
        if (insight == null || insight.isBlank()) {return "Error: insight is required.";}

        List<String> parsedTags = tags != null && !tags.isBlank()
                                  ? Arrays.stream(tags.split("[|,]")).map(String::trim).filter(s -> !s.isEmpty()).toList()
                                  : List.of();

        String resolvedStatus = (status != null && !status.isBlank()) ? status.toLowerCase() : "published";
        return storeFinding(topic, insight, sourceIssue, parsedTags, domain, type, resolvedStatus);
    }

    @Override
    public BatchFindingResult recordFindings(String corpusId, List<FindingInput> findings) {
        if (findings == null || findings.isEmpty()) {
            return new BatchFindingResult(List.of(), 0, 0, List.of("Error: findings list is empty."));
        }

        var entryIds = new java.util.ArrayList<String>();
        var errors   = new java.util.ArrayList<String>();

        for (int i = 0; i < findings.size(); i++) {
            FindingInput f = findings.get(i);
            if (f.topic() == null || f.topic().isBlank()) {
                errors.add("Finding " + (i + 1) + ": topic is required.");
                continue;
            }
            if (f.insight() == null || f.insight().isBlank()) {
                errors.add("Finding " + (i + 1) + ": insight is required.");
                continue;
            }
            try {
                List<String> tags           = f.tags() != null ? f.tags() : List.of();
                String       resolvedStatus = (f.status() != null && !f.status().isBlank()) ? f.status().toLowerCase() : "published";
                String       id             = storeFinding(f.topic(), f.insight(), f.sourceIssue(), tags, f.domain(), f.type(), resolvedStatus);
                entryIds.add(id);
            } catch (Exception e) {
                errors.add("Finding " + (i + 1) + " (" + f.topic() + "): " + e.getMessage());
            }
        }

        return new BatchFindingResult(List.copyOf(entryIds), findings.size(), entryIds.size(), List.copyOf(errors));
    }

    @Override
    public String publishEntry(String corpusId, String entryId) {
        return transitionStatus(entryId, "published");
    }

    @Override
    public String archiveEntry(String corpusId, String entryId) {
        return transitionStatus(entryId, "archived");
    }

    private String transitionStatus(String entryId, String newStatus) {
        if (entryId == null || entryId.isBlank()) {return "Error: entryId is required.";}
        var existing = entryStore.get(corpusRef, entryId);
        if (existing.isEmpty()) {return "Error: entry not found: " + entryId;}

        var    metadata  = new java.util.LinkedHashMap<>(existing.get().metadata());
        String oldStatus = (String) metadata.getOrDefault("status", "published");
        metadata.put("status", newStatus);

        var updated = new CorpusEntry(entryId, java.util.Map.copyOf(metadata),
                                      existing.get().body(), existing.get().artifactRef());
        entryStore.update(corpusRef, entryId, updated);
        return oldStatus + " → " + newStatus;
    }


    private String storeFinding(String topic, String insight, String sourceIssue,
                                List<String> tags, String domain, String type, String status) {
        String entryId = generateEntryId();

        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("title", topic);
        if (domain != null && !domain.isBlank()) {metadata.put("domain", domain);}
        if (type != null && !type.isBlank()) {metadata.put("type", type);}
        if (tags != null && !tags.isEmpty()) {metadata.put("tags", tags);}
        metadata.put("status", status != null ? status : "published");

        var entry = new CorpusEntry(entryId, java.util.Map.copyOf(metadata), insight, null);
        entryStore.add(corpusRef, entry);

        if (sourceIssue != null && !sourceIssue.isBlank()) {
            var parts = parseIssueRef(sourceIssue);
            if (parts != null) {
                provenanceStore.record(parts[0], Integer.parseInt(parts[1]),
                                       "", List.of(entryId), "recordFinding");
            }
        }

        return entryId;
    }

    private static String generateEntryId() {
        String date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        byte[] bytes = new byte[3];
        java.util.concurrent.ThreadLocalRandom.current().nextBytes(bytes);
        String hex = String.format("%02x%02x%02x", bytes[0] & 0xFF, bytes[1] & 0xFF, bytes[2] & 0xFF);
        return "GE-" + date + "-" + hex;
    }

    private static String[] parseIssueRef(String issueRef) {
        int hashIndex = issueRef.lastIndexOf('#');
        if (hashIndex <= 0) {return null;}
        String repo = issueRef.substring(0, hashIndex);
        String number = issueRef.substring(hashIndex + 1);
        try {
            Integer.parseInt(number);
            return new String[]{repo, number};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
