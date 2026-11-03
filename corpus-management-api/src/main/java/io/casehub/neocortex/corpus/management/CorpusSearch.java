package io.casehub.neocortex.corpus.management;

import io.casehub.neocortex.rag.CorpusRef;

import java.util.List;

public interface CorpusSearch {

    List<CorpusSearchResult> search(CorpusRef corpus, CorpusQuery query);
}
