package io.casehub.neocortex.corpus.management.impl;

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
    private final Path gardenRoot;

    public CorpusApiBean(SearchResource searchResource,
                          CorpusIndexService indexService,
                          GardenReindexService reindexService,
                          SearchProfileStore profileStore,
                          ProvenanceStore provenanceStore,
                          CorpusOutcomeService outcomeService,
                          CorpusRef corpusRef,
                          Path gardenRoot) {
        this.searchResource = searchResource;
        this.indexService = indexService;
        this.reindexService = reindexService;
        this.profileStore = profileStore;
        this.provenanceStore = provenanceStore;
        this.outcomeService = outcomeService;
        this.corpusRef = corpusRef;
        this.gardenRoot = gardenRoot;
    }

    @Override
    public List<SearchResult> search(String corpusId, String query,
            List<String> domains, String type, String tags,
            Integer limit, String profile, String stack) {
        if (profile != null || stack != null) {
            return searchResource.searchAdaptive(query, null, domains, type, tags,
                    limit, profile, stack).results();
        }
        return searchResource.search(query, domains, type, tags, limit, null);
    }

    @Override
    public AdaptiveResult adaptiveSearch(String corpusId, String query,
            String keywords, List<String> domains, String type,
            String tags, Integer limit, String profile, String stack) {
        String expandedKeywords = keywords != null && !keywords.isBlank()
                ? keywords.replace("|", " ") : null;
        return searchResource.searchAdaptive(query, expandedKeywords,
                domains, type, tags, limit, profile, stack);
    }

    @Override
    public EntryDetail entry(String corpusId, String entryId) {
        if (gardenRoot == null) return null;
        return indexService.entry(gardenRoot, entryId);
    }

    @Override
    public CorpusStatus status(String corpusId) {
        return indexService.status(corpusRef, gardenRoot != null ? gardenRoot.toString() : "");
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
        if (gardenRoot == null) return "No garden root configured — cannot reindex.";
        return indexService.reindexEntry(corpusRef, entryId, gardenRoot);
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
    public String outcomeReport() {
        return outcomeService.outcomeReport();
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
