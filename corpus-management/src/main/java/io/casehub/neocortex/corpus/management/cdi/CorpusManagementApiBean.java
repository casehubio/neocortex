package io.casehub.neocortex.corpus.management.cdi;

import io.casehub.neocortex.corpus.management.CorpusCatalogue;
import io.casehub.neocortex.corpus.management.CorpusDescriptor;
import io.casehub.neocortex.corpus.management.CorpusEntry;
import io.casehub.neocortex.corpus.management.CorpusEntryStore;
import io.casehub.neocortex.corpus.management.CorpusLifecycle;
import io.casehub.neocortex.corpus.management.CorpusQuery;
import io.casehub.neocortex.corpus.management.CorpusSearch;
import io.casehub.neocortex.corpus.management.CorpusSearchResult;
import io.casehub.neocortex.corpus.management.CorpusType;
import io.casehub.neocortex.corpus.management.FederatedCorpusSearch;
import io.casehub.neocortex.corpus.management.FederatedSearchResult;
import io.casehub.neocortex.corpus.management.HealthReport;
import io.casehub.neocortex.rag.CorpusRef;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class CorpusManagementApiBean implements CorpusManagementApi {

    @Inject CorpusCatalogue catalogue;
    @Inject CorpusEntryStore entryStore;
    @Inject CorpusSearch corpusSearch;
    @Inject FederatedCorpusSearch federatedSearch;

    @Override
    public List<CorpusDescriptor> listCorpora(String tenancyId, String lifecycle) {
        CorpusLifecycle status = lifecycle != null
                ? CorpusLifecycle.valueOf(lifecycle.toUpperCase())
                : CorpusLifecycle.ACTIVE;
        return catalogue.list(tenancyId, status);
    }

    @Override
    public CorpusDescriptor getCorpus(String tenancyId, String corpusName) {
        return catalogue.get(new CorpusRef(tenancyId, corpusName)).orElse(null);
    }

    @Override
    public List<CorpusDescriptor> listByType(String typeName, String tenancyId) {
        return catalogue.forType(new CorpusType(typeName, Map.of(), Map.of(), null), tenancyId);
    }

    @Override
    public List<CorpusDescriptor> searchScope(String tenancyId, String corpusName) {
        return catalogue.searchScope(new CorpusRef(tenancyId, corpusName));
    }

    @Override
    public HealthReport reconcile(String tenancyId) {
        return catalogue.reconcile(tenancyId);
    }

    @Override
    public String addEntry(String tenancyId, String corpusName, String entryId, String body,
                           String metadata, String artifactRef) {
        var entry = new CorpusEntry(entryId, parseMetadata(metadata), body, artifactRef);
        return entryStore.add(new CorpusRef(tenancyId, corpusName), entry);
    }

    @Override
    public CorpusEntry getEntry(String tenancyId, String corpusName, String entryId) {
        return entryStore.get(new CorpusRef(tenancyId, corpusName), entryId).orElse(null);
    }

    @Override
    public String updateEntry(String tenancyId, String corpusName, String entryId, String body,
                              String metadata, String artifactRef) {
        var entry = new CorpusEntry(entryId, parseMetadata(metadata), body, artifactRef);
        entryStore.update(new CorpusRef(tenancyId, corpusName), entryId, entry);
        return entryId;
    }

    @Override
    public String removeEntry(String tenancyId, String corpusName, String entryId) {
        entryStore.remove(new CorpusRef(tenancyId, corpusName), entryId);
        return entryId;
    }

    @Override
    public List<CorpusSearchResult> searchCorpus(String tenancyId, String corpusName,
                                                  String query, Integer maxResults) {
        int limit = maxResults != null ? maxResults : 10;
        return corpusSearch.search(
                new CorpusRef(tenancyId, corpusName), CorpusQuery.of(query, limit));
    }

    @Override
    public FederatedSearchResult federatedSearch(String tenancyId, String query,
                                                  Integer maxResults, Integer federationDepth) {
        int limit = maxResults != null ? maxResults : 10;
        int depth = federationDepth != null ? federationDepth : -1;
        var corpusQuery = CorpusQuery.of(query, limit).withFederationDepth(depth);
        return federatedSearch.search(corpusQuery, tenancyId);
    }

    static Map<String, Object> parseMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return Map.of();
        var result = new LinkedHashMap<String, Object>();
        for (String pair : metadata.split("\\|")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return result;
    }
}
