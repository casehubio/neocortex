package io.casehub.neocortex.corpus.management.impl;

import java.util.List;

@FunctionalInterface
public interface RemoteCorpusClient {

    List<SearchResult> search(String query, List<String> domains, String type, String tags,
                              int limit, String visited);
}
