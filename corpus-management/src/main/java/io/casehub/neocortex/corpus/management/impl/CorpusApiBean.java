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
            Integer limit, String profile, String stack, Integer previewLength) {
        List<SearchResult> results;
        if (profile != null || stack != null) {
            results = searchResource.searchAdaptive(query, null, domains, type, tags,
                    limit, profile, stack).results();
        } else {
            results = searchResource.search(query, domains, type, tags, limit, null);
        }
        return applyPreview(results, previewLength);
    }

    @Override
    public AdaptiveResult adaptiveSearch(String corpusId, String query,
            String keywords, List<String> domains, String type,
            String tags, Integer limit, String profile, String stack, Integer previewLength) {
        String expandedKeywords = keywords != null && !keywords.isBlank()
                ? keywords.replace("|", " ") : null;
        AdaptiveResult result = searchResource.searchAdaptive(query, expandedKeywords,
                domains, type, tags, limit, profile, stack);
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
}
