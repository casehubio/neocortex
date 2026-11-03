package io.casehub.neocortex.corpus.management;

public interface FederatedCorpusSearch {

    FederatedSearchResult search(CorpusQuery query, String tenantId);
}
