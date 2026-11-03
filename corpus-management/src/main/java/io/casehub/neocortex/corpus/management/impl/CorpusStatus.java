package io.casehub.neocortex.corpus.management.impl;

public record CorpusStatus(String corpusId, String corpusPath, int entryCount, boolean collectionReady) {}
